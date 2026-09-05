package com.polaris.timetable.update;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 更新系统专用偏好（SharedPreferences 文件 polaris_update）。
 * 只保存更新状态五个键；不得存储课程、学号、学校名称、设备唯一标识、IP 或 GitHub 凭据。
 * 存储层可注入以便本地单元测试。
 */
public final class UpdatePreferences {

    public static final String FILE_NAME = "polaris_update";
    private static final String KEY_AUTO_CHECK_ENABLED = "auto_check_enabled";
    private static final String KEY_LAST_SUCCESSFUL_CHECK_AT = "last_successful_check_at";
    private static final String KEY_IGNORED_VERSION_CODE = "ignored_version_code";
    private static final String KEY_PENDING_APK_PATH = "pending_apk_path";
    private static final String KEY_PENDING_VERSION_CODE = "pending_version_code";
    private static final String KEY_PENDING_APK_SIZE = "pending_apk_size";
    private static final String KEY_PENDING_APK_SHA256 = "pending_apk_sha256";
    private static final String KEY_PENDING_SESSION_ID = "pending_session_id";
    private static final String KEY_PENDING_CONFIRM_INTENT = "pending_confirm_intent";

    /** 最小键值存储抽象；真实现基于 SharedPreferences，测试用内存表。 */
    public interface Store {
        boolean getBoolean(String key, boolean defValue);

        void putBoolean(String key, boolean value);

        long getLong(String key, long defValue);

        void putLong(String key, long value);

        int getInt(String key, int defValue);

        void putInt(String key, int value);

        String getString(String key, String defValue);

        void putString(String key, String value);

        void remove(String key);
    }

    private final Store store;

    public UpdatePreferences(Store store) {
        this.store = store;
    }

    public static Store sharedPreferenceStore(Context context) {
        final SharedPreferences prefs =
                context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        return new Store() {
            @Override
            public boolean getBoolean(String key, boolean defValue) {
                return prefs.getBoolean(key, defValue);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                prefs.edit().putBoolean(key, value).apply();
            }

            @Override
            public long getLong(String key, long defValue) {
                return prefs.getLong(key, defValue);
            }

            @Override
            public void putLong(String key, long value) {
                prefs.edit().putLong(key, value).apply();
            }

            @Override
            public int getInt(String key, int defValue) {
                return prefs.getInt(key, defValue);
            }

            @Override
            public void putInt(String key, int value) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public String getString(String key, String defValue) {
                return prefs.getString(key, defValue);
            }

            @Override
            public void putString(String key, String value) {
                prefs.edit().putString(key, value).apply();
            }

            @Override
            public void remove(String key) {
                prefs.edit().remove(key).apply();
            }
        };
    }

    public boolean isAutoCheckEnabled() {
        return store.getBoolean(KEY_AUTO_CHECK_ENABLED, false);
    }

    public void setAutoCheckEnabled(boolean enabled) {
        store.putBoolean(KEY_AUTO_CHECK_ENABLED, enabled);
    }

    public long lastSuccessfulCheckAt() {
        return store.getLong(KEY_LAST_SUCCESSFUL_CHECK_AT, 0L);
    }

    public void setLastSuccessfulCheckAt(long atMillis) {
        store.putLong(KEY_LAST_SUCCESSFUL_CHECK_AT, atMillis);
    }

    public int ignoredVersionCode() {
        return store.getInt(KEY_IGNORED_VERSION_CODE, 0);
    }

    public void setIgnoredVersionCode(int versionCode) {
        store.putInt(KEY_IGNORED_VERSION_CODE, versionCode);
    }

    public String pendingApkPath() {
        return store.getString(KEY_PENDING_APK_PATH, null);
    }

    public int pendingVersionCode() {
        return store.getInt(KEY_PENDING_VERSION_CODE, 0);
    }

    public long pendingApkSize() {
        return store.getLong(KEY_PENDING_APK_SIZE, 0L);
    }

    public String pendingApkSha256() {
        return store.getString(KEY_PENDING_APK_SHA256, null);
    }

    /** 校验通过、等待安装的 APK：路径、目标版本、大小和 SHA-256；恢复时完整复验。 */
    public void setPendingApk(String apkPath, int versionCode, long apkSize, String apkSha256) {
        store.putString(KEY_PENDING_APK_PATH, apkPath);
        store.putInt(KEY_PENDING_VERSION_CODE, versionCode);
        store.putLong(KEY_PENDING_APK_SIZE, apkSize);
        store.putString(KEY_PENDING_APK_SHA256, apkSha256);
    }

    /** 已提交、等待用户确认的 PackageInstaller 会话。 */
    public void setPendingSession(int sessionId, int versionCode) {
        store.putInt(KEY_PENDING_SESSION_ID, sessionId);
        store.putInt(KEY_PENDING_VERSION_CODE, versionCode);
    }

    public int pendingSessionId() {
        return store.getInt(KEY_PENDING_SESSION_ID, -1);
    }

    /** 后台收到待确认状态时暂存的确认 Intent（toUri 持久化，返回应用后启动）。 */
    public void setPendingConfirmIntent(String intentUri) {
        store.putString(KEY_PENDING_CONFIRM_INTENT, intentUri);
    }

    public String pendingConfirmIntent() {
        return store.getString(KEY_PENDING_CONFIRM_INTENT, null);
    }

    public void clearPendingConfirmIntent() {
        store.remove(KEY_PENDING_CONFIRM_INTENT);
    }

    /** 会话接管 APK 后仅清理 APK 相关键，保留会话与版本键。 */
    public void clearPendingApkFile() {
        store.remove(KEY_PENDING_APK_PATH);
        store.remove(KEY_PENDING_APK_SIZE);
        store.remove(KEY_PENDING_APK_SHA256);
    }

    public void clearPendingInstall() {
        store.remove(KEY_PENDING_APK_PATH);
        store.remove(KEY_PENDING_VERSION_CODE);
        store.remove(KEY_PENDING_APK_SIZE);
        store.remove(KEY_PENDING_APK_SHA256);
        store.remove(KEY_PENDING_SESSION_ID);
        store.remove(KEY_PENDING_CONFIRM_INTENT);
    }
}
