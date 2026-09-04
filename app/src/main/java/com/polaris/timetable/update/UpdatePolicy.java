package com.polaris.timetable.update;

/**
 * 更新判断集中实现（协议第 7.1 节 UpdatePolicy）。
 * 是否更新只依据 versionCode；minSdk 不兼容时给出设备不受支持结论；
 * 自动检查遵守忽略版本，手动检查不受限。
 */
public final class UpdatePolicy {

    /** 距离上次成功检查 24 小时内不再自动联网。 */
    public static final long AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private UpdatePolicy() {
    }

    public enum Decision {
        AVAILABLE,
        UP_TO_DATE,
        DEVICE_UNSUPPORTED,
        IGNORED_VERSION,
    }

    public static Decision evaluate(UpdateInfo info, int localVersionCode, int deviceSdk,
                                    int ignoredVersionCode, boolean manualCheck) {
        if (info == null) {
            return Decision.UP_TO_DATE;
        }
        if (info.minSdk() > deviceSdk) {
            return Decision.DEVICE_UNSUPPORTED;
        }
        if (info.versionCode() <= localVersionCode) {
            return Decision.UP_TO_DATE;
        }
        // 忽略版本只对自动检查生效；required 的安全更新不允许被忽略，但仍保留“稍后”入口。
        if (!manualCheck && ignoredVersionCode == info.versionCode() && !info.required()) {
            return Decision.IGNORED_VERSION;
        }
        return Decision.AVAILABLE;
    }

    /**
     * 自动检查节流：从未成功检查过则允许；系统时钟回拨（last &gt; now）时不产生高频检查，
     * 等真实时间追上后再恢复。
     */
    public static boolean shouldAutoCheck(long lastSuccessfulCheckAt, long nowMillis) {
        if (lastSuccessfulCheckAt <= 0L) {
            return true;
        }
        if (nowMillis < lastSuccessfulCheckAt) {
            return false;
        }
        return nowMillis - lastSuccessfulCheckAt >= AUTO_CHECK_INTERVAL_MS;
    }
}
