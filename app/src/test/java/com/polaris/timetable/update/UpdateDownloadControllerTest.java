package com.polaris.timetable.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 下载与哈希测试（计划 14.3）：本地假流覆盖正常下载、长度不符、取消、哈希不匹配、
 * 重定向白名单、单任务约束与 .part 清理。不发起真实网络请求。
 */
public class UpdateDownloadControllerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String FILE_NAME = "Polaris-1.27.0-release.apk";

    private static final class FakeConnection extends HttpURLConnection {
        private final int responseCode;
        private final String location;
        private final Long contentLength;
        private final InputStream stream;

        FakeConnection(URL url, int responseCode, String location, Long contentLength,
                       InputStream stream) {
            super(url);
            this.responseCode = responseCode;
            this.location = location;
            this.contentLength = contentLength;
            this.stream = stream;
        }

        @Override
        public void connect() {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public String getHeaderField(String name) {
            return "Location".equals(name) ? location : null;
        }

        @Override
        public long getContentLengthLong() {
            return contentLength == null ? -1L : contentLength;
        }

        @Override
        public int getContentLength() {
            // 单元测试桩 SDK_INT < 24 时控制器走 int 版本分支。
            return contentLength == null ? -1 : (int) (long) contentLength;
        }

        @Override
        public InputStream getInputStream() {
            return stream;
        }
    }

    private static final class SlowStream extends InputStream {
        private int remaining;
        private final long delayMs;

        SlowStream(int bytes, long delayMs) {
            this.remaining = bytes;
            this.delayMs = delayMs;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
            remaining--;
            return 0x41;
        }
    }

    private static final class RecordingCallbacks implements UpdateDownloadController.Callbacks {
        final List<UpdateDownloadState> states = new ArrayList<>();
        final List<UpdateError> failures = new ArrayList<>();
        final List<File> readyFiles = new ArrayList<>();
        final CountDownLatch terminal = new CountDownLatch(1);

        @Override
        public void onStateChanged(UpdateDownloadState state, long downloaded, long total) {
            synchronized (states) {
                states.add(state);
            }
        }

        @Override
        public void onReady(File apkFile, int versionCode) {
            readyFiles.add(apkFile);
            terminal.countDown();
        }

        @Override
        public void onFailed(UpdateError error) {
            synchronized (failures) {
                failures.add(error);
            }
            terminal.countDown();
        }

        @Override
        public void onCancelled() {
            terminal.countDown();
        }
    }

    private UpdateInfo info(long size, String sha256) {
        return new UpdateInfo(1, "stable", "com.polaris.timetable", 12700, "1.27.0", 23,
                12601, "2026-09-10T12:00:00Z", FILE_NAME,
                "https://github.com/x/releases/download/v1.27.0/" + FILE_NAME,
                size, sha256, java.util.Collections.singletonList("说明"),
                "https://github.com/x/releases/tag/v1.27.0", false);
    }

    private UpdateDownloadController controller(UpdateDownloadController.Callbacks callbacks,
                                                UpdateDownloadController.ConnectionFactory factory) {
        return controller(callbacks, factory, null);
    }

    private UpdateDownloadController controller(UpdateDownloadController.Callbacks callbacks,
                                                UpdateDownloadController.ConnectionFactory factory,
                                                UpdateDownloadController.ArchiveVerifier verifier) {
        File partDir = new File(temporaryFolder.getRoot(), "cache-updates");
        File verifiedDir = new File(temporaryFolder.getRoot(), "files-updates");
        return new UpdateDownloadController(partDir, verifiedDir, callbacks, factory,
                Runnable::run,
                verifier != null ? verifier
                        : (context, apk, updateInfo, installedVersionCode, deviceSdk) ->
                        ApkVerifier.Result.success(),
                12601);
    }

    @Test
    public void normalDownloadRenamesPartAtomicallyAndReady() throws Exception {
        byte[] payload = new byte[4096];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        String sha = ApkVerifier.sha256Hex(payload);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        AtomicInteger opened = new AtomicInteger();
        UpdateDownloadController controller = controller(callbacks, url -> {
            opened.incrementAndGet();
            return new FakeConnection(url, 200, null, (long) payload.length,
                    new ByteArrayInputStream(payload));
        });

        controller.start(info(payload.length, sha));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(1, callbacks.readyFiles.size());
        assertEquals(FILE_NAME, callbacks.readyFiles.get(0).getName());
        assertTrue(callbacks.readyFiles.get(0).isFile());
        assertEquals(payload.length, callbacks.readyFiles.get(0).length());
        assertFalse(new File(temporaryFolder.getRoot(), FILE_NAME + ".part").exists());
        boolean sawReady = false;
        synchronized (callbacks.states) {
            for (UpdateDownloadState state : callbacks.states) {
                sawReady |= state == UpdateDownloadState.READY_TO_INSTALL;
            }
        }
        assertTrue(sawReady);
        assertEquals(1, opened.get());
        controller.shutdown();
    }

    @Test
    public void declaredLengthMismatchFails() throws Exception {
        byte[] payload = new byte[128];
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 200, null, (long) payload.length + 5,
                        new ByteArrayInputStream(payload)));

        controller.start(info(payload.length, ApkVerifier.sha256Hex(payload)));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(1, callbacks.failures.size());
        assertEquals(UpdateError.FILE_SIZE_MISMATCH, callbacks.failures.get(0));
        assertFalse(new File(temporaryFolder.getRoot(), FILE_NAME + ".part").exists());
        controller.shutdown();
    }

    @Test
    public void truncatedStreamFails() throws Exception {
        byte[] payload = new byte[100];
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 200, null, null,
                        new ByteArrayInputStream(payload, 0, 60)));

        controller.start(info(payload.length, ApkVerifier.sha256Hex(payload)));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.FILE_SIZE_MISMATCH, callbacks.failures.get(0));
        controller.shutdown();
    }

    @Test
    public void hashMismatchFailsAndCleansPart() throws Exception {
        byte[] payload = new byte[256];
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 200, null, (long) payload.length,
                        new ByteArrayInputStream(payload)));

        String otherSha = ApkVerifier.sha256Hex(new byte[]{1, 2, 3});
        controller.start(info(payload.length, otherSha));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.HASH_MISMATCH, callbacks.failures.get(0));
        assertFalse(new File(temporaryFolder.getRoot(), FILE_NAME + ".part").exists());
        controller.shutdown();
    }

    @Test
    public void cancelDuringDownloadReportsCancelled() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 200, null, null, new SlowStream(60, 30)));

        controller.start(info(60, ApkVerifier.sha256Hex(new byte[60])));
        Thread.sleep(100);
        controller.cancel();
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertFalse(new File(temporaryFolder.getRoot(), FILE_NAME + ".part").exists());
        controller.shutdown();
    }

    @Test
    public void httpErrorMapsToHttpFailure() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 404, null, null, null));

        controller.start(info(64, ApkVerifier.sha256Hex(new byte[64])));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.HTTP, callbacks.failures.get(0));
        controller.shutdown();
    }

    @Test
    public void redirectChainFollowsAllowedHosts() throws Exception {
        byte[] payload = new byte[32];
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url -> {
            if (url.getHost().equals("github.com")) {
                return new FakeConnection(url, 302,
                        "https://objects.githubusercontent.com/x/" + FILE_NAME, null, null);
            }
            return new FakeConnection(url, 200, null, (long) payload.length,
                    new ByteArrayInputStream(payload));
        });

        controller.start(info(payload.length, ApkVerifier.sha256Hex(payload)));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(1, callbacks.readyFiles.size());
        controller.shutdown();
    }

    @Test
    public void redirectToHttpIsRejected() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 302,
                        "http://objects.githubusercontent.com/x/" + FILE_NAME, null, null));

        controller.start(info(64, ApkVerifier.sha256Hex(new byte[64])));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.NETWORK, callbacks.failures.get(0));
        controller.shutdown();
    }

    @Test
    public void redirectToNonAllowedHostIsRejected() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 302,
                        "https://evil.example.com/x/" + FILE_NAME, null, null));

        controller.start(info(64, ApkVerifier.sha256Hex(new byte[64])));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.NETWORK, callbacks.failures.get(0));
        controller.shutdown();
    }

    @Test
    public void tooManyRedirectsFail() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 302,
                        "https://github.com/x/loop/" + FILE_NAME, null, null));

        controller.start(info(64, ApkVerifier.sha256Hex(new byte[64])));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.NETWORK, callbacks.failures.get(0));
        controller.shutdown();
    }

    @Test
    public void onlyOneDownloadTaskRunsAtATime() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 200, null, null, new SlowStream(40, 20)));

        byte[] streamBytes = new byte[40];
        java.util.Arrays.fill(streamBytes, (byte) 0x41);
        controller.start(info(40, ApkVerifier.sha256Hex(streamBytes)));
        assertTrue(controller.isBusy());
        // 第二次启动应被拒绝，不产生第二个任务。
        controller.start(info(40, ApkVerifier.sha256Hex(streamBytes)));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(1, callbacks.readyFiles.size());
        controller.shutdown();
    }

    @Test
    public void cleanTemporaryFilesInRemovesPartOnly() throws Exception {
        File part = new File(temporaryFolder.getRoot(), FILE_NAME + ".part");
        File apk = new File(temporaryFolder.getRoot(), FILE_NAME);
        assertTrue(part.createNewFile());
        assertTrue(apk.createNewFile());
        UpdateDownloadController.cleanTemporaryFilesIn(temporaryFolder.getRoot());
        assertFalse(part.exists());
        assertTrue(apk.exists());
    }

    @Test
    public void archiveVerifierFailurePropagates() throws Exception {
        byte[] payload = new byte[16];
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks,
                url -> new FakeConnection(url, 200, null, (long) payload.length,
                        new ByteArrayInputStream(payload)),
                (context, apk, updateInfo, installedVersionCode, deviceSdk) ->
                        ApkVerifier.Result.failure(UpdateError.PACKAGE_MISMATCH));

        controller.start(info(payload.length, ApkVerifier.sha256Hex(payload)));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.PACKAGE_MISMATCH, callbacks.failures.get(0));
        assertFalse(new File(new File(temporaryFolder.getRoot(), "cache-updates"),
                FILE_NAME + ".part").exists());
        controller.shutdown();
    }

    @Test
    public void firstDownloadCreatesMissingDirectories() throws Exception {
        // U-P0-01：父目录存在、cache-updates 子目录不存在时，首次下载必须自行创建并成功。
        byte[] payload = new byte[64];
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 200, null, (long) payload.length,
                        new ByteArrayInputStream(payload)));

        File partDir = new File(temporaryFolder.getRoot(), "cache-updates");
        assertFalse(partDir.exists());
        controller.start(info(payload.length, ApkVerifier.sha256Hex(payload)));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(1, callbacks.readyFiles.size());
        assertTrue(partDir.isDirectory());
        controller.shutdown();
    }

    @Test
    public void updatesPathOccupiedByRegularFileFailsWithStorageError() throws Exception {
        // U-P0-01：路径被同名普通文件占用时必须归为 STORAGE_WRITE_FAILED，不得误报网络错误。
        byte[] payload = new byte[64];
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 200, null, (long) payload.length,
                        new ByteArrayInputStream(payload)));
        // 直接把 verified 目录换成同名普通文件：先正常构造，再破坏目录条件。
        File verifiedDir = new File(temporaryFolder.getRoot(), "files-updates");
        controller.start(info(payload.length, ApkVerifier.sha256Hex(payload)));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(1, callbacks.readyFiles.size());
        // 破坏：删除目录并创建同名普通文件。
        java.nio.file.Files.walk(verifiedDir.toPath())
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> path.toFile().delete());
        try {
            assertTrue(verifiedDir.createNewFile());
        } catch (java.io.IOException expected) {
            // 已存在则忽略
        }
        RecordingCallbacks callbacks2 = new RecordingCallbacks();
        UpdateDownloadController controller2 = controller(callbacks2, url ->
                new FakeConnection(url, 200, null, (long) payload.length,
                        new ByteArrayInputStream(payload)));
        controller2.start(info(payload.length, ApkVerifier.sha256Hex(payload)));
        assertTrue(callbacks2.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.STORAGE_WRITE_FAILED, callbacks2.failures.get(0));
        controller.shutdown();
        controller2.shutdown();
    }

    @Test
    public void sameVersionTargetIsDeletedBeforeDownload() throws Exception {
        // U-P1-03：同名旧 target 已存在时，下载开始前必须删除，避免 renameTo 失败。
        byte[] payload = new byte[32];
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 200, null, (long) payload.length,
                        new ByteArrayInputStream(payload)));

        File verifiedDir = new File(temporaryFolder.getRoot(), "files-updates");
        assertTrue(verifiedDir.mkdirs() || verifiedDir.isDirectory());
        File staleTarget = new File(verifiedDir, FILE_NAME);
        FileOutputStream stale = new FileOutputStream(staleTarget);
        try {
            stale.write(new byte[]{9, 9, 9});
        } finally {
            stale.close();
        }

        controller.start(info(payload.length, ApkVerifier.sha256Hex(payload)));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(1, callbacks.readyFiles.size());
        assertEquals(payload.length, staleTarget.length());
        controller.shutdown();
    }

    @Test
    public void streamBeyondDeclaredSizeAbortsImmediately() throws Exception {
        // U-P1-04：响应超过清单声明大小时必须立即中止。
        byte[] oversized = new byte[256];
        java.util.Arrays.fill(oversized, (byte) 0x41);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url ->
                new FakeConnection(url, 200, null, null,
                        new ByteArrayInputStream(oversized)));

        controller.start(info(64, ApkVerifier.sha256Hex(new byte[64])));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.FILE_SIZE_MISMATCH, callbacks.failures.get(0));
        File partDir = new File(temporaryFolder.getRoot(), "cache-updates");
        String[] leftovers = partDir.list((dir, name) -> name.endsWith(".part"));
        assertTrue(leftovers == null || leftovers.length == 0);
        controller.shutdown();
    }

    @Test
    public void exactlyFiveRedirectsSucceedAndSixthFails() throws Exception {
        // U-P1-10：恰好 5 次重定向允许，第 6 次拒绝。
        byte[] payload = new byte[16];
        UpdateDownloadController.ConnectionFactory fiveHops = url ->
                new FakeConnection(url, 302,
                        "https://github.com/hop/" + FILE_NAME, null, null);
        RecordingCallbacks callbacks = new RecordingCallbacks();
        // 第 5 次重定向后命中 200：用响应码序列模拟（hop 计数由工厂内部维护）。
        AtomicInteger hops = new AtomicInteger();
        UpdateDownloadController controller = controller(callbacks, url -> {
            if (hops.incrementAndGet() <= 5) {
                return new FakeConnection(url, 302,
                        "https://github.com/hop" + hops.get() + "/" + FILE_NAME, null, null);
            }
            return new FakeConnection(url, 200, null, (long) payload.length,
                    new ByteArrayInputStream(payload));
        });
        controller.start(info(payload.length, ApkVerifier.sha256Hex(payload)));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(1, callbacks.readyFiles.size());
        controller.shutdown();

        AtomicInteger endless = new AtomicInteger();
        RecordingCallbacks callbacks2 = new RecordingCallbacks();
        UpdateDownloadController controller2 = controller(callbacks2, url -> {
            endless.incrementAndGet();
            return new FakeConnection(url, 302,
                    "https://github.com/loop" + endless.get() + "/" + FILE_NAME, null, null);
        });
        controller2.start(info(16, ApkVerifier.sha256Hex(new byte[16])));
        assertTrue(callbacks2.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.NETWORK, callbacks2.failures.get(0));
        controller2.shutdown();
    }

    @Test
    public void factoryTimeoutMapsToTimeoutError() throws Exception {
        RecordingCallbacks callbacks = new RecordingCallbacks();
        UpdateDownloadController controller = controller(callbacks, url -> {
            throw new java.net.SocketTimeoutException("fake timeout");
        });
        controller.start(info(16, ApkVerifier.sha256Hex(new byte[16])));
        assertTrue(callbacks.terminal.await(10, TimeUnit.SECONDS));
        assertEquals(UpdateError.TIMEOUT, callbacks.failures.get(0));
        controller.shutdown();
    }
}
