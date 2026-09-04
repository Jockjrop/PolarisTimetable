package com.polaris.timetable.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 更新偏好存储测试：默认值、读写回路与待安装状态清理。
 * 用内存 Store 隔离，不依赖 Android SharedPreferences。
 */
public class UpdatePreferencesTest {

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

    @Test
    public void defaultsMatchPlan() {
        UpdatePreferences prefs = new UpdatePreferences(new MapStore());
        assertFalse(prefs.isAutoCheckEnabled());
        assertEquals(0L, prefs.lastSuccessfulCheckAt());
        assertEquals(0, prefs.ignoredVersionCode());
        assertNull(prefs.pendingApkPath());
        assertEquals(0, prefs.pendingVersionCode());
    }

    @Test
    public void autoCheckRoundTrip() {
        MapStore store = new MapStore();
        UpdatePreferences prefs = new UpdatePreferences(store);
        prefs.setAutoCheckEnabled(true);
        assertTrue(prefs.isAutoCheckEnabled());
        assertTrue(store.values.containsKey("auto_check_enabled"));
        prefs.setAutoCheckEnabled(false);
        assertFalse(prefs.isAutoCheckEnabled());
    }

    @Test
    public void lastSuccessfulCheckRoundTrip() {
        UpdatePreferences prefs = new UpdatePreferences(new MapStore());
        prefs.setLastSuccessfulCheckAt(1234567890L);
        assertEquals(1234567890L, prefs.lastSuccessfulCheckAt());
    }

    @Test
    public void ignoredVersionRoundTrip() {
        UpdatePreferences prefs = new UpdatePreferences(new MapStore());
        prefs.setIgnoredVersionCode(12700);
        assertEquals(12700, prefs.ignoredVersionCode());
    }

    @Test
    public void pendingApkRoundTripAndClear() {
        UpdatePreferences prefs = new UpdatePreferences(new MapStore());
        prefs.setPendingApk("/data/files/updates/x.apk", 12700, 14853380L,
                "5c99f983378f6c9d5a4ed50670f8e64207075b5051825c922ad98af9f2112c24");
        assertEquals("/data/files/updates/x.apk", prefs.pendingApkPath());
        assertEquals(12700, prefs.pendingVersionCode());
        assertEquals(14853380L, prefs.pendingApkSize());
        assertEquals("5c99f983378f6c9d5a4ed50670f8e64207075b5051825c922ad98af9f2112c24",
                prefs.pendingApkSha256());
        prefs.clearPendingInstall();
        assertNull(prefs.pendingApkPath());
        assertEquals(0, prefs.pendingVersionCode());
        assertEquals(0L, prefs.pendingApkSize());
        assertNull(prefs.pendingApkSha256());
    }

    @Test
    public void pendingSessionRoundTripAndClear() {
        UpdatePreferences prefs = new UpdatePreferences(new MapStore());
        prefs.setPendingSession(4242, 12700);
        assertEquals(4242, prefs.pendingSessionId());
        assertEquals(12700, prefs.pendingVersionCode());
        prefs.setPendingConfirmIntent("intent:#Intent;action=x;end");
        assertEquals("intent:#Intent;action=x;end", prefs.pendingConfirmIntent());
        prefs.clearPendingInstall();
        assertEquals(-1, prefs.pendingSessionId());
        assertNull(prefs.pendingConfirmIntent());
    }

    @Test
    public void clearPendingApkFileKeepsSessionKeys() {
        UpdatePreferences prefs = new UpdatePreferences(new MapStore());
        prefs.setPendingApk("/data/files/updates/x.apk", 12700, 16L, "abc");
        prefs.setPendingSession(4242, 12700);
        prefs.clearPendingApkFile();
        assertNull(prefs.pendingApkPath());
        assertEquals(0L, prefs.pendingApkSize());
        assertEquals(4242, prefs.pendingSessionId());
        assertEquals(12700, prefs.pendingVersionCode());
    }
}
