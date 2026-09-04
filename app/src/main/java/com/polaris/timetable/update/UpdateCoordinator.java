package com.polaris.timetable.update;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.polaris.timetable.R;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 更新系统单一业务入口（协议 7.1 + 改进计划阶段 B/D）：
 * 防重复检查、区分手动/自动检查、串联 Repository→Parser→Policy、
 * 启动/取消下载、恢复待安装状态、把结果切回主线程通知 UI。
 * 安装链迁移为 PackageInstaller Session（U-P0-02）：未知来源授权页与 FileProvider 链已移除，
 * 会话状态经显式广播接收器回传；后台收到待确认 Intent 时暂存 URI，返回前台后启动。
 * 进程级单例，Activity 重建后下载与会话状态均不丢失。
 */
public final class UpdateCoordinator implements UpdateDownloadController.Callbacks {

    /** 客户端内置唯一稳定清单入口；预发布必须标记 prerelease，不得替换稳定入口。 */
    public static final String STABLE_MANIFEST_URL =
            "https://github.com/Jockjrop/PolarisTimetable/releases/latest/download/latest.json";

    public enum InstallAction {LAUNCHED, NO_INSTALLER, NOT_READY}

    public interface Host {
        /** “检查更新”行副标题变化（已在主线程）。 */
        void onUpdateCheckRowTextChanged(String text);

        /** 展示“发现新版本/重要安全更新”弹窗。 */
        void onShowUpdateAvailableDialog(UpdateInfo info);

        /** 打开（或刷新）下载弹窗。 */
        void onShowDownloadDialog();

        /** 下载进度/阶段变化。 */
        void onDownloadProgress(UpdateDownloadState state, long downloaded, long total);

        /** 下载与四层校验全部通过，可安装。 */
        void onDownloadReady();

        void onDownloadCancelled();

        /** 下载/校验失败，message 为用户可读文案。 */
        void onDownloadFailed(String message);

        /** 安装会话状态回执（成功/取消/被阻止等），message 为用户可读文案。 */
        void onInstallStatusMessage(String message);

        /** 自动弹窗延后判断：设置页、导入编辑流程、系统对话框或无窗口焦点时返回 true。 */
        boolean isAutoDialogBlocked();
    }

    /** 安装网关可注入：生产委托 UpdateInstaller，测试用假实现。 */
    interface InstallGateway {
        int commitSession(Context context, File apk, String packageName)
                throws UpdateInstaller.InstallException;

        void abandonSession(Context context, int sessionId);

        PackageInstaller.SessionInfo sessionInfo(Context context, int sessionId);
    }

    /** 主线程派发抽象：生产为 Handler(mainLooper)，测试可同步执行。 */
    interface MainThread {
        void post(Runnable action);
    }

    private static volatile UpdateCoordinator instance;

    /** 进程级单例：同一时刻只允许一个检查/下载/安装任务在应用内运行。 */
    public static UpdateCoordinator acquire(Context context, Host host) {
        if (instance == null) {
            synchronized (UpdateCoordinator.class) {
                if (instance == null) {
                    instance = new UpdateCoordinator(context.getApplicationContext());
                }
            }
        }
        synchronized (instance.hostLock) {
            instance.host = host;
        }
        return instance;
    }

    /** Activity 销毁时解绑 UI；下载与后续自动检查继续（结果不再通知旧 UI）。 */
    public static void releaseHost(Host host) {
        if (instance == null) {
            return;
        }
        synchronized (instance.hostLock) {
            if (instance.host == host) {
                instance.host = null;
            }
        }
    }

    /** 测试专用：丢弃当前单例（连同其执行器），下次 acquire 重建。 */
    static synchronized void resetForTest() {
        if (instance != null) {
            instance.checkExecutor.shutdownNow();
            if (instance.controller != null) {
                instance.controller.shutdown();
            }
            instance = null;
        }
    }

    private final Context appContext;
    private final UpdatePreferences prefs;
    private final UpdateJsonParser parser;
    private final ExecutorService checkExecutor = Executors.newSingleThreadExecutor();
    private final Object hostLock = new Object();
    private final Set<Integer> autoOfferedVersionCodes = new HashSet<>();
    private final Integer fixedLocalVersionCode;
    private final String fixedLocalVersionName;

    private volatile Host host;
    private UpdateRepository repository;
    private InstallGateway installGateway;
    private MainThread mainThread;
    private UpdateDownloadController controller;
    private volatile boolean checkInFlight;
    private volatile boolean activityForeground;
    private UpdateInfo lastAvailable;
    private UpdateInfo pendingAutoOffer;
    private boolean lastCheckFailed;
    private UpdateDownloadState lastState = UpdateDownloadState.IDLE;
    private long lastDownloaded;
    private long lastTotal;
    private File readyApkFile;
    private int readyVersionCode;

    private UpdateCoordinator(Context appContext) {
        this.appContext = appContext;
        this.prefs = new UpdatePreferences(UpdatePreferences.sharedPreferenceStore(appContext));
        this.parser = new UpdateJsonParser(appContext.getPackageName());
        this.repository = new UpdateRepository();
        this.installGateway = new InstallGateway() {
            @Override
            public int commitSession(Context context, File apk, String packageName)
                    throws UpdateInstaller.InstallException {
                return UpdateInstaller.createAndCommitSession(context, apk, packageName);
            }

            @Override
            public void abandonSession(Context context, int sessionId) {
                UpdateInstaller.abandonSession(context, sessionId);
            }

            @Override
            public PackageInstaller.SessionInfo sessionInfo(Context context, int sessionId) {
                return UpdateInstaller.sessionInfo(context, sessionId);
            }
        };
        this.mainThread = new MainThread() {
            @Override
            public void post(Runnable action) {
                new Handler(Looper.getMainLooper()).post(action);
            }
        };
        this.fixedLocalVersionCode = null;
        this.fixedLocalVersionName = null;
    }

    /** 测试构造：内存偏好 + 假仓库 + 同步主线程 + 假安装网关 + 固定本地版本。 */
    UpdateCoordinator(UpdatePreferences prefs, UpdateJsonParser parser,
                      UpdateRepository repository, MainThread mainThread,
                      InstallGateway installGateway, int localVersionCode,
                      String localVersionName) {
        this.appContext = null;
        this.prefs = prefs;
        this.parser = parser;
        this.repository = repository;
        this.mainThread = mainThread;
        this.installGateway = installGateway;
        this.fixedLocalVersionCode = localVersionCode;
        this.fixedLocalVersionName = localVersionName;
    }

    /** 测试注入：替换仓库/网关/主线程派发（必须在首次使用前调用）。 */
    void injectForTest(UpdateRepository repository, InstallGateway gateway,
                       MainThread mainThread) {
        if (repository != null) {
            this.repository = repository;
        }
        if (gateway != null) {
            this.installGateway = gateway;
        }
        if (mainThread != null) {
            this.mainThread = mainThread;
        }
    }

    void setControllerForTest(UpdateDownloadController controller) {
        this.controller = controller;
    }

    /** 测试专用：直接绑定宿主（绕过 acquire 单例）。 */
    void attachHostForTest(Host host) {
        synchronized (hostLock) {
            this.host = host;
        }
    }

    // ===== 生命周期 =====

    /** 冷启动恢复：仅控制器空闲时清理 .part 遗留；完整复验待安装 APK 与安装会话。 */
    public void onHostCreated() {
        if (controller == null || !controller.isBusy()) {
            // 同进程 Activity 重建时下载可能仍在进行，不得误删活动 .part（U-P1-05）。
            UpdateDownloadController.cleanTemporaryFiles(appContext);
        }
        restorePendingApk();
        restorePendingSession();
    }

    private void restorePendingApk() {
        String pendingPath = prefs.pendingApkPath();
        int pendingCode = prefs.pendingVersionCode();
        if (TextUtils.isEmpty(pendingPath)) {
            return;
        }
        File pending = new File(pendingPath);
        String sha256 = prefs.pendingApkSha256();
        long size = prefs.pendingApkSize();
        int localCode = localVersionCode();
        // U-P1-02：恢复时用持久化的大小 + SHA-256 执行完整复验（含包名/版本/签名）。
        UpdateInfo surrogate = new UpdateInfo(1, "stable", appContext.getPackageName(),
                pendingCode, "", 0, 0, "", pending.getName(), "", size,
                sha256 == null ? "" : sha256,
                java.util.Collections.singletonList(""), "", false);
        boolean restored = pendingCode > 0
                && pendingCode > localCode
                && size > 0
                && sha256 != null && !sha256.isEmpty()
                && pending.isFile();
        if (restored) {
            ApkVerifier.Result result = ApkVerifier.verify(appContext, pending, surrogate,
                    localCode, android.os.Build.VERSION.SDK_INT);
            restored = result.ok;
        }
        if (restored) {
            readyApkFile = pending;
            readyVersionCode = pendingCode;
        } else {
            if (pending.exists() && !pending.delete()) {
                pending.deleteOnExit();
            }
            prefs.clearPendingInstall();
        }
    }

    private void restorePendingSession() {
        int sessionId = prefs.pendingSessionId();
        if (sessionId < 0) {
            return;
        }
        PackageInstaller.SessionInfo info = installGateway.sessionInfo(appContext, sessionId);
        String confirmUri = prefs.pendingConfirmIntent();
        if (info != null && info.isActive()) {
            if (confirmUri == null) {
                // 进程死亡丢失确认 Intent 后无法重建确认流程，只能放弃会话并要求重新下载。
                installGateway.abandonSession(appContext, sessionId);
                prefs.clearPendingInstall();
            }
            // 有暂存确认 Intent：交由 onHostResumed 启动。
        } else {
            // 会话已结束：状态广播应已处理过；此处兜底清理。
            prefs.clearPendingInstall();
        }
    }

    public void onHostResumed() {
        activityForeground = true;
        Host h = currentHost();
        if (h == null) {
            return;
        }
        // Host 重绑后立即回放当前下载状态（U-P1-05）。
        if (lastState == UpdateDownloadState.PREPARING
                || lastState == UpdateDownloadState.DOWNLOADING
                || lastState == UpdateDownloadState.VERIFYING_HASH
                || lastState == UpdateDownloadState.VERIFYING_APK) {
            h.onDownloadProgress(lastState, lastDownloaded, lastTotal);
        }
        deliverPendingConfirmIntent(h);
        offerPendingAutoUpdate(h);
    }

    public void onHostPaused() {
        activityForeground = false;
    }

    private void deliverPendingConfirmIntent(Host h) {
        String confirmUri = prefs.pendingConfirmIntent();
        if (confirmUri == null) {
            return;
        }
        prefs.clearPendingConfirmIntent();
        try {
            Intent confirm = Intent.parseUri(confirmUri, Intent.URI_INTENT_SCHEME);
            appContext.startActivity(confirm);
        } catch (Exception exception) {
            h.onInstallStatusMessage(getString(R.string.update_install_failed));
        }
    }

    private void offerPendingAutoUpdate(Host h) {
        if (pendingAutoOffer == null) {
            return;
        }
        if (!h.isAutoDialogBlocked()) {
            UpdateInfo info = pendingAutoOffer;
            pendingAutoOffer = null;
            h.onShowUpdateAvailableDialog(info);
        }
    }

    public void onHostDestroyed() {
        releaseHost(currentHost());
    }

    // ===== 检查 =====

    /** 手动检查：不受 24 小时间隔与忽略版本限制；待安装/下载中时直接回到下载弹窗。 */
    public void onManualCheckClicked() {
        UpdateDownloadController activeController = controller();
        if (readyApkFile != null || (activeController != null && activeController.isBusy())) {
            Host h = currentHost();
            if (h != null) {
                h.onShowDownloadDialog();
            }
            return;
        }
        performCheck(true);
    }

    /** 自动检查：默认关闭；仅冷启动稳定 5 秒后由宿主触发；24 小时节流。 */
    public void maybeAutoCheck() {
        if (checkInFlight) {
            return;
        }
        if (!prefs.isAutoCheckEnabled()) {
            return;
        }
        if (!UpdatePolicy.shouldAutoCheck(prefs.lastSuccessfulCheckAt(),
                System.currentTimeMillis())) {
            return;
        }
        performCheck(false);
    }

    private void performCheck(final boolean manual) {
        if (checkInFlight) {
            return;
        }
        checkInFlight = true;
        lastCheckFailed = false;
        if (manual) {
            Host hostForChecking = currentHost();
            if (hostForChecking != null) {
                hostForChecking.onUpdateCheckRowTextChanged(
                        getString(R.string.update_check_checking));
            }
        }
        checkExecutor.execute(() -> {
            UpdateCheckResult result;
            try {
                result = doCheck(manual);
            } catch (RuntimeException exception) {
                result = UpdateCheckResult.invalidMetadata(UpdateError.INVALID_METADATA);
            }
            final UpdateCheckResult finalResult = result;
            mainThread.post(() -> deliverCheckResult(manual, finalResult));
        });
    }

    private UpdateCheckResult doCheck(boolean manual) {
        try {
            String body = repository.fetchManifest(STABLE_MANIFEST_URL, localVersionName());
            UpdateInfo info;
            try {
                info = parser.parse(body);
            } catch (UpdateJsonParser.InvalidManifestException exception) {
                return exception.reason == UpdateError.UNSUPPORTED_SCHEMA
                        ? UpdateCheckResult.unsupportedSchema()
                        : UpdateCheckResult.invalidMetadata(exception.reason);
            }
            prefs.setLastSuccessfulCheckAt(System.currentTimeMillis());
            UpdatePolicy.Decision decision = UpdatePolicy.evaluate(info, localVersionCode(),
                    android.os.Build.VERSION.SDK_INT, prefs.ignoredVersionCode(), manual);
            switch (decision) {
                case AVAILABLE:
                    lastAvailable = info;
                    return UpdateCheckResult.available(info);
                case UP_TO_DATE:
                    return UpdateCheckResult.upToDate(info);
                case DEVICE_UNSUPPORTED:
                    return UpdateCheckResult.deviceUnsupported(info);
                case IGNORED_VERSION:
                default:
                    return UpdateCheckResult.ignoredVersion(info);
            }
        } catch (UpdateRepository.FetchException exception) {
            switch (exception.error) {
                case TIMEOUT:
                    return UpdateCheckResult.networkError(UpdateError.TIMEOUT);
                case HTTP:
                    return UpdateCheckResult.httpError();
                case INVALID_METADATA:
                    return UpdateCheckResult.invalidMetadata(UpdateError.INVALID_METADATA);
                default:
                    return UpdateCheckResult.networkError(UpdateError.NETWORK);
            }
        }
    }

    private void deliverCheckResult(boolean manual, UpdateCheckResult result) {
        checkInFlight = false;
        Host h = currentHost();
        UpdateInfo info = result.update();
        switch (result.kind()) {
            case UPDATE_AVAILABLE:
                lastAvailable = info;
                if (manual) {
                    if (h != null) {
                        h.onUpdateCheckRowTextChanged(getString(
                                R.string.update_check_available_value, info.versionName()));
                        h.onShowUpdateAvailableDialog(info);
                    }
                } else if (info != null
                        && !autoOfferedVersionCodes.contains(info.versionCode())) {
                    autoOfferedVersionCodes.add(info.versionCode());
                    if (h == null || h.isAutoDialogBlocked()) {
                        pendingAutoOffer = info;
                    } else {
                        h.onShowUpdateAvailableDialog(info);
                    }
                }
                break;
            case UP_TO_DATE:
                if (manual && h != null) {
                    h.onUpdateCheckRowTextChanged(getString(
                            R.string.update_check_up_to_date_value,
                            relativeTime(prefs.lastSuccessfulCheckAt())));
                }
                break;
            case DEVICE_UNSUPPORTED:
                if (manual && h != null) {
                    h.onUpdateCheckRowTextChanged(getString(R.string.update_check_failed_value));
                    h.onDownloadFailed(getString(R.string.update_error_device_unsupported));
                }
                break;
            case IGNORED_VERSION:
                // 自动检查命中忽略版本：静默；手动检查不会走到这里。
                break;
            case CANCELLED:
                break;
            case UNSUPPORTED_SCHEMA:
            case INVALID_METADATA:
            case NETWORK_ERROR:
            case HTTP_ERROR:
            default:
                lastCheckFailed = true;
                if (manual && h != null) {
                    h.onUpdateCheckRowTextChanged(getString(R.string.update_check_failed_value));
                    h.onDownloadFailed(getString(errorTextRes(result.error())));
                }
                break;
        }
    }

    // ===== 下载与安装 =====

    public void startDownload() {
        UpdateInfo info = lastAvailable;
        if (info == null) {
            if (readyApkFile != null) {
                Host h = currentHost();
                if (h != null) {
                    h.onShowDownloadDialog();
                }
            }
            return;
        }
        readyApkFile = null;
        prefs.clearPendingInstall();
        UpdateDownloadController activeController = controller();
        if (activeController != null) {
            activeController.start(info);
        }
    }

    public void cancelDownload() {
        if (controller != null) {
            controller.cancel();
        }
    }

    /** 通过 PackageInstaller 会话提交安装；失败时已 abandon 并向宿主回报原因。 */
    public InstallAction installReadyApk() {
        final File file = readyApkFile;
        if (file == null || !file.isFile()) {
            return InstallAction.NOT_READY;
        }
        try {
            int sessionId = installGateway.commitSession(appContext, file,
                    appContext != null ? appContext.getPackageName() : null);
            prefs.setPendingSession(sessionId, readyVersionCode);
            // 会话已接管 APK 数据（commit 成功后安装器内部已删除本地文件）。
            readyApkFile = null;
            readyVersionCode = 0;
            prefs.clearPendingApkFile();
            return InstallAction.LAUNCHED;
        } catch (UpdateInstaller.InstallException exception) {
            if (exception.sessionId >= 0) {
                installGateway.abandonSession(appContext, exception.sessionId);
            }
            Host h = currentHost();
            if (h != null) {
                h.onInstallStatusMessage(getString(errorTextRes(exception.error)));
            }
            return InstallAction.NOT_READY;
        }
    }

    public void ignoreVersion(int versionCode) {
        prefs.setIgnoredVersionCode(versionCode);
    }

    public void setAutoCheckEnabled(boolean enabled) {
        prefs.setAutoCheckEnabled(enabled);
    }

    public boolean isAutoCheckEnabled() {
        return prefs.isAutoCheckEnabled();
    }

    // ===== PackageInstaller 状态回执 =====

    /** 供 UpdateInstallStatusReceiver 调用：进程存活与否都能正确收敛状态。 */
    static void handleInstallStatus(Context appContext, int sessionId, int status,
                                    Intent confirmIntent) {
        UpdateCoordinator coordinator = instance;
        if (coordinator == null) {
            // 进程被杀后广播重启进程：重建无宿主实例，仅做状态收敛与持久化。
            coordinator = acquire(appContext, null);
        }
        coordinator.deliverInstallStatus(sessionId, status, confirmIntent);
    }

    private void deliverInstallStatus(final int sessionId, final int status,
                                      final Intent confirmIntent) {
        mainThread.post(() -> handleInstallStatusOnMain(sessionId, status, confirmIntent));
    }

    /** 包内可见：UpdateCoordinatorTest 直接驱动状态收敛分支。 */
    void handleInstallStatusOnMain(int sessionId, int status, Intent confirmIntent) {
        Host h = currentHost();
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            if (confirmIntent != null) {
                if (activityForeground && h != null) {
                    try {
                        appContext.startActivity(confirmIntent);
                    } catch (RuntimeException exception) {
                        h.onInstallStatusMessage(getString(R.string.update_install_failed));
                    }
                } else if (h == null || !activityForeground) {
                    // 后台收到确认请求：暂存确认 Intent，用户返回前台后启动（U-P0-02-7）。
                    prefs.setPendingConfirmIntent(
                            confirmIntent.toUri(Intent.URI_INTENT_SCHEME));
                }
            } else {
                installGateway.abandonSession(appContext, sessionId);
                prefs.clearPendingInstall();
                if (h != null) {
                    h.onInstallStatusMessage(getString(R.string.update_install_failed));
                }
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            prefs.clearPendingInstall();
            if (h != null) {
                h.onInstallStatusMessage(getString(R.string.update_install_success));
                h.onUpdateCheckRowTextChanged(statusLineText());
            }
            return;
        }
        // 各类失败：放弃会话、清理待安装状态并给出可理解原因。
        installGateway.abandonSession(appContext, sessionId);
        prefs.clearPendingInstall();
        UpdateError error;
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                error = UpdateError.CANCELLED;
                break;
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
                error = UpdateError.INSTALL_BLOCKED;
                break;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                error = UpdateError.APK_VERSION_MISMATCH;
                break;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                error = UpdateError.DEVICE_UNSUPPORTED;
                break;
            case PackageInstaller.STATUS_FAILURE_INVALID:
                error = UpdateError.APK_UNREADABLE;
                break;
            case PackageInstaller.STATUS_FAILURE_STORAGE:
                error = UpdateError.INSUFFICIENT_STORAGE;
                break;
            default:
                error = UpdateError.INSTALL_BLOCKED;
                break;
        }
        if (h != null) {
            h.onInstallStatusMessage(getString(R.string.update_install_failed_prefix,
                    getString(errorTextRes(error))));
            h.onUpdateCheckRowTextChanged(statusLineText());
        }
    }

    // ===== 状态读取（供 Host 渲染） =====

    public String statusLineText() {
        if (readyApkFile != null) {
            return getString(R.string.update_check_ready_value);
        }
        if (checkInFlight) {
            return getString(R.string.update_check_checking);
        }
        if (lastAvailable != null) {
            return getString(R.string.update_check_available_value, lastAvailable.versionName());
        }
        if (lastCheckFailed) {
            return getString(R.string.update_check_failed_value);
        }
        return getString(R.string.update_check_value_current, localVersionName());
    }

    public UpdateDownloadState currentDownloadState() {
        return lastState;
    }

    public long currentDownloaded() {
        return lastDownloaded;
    }

    public long currentTotal() {
        return lastTotal;
    }

    public File readyApkFile() {
        return readyApkFile;
    }

    public int readyVersionCode() {
        return readyVersionCode;
    }

    public UpdateInfo lastAvailableInfo() {
        return lastAvailable;
    }

    public boolean isAutoDialogOfferPending() {
        return pendingAutoOffer != null;
    }

    // ===== UpdateDownloadController.Callbacks（已在主线程） =====

    @Override
    public void onStateChanged(UpdateDownloadState state, long downloaded, long total) {
        lastState = state;
        lastDownloaded = downloaded;
        lastTotal = total;
        Host h = currentHost();
        if (h != null) {
            h.onDownloadProgress(state, downloaded, total);
        }
    }

    @Override
    public void onReady(File apkFile, int versionCode) {
        readyApkFile = apkFile;
        readyVersionCode = versionCode;
        UpdateInfo info = lastAvailable;
        long size = info != null ? info.apkSize() : apkFile.length();
        String sha256 = info != null ? info.apkSha256() : "";
        prefs.setPendingApk(apkFile.getAbsolutePath(), versionCode, size, sha256);
        Host h = currentHost();
        if (h != null) {
            h.onDownloadReady();
        }
    }

    @Override
    public void onFailed(UpdateError error) {
        // 校验失败即删除目标与挂起状态（临时 .part 已由控制器删除）。
        if (readyApkFile != null) {
            File file = readyApkFile;
            if (file.exists() && !file.delete()) {
                file.deleteOnExit();
            }
        }
        readyApkFile = null;
        readyVersionCode = 0;
        prefs.clearPendingInstall();
        Host h = currentHost();
        if (h != null) {
            h.onDownloadFailed(getString(errorTextRes(error)));
        }
    }

    @Override
    public void onCancelled() {
        readyApkFile = null;
        readyVersionCode = 0;
        prefs.clearPendingInstall();
        Host h = currentHost();
        if (h != null) {
            h.onDownloadCancelled();
        }
    }

    // ===== 内部工具 =====

    private synchronized UpdateDownloadController controller() {
        if (controller == null && appContext != null) {
            controller = new UpdateDownloadController(appContext, this);
        }
        return controller;
    }

    private Host currentHost() {
        synchronized (hostLock) {
            return host;
        }
    }

    private int localVersionCode() {
        if (fixedLocalVersionCode != null) {
            return fixedLocalVersionCode;
        }
        try {
            return appContext.getPackageManager()
                    .getPackageInfo(appContext.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException exception) {
            return 0;
        }
    }

    private String localVersionName() {
        if (fixedLocalVersionName != null) {
            return fixedLocalVersionName;
        }
        try {
            PackageInfo info = appContext.getPackageManager()
                    .getPackageInfo(appContext.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            return "";
        }
    }

    private String getString(int resId) {
        // 测试构造无 appContext：返回占位串，测试只断言状态与回调次序。
        if (appContext == null) {
            return "[" + resId + "]";
        }
        return appContext.getString(resId);
    }

    private String getString(int resId, Object... args) {
        if (appContext == null) {
            return "[" + resId + "]";
        }
        return appContext.getString(resId, args);
    }

    /** “已是最新版 · xx”的相对时间：刚刚 / N 分钟前 / N 小时前 / 日期。 */
    private String relativeTime(long atMillis) {
        long delta = System.currentTimeMillis() - atMillis;
        if (delta < 60_000L) {
            return getString(R.string.update_time_just_now);
        }
        if (delta < 60L * 60_000L) {
            return getString(R.string.update_time_minutes_ago, (int) (delta / 60_000L));
        }
        if (delta < 24L * 60L * 60_000L) {
            return getString(R.string.update_time_hours_ago, (int) (delta / (60L * 60_000L)));
        }
        return new java.text.SimpleDateFormat("yyyy-M-d", Locale.getDefault())
                .format(new java.util.Date(atMillis));
    }

    static int errorTextRes(UpdateError error) {
        if (error == null) {
            return R.string.update_error_network;
        }
        switch (error) {
            case TIMEOUT:
                return R.string.update_error_timeout;
            case HTTP:
                return R.string.update_error_http;
            case INVALID_METADATA:
                return R.string.update_error_invalid_metadata;
            case UNSUPPORTED_SCHEMA:
                return R.string.update_error_unsupported_schema;
            case DEVICE_UNSUPPORTED:
                return R.string.update_error_device_unsupported;
            case INSUFFICIENT_STORAGE:
                return R.string.update_error_insufficient_storage;
            case CANCELLED:
                return R.string.update_error_cancelled;
            case FILE_SIZE_MISMATCH:
                return R.string.update_error_file_size;
            case HASH_MISMATCH:
                return R.string.update_error_hash;
            case APK_UNREADABLE:
                return R.string.update_error_apk_unreadable;
            case PACKAGE_MISMATCH:
                return R.string.update_error_package_mismatch;
            case APK_VERSION_MISMATCH:
                return R.string.update_error_apk_version_mismatch;
            case SIGNATURE_MISMATCH:
                return R.string.update_error_signature_mismatch;
            case NO_INSTALLER:
                return R.string.update_error_no_installer;
            case INSTALL_BLOCKED:
                return R.string.update_install_blocked;
            case STORAGE_WRITE_FAILED:
                return R.string.update_error_storage_write;
            case NETWORK:
            default:
                return R.string.update_error_network;
        }
    }
}



