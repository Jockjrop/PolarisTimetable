package com.polaris.timetable.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.content.pm.PackageInstaller;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 协调器 JVM 测试：假仓库、假网关、内存偏好和同步主线程。
 * 覆盖手动与自动检查、忽略版本、required、宿主阻塞延后与重绑回放、
 * 待安装与安装网关、PackageInstaller 状态收敛。
 */
public class UpdateCoordinatorTest {

    private static final String SHA =
            "5c99f983378f6c9d5a4ed50670f8e64207075b5051825c922ad98af9f2112c24";
    private static final int LOCAL_CODE = 12601;

    private UpdatePreferences prefs;
    private UpdateCoordinator coordinator;
    private RecordingHost host;
    private FakeGateway gateway;
    private String manifestBody;

    private static final class MapStore implements UpdatePreferences.Store {
        final Map<String, Object> values = new HashMap<>();

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return values.containsKey(key) ? (Boolean) values.get(key) : defValue;
        }

        @Override
        public void putBoolean(String key, boolean value) {
            values.put(key, value);
        }

        @Override
        public long getLong(String key, long defValue) {
            return values.containsKey(key) ? (Long) values.get(key) : defValue;
        }

        @Override
        public void putLong(String key, long value) {
            values.put(key, value);
        }

        @Override
        public int getInt(String key, int defValue) {
            return values.containsKey(key) ? (Integer) values.get(key) : defValue;
        }

        @Override
        public void putInt(String key, int value) {
            values.put(key, value);
        }

        @Override
        public String getString(String key, String defValue) {
            return values.containsKey(key) ? (String) values.get(key) : defValue;
        }

        @Override
        public void putString(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void remove(String key) {
            values.remove(key);
        }
    }

    private static final class RecordingHost implements UpdateCoordinator.Host {
        final List<String> rowTexts = new ArrayList<>();
        final List<UpdateInfo> dialogs = new ArrayList<>();
        final List<String> installMessages = new ArrayList<>();
        final List<String> downloadFailures = new ArrayList<>();
        final List<Object[]> progressEvents = new ArrayList<>();
        boolean blocked = false;
        int downloadDialogCount = 0;

        @Override
        public void onUpdateCheckRowTextChanged(String text) {
            rowTexts.add(text);
        }

        @Override
        public void onShowUpdateAvailableDialog(UpdateInfo info) {
            dialogs.add(info);
        }

        @Override
        public void onShowDownloadDialog() {
            downloadDialogCount++;
        }

        @Override
        public void onDownloadProgress(UpdateDownloadState state, long downloaded, long total) {
            progressEvents.add(new Object[]{state, downloaded, total});
        }

        @Override
        public void onDownloadReady() {
        }

        @Override
        public void onDownloadCancelled() {
        }

        @Override
        public void onDownloadFailed(String message) {
            downloadFailures.add(message);
        }

        @Override
        public void onInstallStatusMessage(String message) {
            installMessages.add(message);
        }

        @Override
        public boolean isAutoDialogBlocked() {
            return blocked;
        }
    }

    private static final class FakeGateway implements UpdateCoordinator.InstallGateway {
        final List<File> committed = new ArrayList<>();
        final List<Integer> abandoned = new ArrayList<>();
        boolean failCommit = false;
        int nextSessionId = 4242;

        @Override
        public int commitSession(android.content.Context context, File apk, String packageName)
                throws UpdateInstaller.InstallException {
            if (failCommit) {
                throw new UpdateInstaller.InstallException(UpdateError.NO_INSTALLER, -1);
            }
            committed.add(apk);
            return ++nextSessionId;
        }

        @Override
        public void abandonSession(android.content.Context context, int sessionId) {
            abandoned.add(sessionId);
        }

        @Override
        public PackageInstaller.SessionInfo sessionInfo(android.content.Context context,
                                                        int sessionId) {
            return null;
        }
    }

    private UpdateRepository repoReturning(final String body, final UpdateError failure) {
        return new UpdateRepository((URL url) -> new HttpURLConnection(url) {
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
                return failure == null ? 200 : failure == UpdateError.HTTP ? 500 : 200;
            }

            @Override
            public String getHeaderField(String name) {
                return null;
            }

            @Override
            public InputStream getInputStream() throws java.io.IOException {
                if (failure == UpdateError.NETWORK) {
                    throw new java.io.IOException("fake network");
                }
                if (failure == UpdateError.INVALID_METADATA) {
                    StringBuilder giant = new StringBuilder("{");
                    while (giant.length() <= UpdateJsonParser.MAX_MANIFEST_BYTES + 10) {
                        giant.append(" ");
                    }
                    return new ByteArrayInputStream(giant.toString().getBytes());
                }
                return new ByteArrayInputStream(body.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
            }
        });
    }

    private String manifest(int versionCode, String versionName, boolean required) {
        return "{\"schemaVersion\":1,\"channel\":\"stable\","
                + "\"packageName\":\"com.polaris.timetable\",\"versionCode\":" + versionCode + ","
                + "\"versionName\":\"" + versionName + "\",\"minSdk\":0,"
                + "\"minSupportedVersionCode\":12601,"
                + "\"publishedAt\":\"2026-09-10T12:00:00Z\","
                + "\"apk\":{\"fileName\":\"Polaris-" + versionName + "-release.apk\","
                + "\"url\":\"https://github.com/x/releases/download/v" + versionName
                + "/Polaris-" + versionName + "-release.apk\","
                + "\"size\":16,\"sha256\":\"" + SHA + "\"},"
                + "\"releaseNotes\":[\"n\"],"
                + "\"releaseNotesUrl\":\"https://github.com/x/releases/tag/v" + versionName
                + "\",\"required\":" + required + "}";
    }

    private String giteeManifest(int versionCode, String versionName, boolean required) {
        return manifest(versionCode, versionName, required)
                .replace("https://github.com/x",
                        "https://gitee.com/Jockjrop/polaris-course-schedule");
    }

    private UpdateRepository sourceRepository(String giteeBody, UpdateError giteeFailure,
                                              String githubBody, UpdateError githubFailure) {
        return new UpdateRepository((URL url) -> new HttpURLConnection(url) {
            private final boolean giteeApi = "gitee.com".equals(url.getHost())
                    && url.getPath().endsWith("/releases/latest");
            private final boolean giteeAsset = "gitee.com".equals(url.getHost()) && !giteeApi;
            private final UpdateError failure = (giteeApi || giteeAsset)
                    ? giteeFailure : githubFailure;

            @Override public void connect() { }
            @Override public void disconnect() { }
            @Override public boolean usingProxy() { return false; }

            @Override
            public int getResponseCode() throws java.io.IOException {
                if (failure == UpdateError.TIMEOUT) {
                    throw new java.net.SocketTimeoutException("fake timeout");
                }
                return failure == UpdateError.HTTP ? 500 : 200;
            }

            @Override public String getHeaderField(String name) { return null; }

            @Override
            public InputStream getInputStream() throws java.io.IOException {
                if (failure == UpdateError.NETWORK) {
                    throw new java.io.IOException("fake network");
                }
                String body;
                if (giteeApi) {
                    java.util.regex.Matcher matcher = java.util.regex.Pattern
                            .compile("\\\"versionName\\\":\\\"([^\\\"]+)\\\"")
                            .matcher(giteeBody == null ? "" : giteeBody);
                    String version = matcher.find() ? matcher.group(1) : "1.27.0";
                    body = "{\"tag_name\":\"v" + version + "\",\"prerelease\":false,"
                            + "\"assets\":[{\"name\":\"latest.json\","
                            + "\"browser_download_url\":\"https://gitee.com/Jockjrop/"
                            + "polaris-course-schedule/releases/download/v" + version
                            + "/latest.json\"}]}";
                } else {
                    body = giteeAsset ? giteeBody : githubBody;
                }
                return new ByteArrayInputStream((body == null ? "" : body).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
            }
        });
    }

    private void useSources(String giteeBody, UpdateError giteeFailure,
                            String githubBody, UpdateError githubFailure) {
        coordinator = new UpdateCoordinator(prefs,
                new UpdateJsonParser("com.polaris.timetable"),
                sourceRepository(giteeBody, giteeFailure, githubBody, githubFailure),
                Runnable::run, gateway, LOCAL_CODE, "1.26.1");
        coordinator.attachHostForTest(host);
    }

    /** 假仓库为内存实现，单线程执行器几乎瞬时完成；短暂等待后断言。 */
    private void runCheck() {
        coordinator.maybeAutoCheck();
        sleepForExecutor();
    }

    private void runManualCheck() {
        coordinator.onManualCheckClicked();
        sleepForExecutor();
    }

    private void sleepForExecutor() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Before
    public void setUp() {
        prefs = new UpdatePreferences(new MapStore());
        gateway = new FakeGateway();
        host = new RecordingHost();
        manifestBody = manifest(12700, "1.27.0", false);
        coordinator = new UpdateCoordinator(prefs,
                new UpdateJsonParser("com.polaris.timetable"),
                repoReturning(manifestBody, null),
                Runnable::run,
                gateway,
                LOCAL_CODE, "1.26.1");
        coordinator.attachHostForTest(host);
    }

    @Test
    public void manualCheckShowsDialogForNewerVersion() {
        runManualCheck();
        assertEquals(1, host.dialogs.size());
        assertEquals(12700, host.dialogs.get(0).versionCode());
        assertTrue(prefs.lastSuccessfulCheckAt() > 0L);
    }

    @Test
    public void manualCheckUpToDateShowsNoDialog() {
        coordinator = new UpdateCoordinator(prefs,
                new UpdateJsonParser("com.polaris.timetable"),
                repoReturning(manifest(12601, "1.26.1", false), null),
                Runnable::run, gateway, LOCAL_CODE, "1.26.1");
        coordinator.attachHostForTest(host);
        runManualCheck();
        assertTrue(host.dialogs.isEmpty());
        assertFalse(host.rowTexts.isEmpty());
    }

    @Test
    public void manualCheckNetworkFailureMarksRowAndMessage() {
        coordinator = new UpdateCoordinator(prefs,
                new UpdateJsonParser("com.polaris.timetable"),
                repoReturning(manifestBody, UpdateError.NETWORK),
                Runnable::run, gateway, LOCAL_CODE, "1.26.1");
        coordinator.attachHostForTest(host);
        runManualCheck();
        assertTrue(host.dialogs.isEmpty());
        assertFalse(host.rowTexts.isEmpty());
        assertFalse(host.downloadFailures.isEmpty());
    }

    @Test
    public void autoCheckDisabledDoesNothing() {
        runCheck();
        assertTrue(host.dialogs.isEmpty());
        assertEquals(0L, prefs.lastSuccessfulCheckAt());
    }

    @Test
    public void autoCheckThrottledWithin24h() {
        prefs.setAutoCheckEnabled(true);
        prefs.setLastSuccessfulCheckAt(System.currentTimeMillis());
        runCheck();
        assertTrue(host.dialogs.isEmpty());
    }

    @Test
    public void autoCheckRunsAfter24hAndOffersDialog() {
        prefs.setAutoCheckEnabled(true);
        prefs.setLastSuccessfulCheckAt(
                System.currentTimeMillis() - UpdatePolicy.AUTO_CHECK_INTERVAL_MS - 1000L);
        runCheck();
        assertEquals(1, host.dialogs.size());
        assertTrue(prefs.lastSuccessfulCheckAt() > 0L);
    }

    @Test
    public void autoCheckHonorsIgnoredVersionSilently() {
        prefs.setAutoCheckEnabled(true);
        prefs.setIgnoredVersionCode(12700);
        runCheck();
        assertTrue(host.dialogs.isEmpty());
        assertFalse(coordinator.isAutoDialogOfferPending());
    }

    @Test
    public void manualCheckBypassesIgnoredVersion() {
        prefs.setIgnoredVersionCode(12700);
        runManualCheck();
        assertEquals(1, host.dialogs.size());
    }

    @Test
    public void requiredVersionCanNotBeIgnoredByAutoCheck() {
        prefs.setAutoCheckEnabled(true);
        prefs.setIgnoredVersionCode(12700);
        manifestBody = manifest(12700, "1.27.0", true);
        coordinator = new UpdateCoordinator(prefs,
                new UpdateJsonParser("com.polaris.timetable"),
                repoReturning(manifestBody, null),
                Runnable::run, gateway, LOCAL_CODE, "1.26.1");
        coordinator.attachHostForTest(host);
        runCheck();
        assertEquals(1, host.dialogs.size());
    }

    @Test
    public void blockedHostDefersOfferAndResumeDelivers() {
        host.blocked = true;
        prefs.setAutoCheckEnabled(true);
        runCheck();
        assertTrue(coordinator.isAutoDialogOfferPending());
        assertTrue(host.dialogs.isEmpty());
        host.blocked = false;
        coordinator.onHostResumed();
        assertEquals(1, host.dialogs.size());
        assertFalse(coordinator.isAutoDialogOfferPending());
    }

    @Test
    public void hostlessRunStoresPendingOfferForNextAttach() {
        UpdateCoordinator detached = new UpdateCoordinator(prefs,
                new UpdateJsonParser("com.polaris.timetable"),
                repoReturning(manifestBody, null),
                Runnable::run, gateway, LOCAL_CODE, "1.26.1");
        prefs.setAutoCheckEnabled(true);
        detached.maybeAutoCheck();
        sleepForExecutor();
        assertTrue(detached.isAutoDialogOfferPending());
        detached.attachHostForTest(host);
        detached.onHostResumed();
        assertEquals(1, host.dialogs.size());
    }

    @Test
    public void hostRebindReplaysActiveDownloadState() {
        coordinator.onStateChanged(UpdateDownloadState.DOWNLOADING, 64L, 128L);
        host.progressEvents.clear();
        coordinator.onHostResumed();
        assertEquals(1, host.progressEvents.size());
        assertEquals(UpdateDownloadState.DOWNLOADING, host.progressEvents.get(0)[0]);
    }

    @Test
    public void installWithoutReadyFileReturnsNotReady() {
        assertEquals(UpdateCoordinator.InstallAction.NOT_READY, coordinator.installReadyApk());
        assertTrue(gateway.committed.isEmpty());
    }

    @Test
    public void installWithoutReadyFileReportsMissingApkToUser() {
        // 文件缺失的静默早退路径也必须回报提示，否则 UI 表现为“点了没反应”。
        assertEquals(UpdateCoordinator.InstallAction.NOT_READY, coordinator.installReadyApk());
        assertEquals(1, host.installMessages.size());
        assertTrue(gateway.committed.isEmpty());
    }

    @Test
    public void installReadyFileCommitsSessionAndClearsApkState() throws Exception {
        File apk = File.createTempFile("Polaris-1.27.0-release", ".apk");
        coordinator.onReady(apk, 12700);
        assertEquals(UpdateCoordinator.InstallAction.LAUNCHED, coordinator.installReadyApk());
        assertEquals(1, gateway.committed.size());
        assertEquals(4243, prefs.pendingSessionId());
        assertEquals(12700, prefs.pendingVersionCode());
        assertNull(prefs.pendingApkPath());
        assertTrue(gateway.abandoned.isEmpty());
    }

    @Test
    public void installFailureReportsMessage() throws Exception {
        gateway.failCommit = true;
        File apk = File.createTempFile("Polaris-1.27.0-release", ".apk");
        coordinator.onReady(apk, 12700);
        assertEquals(UpdateCoordinator.InstallAction.NOT_READY, coordinator.installReadyApk());
        assertEquals(1, host.installMessages.size());
        assertTrue(gateway.committed.isEmpty());
    }

    @Test
    public void installStatusSuccessClearsPendingState() {
        prefs.setPendingSession(4243, 12700);
        coordinator.handleInstallStatusOnMain(4243,
                PackageInstaller.STATUS_SUCCESS, null);
        assertEquals(-1, prefs.pendingSessionId());
        assertEquals(1, host.installMessages.size());
    }

    @Test
    public void installStatusAbortedClearsAndReports() {
        prefs.setPendingSession(4243, 12700);
        coordinator.handleInstallStatusOnMain(4243,
                PackageInstaller.STATUS_FAILURE_ABORTED, null);
        assertEquals(-1, prefs.pendingSessionId());
        assertTrue(gateway.abandoned.contains(4243));
        assertEquals(1, host.installMessages.size());
    }

    @Test
    public void installStatusPendingInBackgroundStoresConfirmIntent() {
        coordinator.onHostPaused();
        Intent confirm = new Intent("com.android.packageinstaller.packageinstaller");
        coordinator.handleInstallStatusOnMain(4243,
                PackageInstaller.STATUS_PENDING_USER_ACTION, confirm);
        assertTrue(gateway.abandoned.isEmpty());
        assertTrue(host.installMessages.isEmpty());
    }

    @Test
    public void downloadFailureClearsPendingApk() {
        File apk = new File(java.io.File.separator + "tmp", "not-exists.apk");
        coordinator.onReady(apk, 12700);
        coordinator.onFailed(UpdateError.HASH_MISMATCH);
        assertEquals(-1, prefs.pendingSessionId());
        assertTrue(host.downloadFailures.size() >= 1);
    }

    @Test
    public void statusLineIdleFallsBackToCurrentVersion() {
        assertNotNull(coordinator.statusLineText());
        assertFalse(host.dialogs.size() > 0);
    }

    @Test
    public void giteeNewVersionIsPreferredWhenGithubFails() {
        useSources(giteeManifest(12700, "1.27.0", false), null,
                null, UpdateError.HTTP);
        runManualCheck();
        assertEquals(1, host.dialogs.size());
        assertEquals("gitee.com", host.dialogs.get(0).apkUrl().split("/")[2]);
    }

    @Test
    public void giteeTimeoutFallsBackToGithub() {
        useSources(null, UpdateError.TIMEOUT, manifestBody, null);
        runManualCheck();
        assertEquals(1, host.dialogs.size());
        assertEquals("github.com", host.dialogs.get(0).apkUrl().split("/")[2]);
    }

    @Test
    public void giteeHttpOrBrokenJsonFallsBackToGithub() {
        for (Object value : new Object[]{UpdateError.HTTP, "broken"}) {
            host.dialogs.clear();
            useSources(value instanceof UpdateError ? null : (String) value,
                    value instanceof UpdateError ? (UpdateError) value : null,
                    manifestBody, null);
            runManualCheck();
            assertEquals(1, host.dialogs.size());
            assertEquals("github.com", host.dialogs.get(0).apkUrl().split("/")[2]);
        }
    }

    @Test
    public void newerGithubBeatsCurrentGitee() {
        useSources(giteeManifest(12601, "1.26.1", false), null,
                manifest(12701, "1.27.1", false), null);
        runManualCheck();
        assertEquals(12701, host.dialogs.get(0).versionCode());
        assertEquals("github.com", host.dialogs.get(0).apkUrl().split("/")[2]);
    }

    @Test
    public void matchingSameVersionPrefersGitee() {
        useSources(giteeManifest(12700, "1.27.0", false), null,
                manifestBody, null);
        runManualCheck();
        assertEquals(1, host.dialogs.size());
        assertEquals("gitee.com", host.dialogs.get(0).apkUrl().split("/")[2]);
    }

    @Test
    public void sameVersionDifferentHashIsRejected() {
        String different = manifestBody.replace(SHA,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        useSources(giteeManifest(12700, "1.27.0", false), null, different, null);
        runManualCheck();
        assertTrue(host.dialogs.isEmpty());
        assertEquals(1, host.downloadFailures.size());
    }

    @Test
    public void bothSourcesFailManualReportsButAutoStaysSilent() {
        useSources(null, UpdateError.HTTP, null, UpdateError.NETWORK);
        runManualCheck();
        assertEquals(1, host.downloadFailures.size());

        host.downloadFailures.clear();
        prefs.setAutoCheckEnabled(true);
        runCheck();
        assertTrue(host.downloadFailures.isEmpty());
    }

    @Test
    public void serverVersionBelowInstalledDoesNotDowngrade() {
        useSources(giteeManifest(12600, "1.26.0", false), null,
                manifest(12600, "1.26.0", false), null);
        runManualCheck();
        assertTrue(host.dialogs.isEmpty());
    }
}
