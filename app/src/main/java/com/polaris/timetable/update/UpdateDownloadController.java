package com.polaris.timetable.update;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用进程内、用户主动触发的 HTTPS 下载（协议第 7.1 节 UpdateDownloadController）。
 * 不引入系统 DownloadManager 或后台调度依赖：
 * - 单线程 ExecutorService；同一时刻只允许一个下载任务。
 * - 下载到 updates/<fileName>.part，成功校验后原子重命名为 .apk。
 * - 下载前确认可用空间至少 apk.size + 50 MiB；单 APK 上限 200 MiB。
 * - 每 250 ms 或每 256 KiB 汇报一次进度；支持原子取消；.part 失败即删，不做断点续传。
 * 连接工厂、归档校验与主线程派发可注入，供本地单元测试以假流覆盖全部下载分支。
 */
public final class UpdateDownloadController {

    public static final long MIN_FREE_SPACE_BYTES = 50L * 1024L * 1024L;
    public static final long PROGRESS_NOTIFY_INTERVAL_MS = 250L;
    public static final long PROGRESS_NOTIFY_BYTES = 256L * 1024L;
    public static final int MAX_REDIRECTS = 5;

    public interface ConnectionFactory {
        HttpURLConnection open(URL url) throws IOException;
    }

    /** 归档校验委托：生产实现为 {@link ApkVerifier#verify}，测试可注入替身。 */
    public interface ArchiveVerifier {
        ApkVerifier.Result verify(Context context, File apk, UpdateInfo info,
                                  int installedVersionCode, int deviceSdk);
    }

    /** 主线程派发抽象：生产为 Handler(mainLooper)，测试可同步执行。 */
    public interface MainThread {
        void post(Runnable action);
    }

    public interface Callbacks {
        void onStateChanged(UpdateDownloadState state, long downloaded, long total);

        void onReady(File apkFile, int versionCode);

        void onFailed(UpdateError error);

        void onCancelled();
    }

    private final File partDirectory;
    private final File verifiedDirectory;
    private final Context appContext;
    private final Integer fixedInstalledVersionCode;
    private final Callbacks callbacks;
    private final ConnectionFactory connectionFactory;
    private final MainThread mainThread;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private ArchiveVerifier archiveVerifier;

    public UpdateDownloadController(Context context, Callbacks callbacks) {
        this(context, callbacks, null);
    }

    public UpdateDownloadController(Context context, Callbacks callbacks,
                                    ConnectionFactory connectionFactory) {
        // .part 放缓存目录（允许被系统清理，U-P1-01）；校验通过后的 APK 移入 filesDir/updates。
        this(new File(context.getApplicationContext().getCacheDir(), "updates"),
                new File(context.getApplicationContext().getFilesDir(), "updates"),
                context.getApplicationContext(), null, callbacks,
                connectionFactory, null, null);
    }

    /** 测试构造：显式给定目录、固定已安装版本码与主线程派发策略。 */
    UpdateDownloadController(File partDirectory, File verifiedDirectory, Callbacks callbacks,
                             ConnectionFactory connectionFactory, MainThread mainThread,
                             ArchiveVerifier archiveVerifier, int installedVersionCode) {
        this(partDirectory, verifiedDirectory, null, installedVersionCode, callbacks,
                connectionFactory, mainThread, archiveVerifier);
    }

    private UpdateDownloadController(File partDirectory, File verifiedDirectory,
                                     Context appContext, Integer fixedInstalledVersionCode,
                                     Callbacks callbacks, ConnectionFactory connectionFactory,
                                     MainThread mainThread, ArchiveVerifier archiveVerifier) {
        this.partDirectory = partDirectory;
        this.verifiedDirectory = verifiedDirectory;
        this.appContext = appContext;
        this.fixedInstalledVersionCode = fixedInstalledVersionCode;
        this.callbacks = callbacks;
        this.connectionFactory = connectionFactory != null
                ? connectionFactory
                : new ConnectionFactory() {
                    @Override
                    public HttpURLConnection open(URL url) throws IOException {
                        return (javax.net.ssl.HttpsURLConnection) url.openConnection();
                    }
                };
        this.mainThread = mainThread != null
                ? mainThread
                : new MainThread() {
                    @Override
                    public void post(Runnable action) {
                        new Handler(Looper.getMainLooper()).post(action);
                    }
                };
        this.archiveVerifier = archiveVerifier != null
                ? archiveVerifier
                : new ArchiveVerifier() {
                    @Override
                    public ApkVerifier.Result verify(Context context, File apk, UpdateInfo info,
                                                     int installedVersionCode, int deviceSdk) {
                        return ApkVerifier.verify(context, apk, info, installedVersionCode, deviceSdk);
                    }
                };
    }

    void setArchiveVerifier(ArchiveVerifier verifier) {
        this.archiveVerifier = verifier;
    }

    public static File updatesDirectory(Context context) {
        return new File(context.getApplicationContext().getCacheDir(), "updates");
    }

    /** 启动清理：进程被系统终止后遗留的 .part 视为失败，直接删除。 */
    public static void cleanTemporaryFiles(Context context) {
        cleanTemporaryFilesIn(updatesDirectory(context));
    }

    public static void cleanTemporaryFilesIn(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().endsWith(".part") && !file.delete()) {
                file.deleteOnExit();
            }
        }
    }

    public boolean isBusy() {
        return busy.get();
    }

    public void cancel() {
        cancelled.set(true);
    }

    /** 开始新下载前清理旧版本文件：最多保留一个已验证 APK。 */
    public void start(UpdateInfo info) {
        if (info == null || busy.get()) {
            return;
        }
        busy.set(true);
        cancelled.set(false);
        executor.execute(() -> runDownload(info));
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void runDownload(UpdateInfo info) {
        // 任务 ID 隔离：.part 文件带任务后缀，清理逻辑只删除非活动任务的临时文件（U-P1-05）。
        long taskId = System.nanoTime();
        File part = new File(partDirectory, info.apkFileName() + "." + taskId + ".part");
        File target = new File(verifiedDirectory, info.apkFileName());
        try {
            notifyState(UpdateDownloadState.PREPARING, 0L, info.apkSize());
            ensureWritableDirectory(partDirectory);
            ensureWritableDirectory(verifiedDirectory);
            // 同名旧 target 必须在下载开始前删除：上次在重命名前后崩溃时，
            // 保留旧文件会让本次 renameTo 失败（U-P1-03）。
            deleteQuietly(target);
            cleanupStaleParts(part);
            if (!ensureFreeSpace(info.apkSize())) {
                throw failure(UpdateError.INSUFFICIENT_STORAGE);
            }
            download(info, part);
            if (cancelled.get()) {
                throw cancelledFailure();
            }
            notifyState(UpdateDownloadState.VERIFYING_HASH, info.apkSize(), info.apkSize());
            String digest = ApkVerifier.sha256Hex(part);
            if (digest == null || !digest.equals(info.apkSha256())) {
                throw failure(UpdateError.HASH_MISMATCH);
            }
            notifyState(UpdateDownloadState.VERIFYING_APK, info.apkSize(), info.apkSize());
            ApkVerifier.Result result = archiveVerifier.verify(appContext, part, info,
                    installedVersionCode(), android.os.Build.VERSION.SDK_INT);
            if (result == null || !result.ok) {
                throw failure(result == null ? UpdateError.APK_UNREADABLE : result.error);
            }
            if (!part.renameTo(target)) {
                throw failure(UpdateError.STORAGE_WRITE_FAILED);
            }
            notifyState(UpdateDownloadState.READY_TO_INSTALL, info.apkSize(), info.apkSize());
            postToMain(() -> callbacks.onReady(target, info.versionCode()));
        } catch (DownloadAbort abort) {
            UpdateError error = abort.error;
            deleteQuietly(part);
            if (error == UpdateError.CANCELLED) {
                notifyState(UpdateDownloadState.CANCELLED, 0L, 0L);
                postToMain(callbacks::onCancelled);
            } else {
                notifyState(UpdateDownloadState.FAILED, 0L, 0L);
                final UpdateError reported = error;
                postToMain(() -> callbacks.onFailed(reported));
            }
        } finally {
            busy.set(false);
        }
    }

    private void download(UpdateInfo info, File part) throws DownloadAbort {
        URL url = parseApkUrl(info);
        long expected = info.apkSize();
        long written = 0L;
        long lastNotifyAt = 0L;
        long lastNotifyBytes = 0L;
        HttpURLConnection connection = null;
        FileOutputStream output = null;
        InputStream input = null;
        try {
            for (int redirects = 0; ; ) {
                connection = connectionFactory.open(url);
                connection.setConnectTimeout(UpdateRepository.CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(UpdateRepository.READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", UpdateRepository.USER_AGENT_PREFIX
                        + "Android/" + android.os.Build.VERSION.SDK_INT);
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    // 收到重定向响应时即计数：恰好 5 次允许，第 6 次拒绝（U-P1-10）。
                    if (redirects >= MAX_REDIRECTS) {
                        throw failure(UpdateError.NETWORK);
                    }
                    redirects++;
                    String location = connection.getHeaderField("Location");
                    url = UpdateJsonParser.toAllowedHttpsUrl(location);
                    connection.disconnect();
                    connection = null;
                    continue;
                }
                if (code != 200) {
                    throw failure(UpdateError.HTTP);
                }
                break;
            }
            // getContentLengthLong 需 API 24；API 23 退回 int 版本（APK ≤ 2 GiB 不会溢出）。
            long declaredLength = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? connection.getContentLengthLong()
                    : connection.getContentLength();
            if (declaredLength > 0 && declaredLength != expected) {
                throw failure(UpdateError.FILE_SIZE_MISMATCH);
            }
            input = connection.getInputStream();
            output = new FileOutputStream(part);
            byte[] chunk = new byte[65536];
            int read;
            while ((read = input.read(chunk)) != -1) {
                if (cancelled.get()) {
                    throw cancelledFailure();
                }
                written += read;
                // 超过清单声明大小时立即终止：恶意/错误响应不得继续消耗流量与存储（U-P1-04）。
                if (written > expected || written > UpdateJsonParser.MAX_APK_BYTES) {
                    throw failure(UpdateError.FILE_SIZE_MISMATCH);
                }
                output.write(chunk, 0, read);
                long now = System.currentTimeMillis();
                if (written == expected
                        || now - lastNotifyAt >= PROGRESS_NOTIFY_INTERVAL_MS
                        || written - lastNotifyBytes >= PROGRESS_NOTIFY_BYTES) {
                    lastNotifyAt = now;
                    lastNotifyBytes = written;
                    notifyState(UpdateDownloadState.DOWNLOADING, written, expected);
                }
            }
            if (written != expected) {
                throw failure(UpdateError.FILE_SIZE_MISMATCH);
            }
            output.flush();
            output.getFD().sync();
        } catch (SocketTimeoutException exception) {
            throw failure(UpdateError.TIMEOUT);
        } catch (MalformedURLException exception) {
            throw failure(UpdateError.NETWORK);
        } catch (IOException exception) {
            throw failure(UpdateError.NETWORK);
        } finally {
            closeQuietly(input);
            closeQuietly(output);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private URL parseApkUrl(UpdateInfo info) throws DownloadAbort {
        // APK URL 已在清单解析时校验 HTTPS 与白名单；重定向阶段再做同规则校验。
        try {
            return new URL(info.apkUrl());
        } catch (MalformedURLException exception) {
            throw failure(UpdateError.NETWORK);
        }
    }

    private int installedVersionCode() {
        if (fixedInstalledVersionCode != null) {
            return fixedInstalledVersionCode;
        }
        try {
            return appContext.getPackageManager()
                    .getPackageInfo(appContext.getPackageName(), 0).versionCode;
        } catch (Exception exception) {
            return 0;
        }
    }

    private boolean ensureFreeSpace(long apkSize) {
        try {
            File anchor = partDirectory.getParentFile();
            if (anchor == null) {
                anchor = partDirectory;
            }
            StatFs stat = new StatFs(anchor.getAbsolutePath());
            long available = stat.getAvailableBytes();
            if (available <= 0) {
                // 无法评估空间（含测试桩）时不拦截，交由写入失败路径处理。
                return true;
            }
            return available >= apkSize + MIN_FREE_SPACE_BYTES;
        } catch (Exception exception) {
            return true;
        }
    }

    /** PREPARING 阶段显式创建目录；失败归为写入错误而非网络错误（U-P0-01）。 */
    private void ensureWritableDirectory(File directory) throws DownloadAbort {
        if (directory.isDirectory()) {
            if (!directory.canWrite()) {
                throw failure(UpdateError.STORAGE_WRITE_FAILED);
            }
            return;
        }
        if (directory.exists()) {
            // 路径被同名普通文件占用。
            throw failure(UpdateError.STORAGE_WRITE_FAILED);
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw failure(UpdateError.STORAGE_WRITE_FAILED);
        }
        if (!directory.canWrite()) {
            throw failure(UpdateError.STORAGE_WRITE_FAILED);
        }
    }

    /** 只清理非活动任务的 .part；活动任务的临时文件不受影响（U-P1-05）。 */
    private void cleanupStaleParts(File activePart) {
        File[] files = partDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.equals(activePart)) {
                continue;
            }
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".part")) {
                deleteQuietly(file);
            }
        }
    }

    private static final class DownloadAbort extends RuntimeException {
        final UpdateError error;

        DownloadAbort(UpdateError error) {
            super(error.name());
            this.error = error;
        }
    }

    private static DownloadAbort failure(UpdateError error) {
        return new DownloadAbort(error);
    }

    private static DownloadAbort cancelledFailure() {
        return new DownloadAbort(UpdateError.CANCELLED);
    }

    private void notifyState(UpdateDownloadState state, long downloaded, long total) {
        postToMain(() -> callbacks.onStateChanged(state, downloaded, total));
    }

    private void postToMain(Runnable action) {
        mainThread.post(action);
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // 关闭失败不影响结果。
        }
    }
}