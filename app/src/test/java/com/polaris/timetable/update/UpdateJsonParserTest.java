package com.polaris.timetable.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

/**
 * latest.json 解析与边界校验（计划 14.1）。纯 JVM 测试，不触网。
 */
public class UpdateJsonParserTest {

    static final String SHA =
            "5c99f983378f6c9d5a4ed50670f8e64207075b5051825c922ad98af9f2112c24";
    private static final String APK_URL =
            "https://github.com/Jockjrop/PolarisTimetable/releases/download/v1.27.0/Polaris-1.27.0-release.apk";
    private static final String NOTES_URL =
            "https://github.com/Jockjrop/PolarisTimetable/releases/tag/v1.27.0";

    private UpdateJsonParser parser;

    @Before
    public void setUp() {
        parser = new UpdateJsonParser("com.polaris.timetable");
    }

    private JSONObject base() throws Exception {
        return new JSONObject()
                .put("schemaVersion", 1)
                .put("channel", "stable")
                .put("packageName", "com.polaris.timetable")
                .put("versionCode", 12700)
                .put("versionName", "1.27.0")
                .put("minSdk", 23)
                .put("minSupportedVersionCode", 12601)
                .put("publishedAt", "2026-09-10T12:00:00Z")
                .put("apk", new JSONObject()
                        .put("fileName", "Polaris-1.27.0-release.apk")
                        .put("url", APK_URL)
                        .put("size", 18342190L)
                        .put("sha256", SHA))
                .put("releaseNotes", new org.json.JSONArray()
                        .put("新增应用内安全更新功能")
                        .put("优化更新失败后的错误提示"))
                .put("releaseNotesUrl", NOTES_URL)
                .put("required", false);
    }

    private String serialize(JSONObject object) {
        return object.toString();
    }

    private void assertInvalid(String body) {
        try {
            parser.parse(body);
            fail("expected InvalidManifestException");
        } catch (UpdateJsonParser.InvalidManifestException exception) {
            assertSame(UpdateError.INVALID_METADATA, exception.reason);
        }
    }

    private void assertUnsupported(String body) {
        try {
            parser.parse(body);
            fail("expected UNSUPPORTED_SCHEMA");
        } catch (UpdateJsonParser.InvalidManifestException exception) {
            assertSame(UpdateError.UNSUPPORTED_SCHEMA, exception.reason);
        }
    }

    @Test
    public void fullValidManifestParses() throws Exception {
        UpdateInfo info = parser.parse(serialize(base()));
        assertNotNull(info);
        assertEquals(1, info.schemaVersion());
        assertEquals("stable", info.channel());
        assertEquals("com.polaris.timetable", info.packageName());
        assertEquals(12700, info.versionCode());
        assertEquals("1.27.0", info.versionName());
        assertEquals(23, info.minSdk());
        assertEquals(12601, info.minSupportedVersionCode());
        assertEquals("2026-09-10T12:00:00Z", info.publishedAt());
        assertEquals("Polaris-1.27.0-release.apk", info.apkFileName());
        assertEquals(APK_URL, info.apkUrl());
        assertEquals(18342190L, info.apkSize());
        assertEquals(SHA, info.apkSha256());
        assertEquals(2, info.releaseNotes().size());
        assertEquals(NOTES_URL, info.releaseNotesUrl());
        assertEquals(false, info.required());
    }

    @Test
    public void unknownFieldsAreIgnored() throws Exception {
        JSONObject body = base();
        body.put("futureField", new JSONObject().put("anything", true));
        body.put("ttlSeconds", 42);
        assertNotNull(parser.parse(serialize(body)));
    }

    @Test
    public void everyMissingRequiredFieldIsRejected() throws Exception {
        String[] rootKeys = {"schemaVersion", "channel", "packageName", "versionCode",
                "versionName", "minSdk", "minSupportedVersionCode", "publishedAt",
                "releaseNotes", "releaseNotesUrl", "required"};
        for (String key : rootKeys) {
            JSONObject body = base();
            body.remove(key);
            assertInvalid(serialize(body));
        }
        String[] apkKeys = {"fileName", "url", "size", "sha256"};
        for (String key : apkKeys) {
            JSONObject body = base();
            body.getJSONObject("apk").remove(key);
            assertInvalid(serialize(body));
        }
        JSONObject body = base();
        body.remove("apk");
        assertInvalid(serialize(body));
    }

    @Test
    public void typeErrorsAreRejected() throws Exception {
        JSONObject body = base();
        body.put("versionCode", "12700");
        assertInvalid(serialize(body));
        body = base();
        body.put("required", "false");
        assertInvalid(serialize(body));
        body = base();
        body.getJSONObject("apk").put("size", "18342190");
        assertInvalid(serialize(body));
        body = base();
        body.put("releaseNotes", "text");
        assertInvalid(serialize(body));
        body = base();
        body.put("apk", "object");
        assertInvalid(serialize(body));
    }

    @Test
    public void schemaVersionTooNewIsRejectedAsUnsupported() throws Exception {
        JSONObject body = base();
        body.put("schemaVersion", 2);
        assertUnsupported(serialize(body));
    }

    @Test
    public void schemaVersionZeroIsRejected() throws Exception {
        JSONObject body = base();
        body.put("schemaVersion", 0);
        assertInvalid(serialize(body));
    }

    @Test
    public void nonStableChannelIsRejected() throws Exception {
        JSONObject body = base();
        body.put("channel", "beta");
        assertInvalid(serialize(body));
    }

    @Test
    public void wrongPackageNameIsRejected() throws Exception {
        JSONObject body = base();
        body.put("packageName", "com.other.app");
        assertInvalid(serialize(body));
    }

    @Test
    public void invalidVersionCodesAreRejected() throws Exception {
        for (int code : new int[]{0, -1, 12701}) {
            JSONObject body = base();
            body.put("versionCode", code);
            assertInvalid(serialize(body));
        }
    }

    @Test
    public void nonSemanticVersionNameIsRejected() throws Exception {
        for (String name : new String[]{"1.27", "v1.27.0", "1.27.0-beta", "a.b.c"}) {
            JSONObject body = base();
            body.put("versionName", name);
            assertInvalid(serialize(body));
        }
    }

    @Test
    public void plainHttpUrlIsRejected() throws Exception {
        JSONObject body = base();
        body.getJSONObject("apk").put("url", APK_URL.replace("https://", "http://"));
        assertInvalid(serialize(body));
    }

    @Test
    public void nonAllowedHostIsRejected() throws Exception {
        JSONObject body = base();
        body.getJSONObject("apk").put("url",
                "https://evil.example.com/v1.27.0/Polaris-1.27.0-release.apk");
        assertInvalid(serialize(body));
        body = base();
        body.put("releaseNotesUrl", "https://evil.example.com/releases/tag/v1.27.0");
        assertInvalid(serialize(body));
    }

    @Test
    public void pathTraversalInFileNameIsRejected() throws Exception {
        for (String name : new String[]{"Polaris-1.27.0-release.apk.part",
                "../Polaris-1.27.0-release.apk",
                "sub/Polaris-1.27.0-release.apk",
                "Polaris-1.27.0-release.apk\\x"}) {
            JSONObject body = base();
            body.getJSONObject("apk").put("fileName", name);
            assertInvalid(serialize(body));
        }
    }

    @Test
    public void apkSizeBoundsAreEnforced() throws Exception {
        for (long size : new long[]{0L, -1L, 200L * 1024L * 1024L + 1}) {
            JSONObject body = base();
            body.getJSONObject("apk").put("size", size);
            assertInvalid(serialize(body));
        }
    }

    @Test
    public void invalidSha256IsRejected() throws Exception {
        JSONObject body = base();
        body.getJSONObject("apk").put("sha256", SHA.substring(1));
        assertInvalid(serialize(body));
        body = base();
        body.getJSONObject("apk").put("sha256", SHA.toUpperCase());
        assertInvalid(serialize(body));
        body = base();
        body.getJSONObject("apk").put("sha256", "zz" + SHA.substring(2));
        assertInvalid(serialize(body));
    }

    @Test
    public void releaseNotesBoundsAreEnforced() throws Exception {
        JSONObject body = base();
        body.put("releaseNotes", new org.json.JSONArray());
        assertInvalid(serialize(body));

        body = base();
        org.json.JSONArray tooMany = new org.json.JSONArray();
        for (int i = 0; i < 21; i++) {
            tooMany.put("条目" + i);
        }
        body.put("releaseNotes", tooMany);
        assertInvalid(serialize(body));

        body = base();
        body.put("releaseNotes", new org.json.JSONArray().put(
                new String(new char[201]).replace('\0', '字')));
        assertInvalid(serialize(body));
    }

    @Test
    public void oversizedResponseBodyIsRejected() throws Exception {
        StringBuilder giant = new StringBuilder();
        giant.append("{");
        while (giant.length() <= UpdateJsonParser.MAX_MANIFEST_BYTES) {
            giant.append(" ");
        }
        assertInvalid(giant.toString());
    }

    @Test
    public void fileNameAndUrlTagMustMatchVersionName() throws Exception {
        JSONObject body = base();
        body.getJSONObject("apk").put("fileName", "Polaris-1.26.0-release.apk");
        assertInvalid(serialize(body));

        body = base();
        body.getJSONObject("apk").put("url", APK_URL.replace("v1.27.0", "v1.26.0"));
        assertInvalid(serialize(body));
    }

    @Test
    public void malformedPublishedAtIsRejected() throws Exception {
        for (String value : new String[]{"2026-09-10 12:00:00", "2026-09-10T12:00:00",
                "not-a-date", "2026-13-40T00:00:00Z"}) {
            JSONObject body = base();
            body.put("publishedAt", value);
            assertInvalid(serialize(body));
        }
    }

    @Test
    public void versionCodeMappingFollowsSemanticRule() {
        assertEquals(12700, UpdateJsonParser.versionCodeFromVersionName("1.27.0"));
        assertEquals(10001, UpdateJsonParser.versionCodeFromVersionName("1.0.1"));
        assertEquals(100000, UpdateJsonParser.versionCodeFromVersionName("10.0.0"));
        assertEquals(-1, UpdateJsonParser.versionCodeFromVersionName("1.27"));
        assertEquals(-1, UpdateJsonParser.versionCodeFromVersionName("a.b.c"));
        assertEquals(-1, UpdateJsonParser.versionCodeFromVersionName(null));
    }

    @Test
    public void urlAllowlistAndHttpsRules() throws Exception {
        assertTrue(UpdateJsonParser.isAllowedHost("github.com"));
        assertTrue(UpdateJsonParser.isAllowedHost("objects.githubusercontent.com"));
        assertTrue(UpdateJsonParser.isAllowedHost("release-assets.githubusercontent.com"));
        assertTrue(!UpdateJsonParser.isAllowedHost("evil.example.com"));
        assertNotNull(UpdateJsonParser.toAllowedHttpsUrl(
                "https://github.com/Jockjrop/PolarisTimetable/releases/latest/download/latest.json"));
        try {
            UpdateJsonParser.toAllowedHttpsUrl("http://github.com/latest.json");
            fail("http must be rejected");
        } catch (java.net.MalformedURLException expected) {
            // 预期拒绝
        }
    }

    @Test
    public void stringFieldsRejectNonStringValues() throws Exception {
        // U-P1-07：getString 的宽松转换不允许通过协议校验。
        String[] keys = {"channel", "packageName", "versionName", "publishedAt", "releaseNotesUrl"};
        for (String key : keys) {
            JSONObject body = base();
            body.put(key, 12345);
            assertInvalid(serialize(body));
            body = base();
            body.put(key, true);
            assertInvalid(serialize(body));
        }
        JSONObject body = base();
        body.getJSONObject("apk").put("fileName", 42);
        assertInvalid(serialize(body));
        body = base();
        body.getJSONObject("apk").put("url", 42);
        assertInvalid(serialize(body));
        body = base();
        body.getJSONObject("apk").put("sha256", 42);
        assertInvalid(serialize(body));
    }

    @Test
    public void apkSizeRejectsFractionalNumbers() throws Exception {
        // U-P1-08：小数会被静默截断，必须拒绝。
        JSONObject body = base();
        body.getJSONObject("apk").put("size", 18342190.5);
        assertInvalid(serialize(body));
    }

    @Test
    public void versionSegmentCollisionIsRejected() throws Exception {
        // U-P1-09：minor/patch 超出 0..99 会产生映射碰撞（1.2.100 == 1.3.0），必须拒绝。
        JSONObject body = base();
        body.put("versionName", "1.2.100");
        body.put("versionCode", 10300);
        assertInvalid(serialize(body));
        body = base();
        body.put("versionName", "1.100.0");
        body.put("versionCode", 20000);
        assertInvalid(serialize(body));
        assertEquals(-1, UpdateJsonParser.versionCodeFromVersionName("1.2.100"));
        assertEquals(-1, UpdateJsonParser.versionCodeFromVersionName("1.100.0"));
        assertEquals(-1, UpdateJsonParser.versionCodeFromVersionName("210001.0.0"));
    }

    @Test
    public void publishedAtMustBeFullyConsumed() throws Exception {
        // U-P1-12：整串必须被解析消费，尾巴字符不允许通过。
        JSONObject body = base();
        body.put("publishedAt", "2026-09-10T12:00:00Z尾巴");
        assertInvalid(serialize(body));
    }

    @Test
    public void publishedDatePartExtractsDateOnly() {
        assertEquals("2026-09-10", UpdateJsonParser.publishedDatePart("2026-09-10T12:00:00Z"));
        assertEquals("", UpdateJsonParser.publishedDatePart(null));
    }
}
