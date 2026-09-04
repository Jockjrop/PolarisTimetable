package com.polaris.timetable.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 版本策略测试（计划 14.2）：只比较 versionCode、minSdk 兼容性、
 * 忽略版本（手动绕过、required 不可忽略）、24 小时节流与时钟回拨。
 */
public class UpdatePolicyTest {

    private UpdateInfo info(int versionCode, boolean required) {
        return info(versionCode, 23, required);
    }

    private UpdateInfo info(int versionCode, int minSdk, boolean required) {
        return new UpdateInfo(1, "stable", "com.polaris.timetable", versionCode,
                "1." + (versionCode / 100 % 100) + "." + (versionCode % 100), minSdk,
                0, "2026-09-10T12:00:00Z", "Polaris-1.27.0-release.apk",
                "https://github.com/x/releases/download/v1.27.0/Polaris-1.27.0-release.apk",
                1024L, UpdateJsonParserTest.SHA,
                java.util.Collections.singletonList("说明"), "", required);
    }

    @Test
    public void remoteNewerIsAvailable() {
        assertEquals(UpdatePolicy.Decision.AVAILABLE,
                UpdatePolicy.evaluate(info(12700, false), 12601, 35, 0, false));
        assertEquals(UpdatePolicy.Decision.AVAILABLE,
                UpdatePolicy.evaluate(info(12700, false), 12601, 35, 0, true));
    }

    @Test
    public void remoteEqualOrOlderIsUpToDate() {
        assertEquals(UpdatePolicy.Decision.UP_TO_DATE,
                UpdatePolicy.evaluate(info(12700, false), 12700, 35, 0, false));
        assertEquals(UpdatePolicy.Decision.UP_TO_DATE,
                UpdatePolicy.evaluate(info(12600, false), 12700, 35, 0, false));
    }

    @Test
    public void versionNameLooksNewerButCodeIsNot() {
        // versionName “2.0.0” 但 versionCode 仍是 12601：不构成更新。
        UpdateInfo trick = new UpdateInfo(1, "stable", "com.polaris.timetable", 12601,
                "2.0.0", 23, 0, "2026-09-10T12:00:00Z", "Polaris-2.0.0-release.apk",
                "https://github.com/x/releases/download/v2.0.0/Polaris-2.0.0-release.apk",
                1024L, UpdateJsonParserTest.SHA,
                java.util.Collections.singletonList("说明"), "", false);
        assertEquals(UpdatePolicy.Decision.UP_TO_DATE,
                UpdatePolicy.evaluate(trick, 12601, 35, 0, true));
    }

    @Test
    public void deviceBelowMinSdkIsUnsupported() {
        assertEquals(UpdatePolicy.Decision.DEVICE_UNSUPPORTED,
                UpdatePolicy.evaluate(info(12700, 30, false), 12601, 26, 0, false));
        assertEquals(UpdatePolicy.Decision.AVAILABLE,
                UpdatePolicy.evaluate(info(12700, 30, false), 12601, 30, 0, false));
    }

    @Test
    public void autoCheckHonorsIgnoredVersionButManualDoesNot() {
        assertEquals(UpdatePolicy.Decision.IGNORED_VERSION,
                UpdatePolicy.evaluate(info(12700, false), 12601, 35, 12700, false));
        assertEquals(UpdatePolicy.Decision.AVAILABLE,
                UpdatePolicy.evaluate(info(12700, false), 12601, 35, 12700, true));
    }

    @Test
    public void requiredVersionCanNotBeIgnored() {
        assertEquals(UpdatePolicy.Decision.AVAILABLE,
                UpdatePolicy.evaluate(info(12700, true), 12601, 35, 12700, false));
    }

    @Test
    public void differentIgnoredVersionDoesNotMatch() {
        assertEquals(UpdatePolicy.Decision.AVAILABLE,
                UpdatePolicy.evaluate(info(12700, false), 12601, 35, 12500, false));
    }

    @Test
    public void autoCheckThrottling() {
        long now = 1_800_000_000_000L;
        assertTrue(UpdatePolicy.shouldAutoCheck(0L, now));
        assertTrue(UpdatePolicy.shouldAutoCheck(now - UpdatePolicy.AUTO_CHECK_INTERVAL_MS, now));
        assertFalse(UpdatePolicy.shouldAutoCheck(
                now - UpdatePolicy.AUTO_CHECK_INTERVAL_MS + 60_000L, now));
        // 时钟回拨：不产生高频检查。
        assertFalse(UpdatePolicy.shouldAutoCheck(now + 60_000L, now));
    }
}
