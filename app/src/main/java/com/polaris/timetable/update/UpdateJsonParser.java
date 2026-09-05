package com.polaris.timetable.update;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * latest.json 清单解析与全量边界校验（协议第 6 节）。
 * 只做纯解析，不访问网络、磁盘或 UI，保证可在本地单元测试覆盖。
 */
public final class UpdateJsonParser {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    public static final int MAX_MANIFEST_BYTES = 256 * 1024;
    public static final long MAX_APK_BYTES = 200L * 1024L * 1024L;
    /** Android 平台 versionCode 上限（2_100_000_000）。 */
    public static final long MAX_VERSION_CODE = 2_100_000_000L;
    public static final int MAX_RELEASE_NOTES = 20;
    public static final int MAX_RELEASE_NOTE_CHARS = 200;

    /** 更新下载与清单允许的主机白名单；迁移分发源必须随发版修改客户端。 */
    public static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "gitee.com",
            "foruda.gitee.com"));

    private static final Pattern VERSION_NAME_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final SimpleDateFormat PUBLISHED_AT_FORMAT = buildUtcFormat();

    private final String expectedPackageName;

    public UpdateJsonParser(String expectedPackageName) {
        this.expectedPackageName = expectedPackageName;
    }

    /** 清单非法时抛出，携带具体失败原因；schema 过新单独返回 UNSUPPORTED_SCHEMA。 */
    public static final class InvalidManifestException extends Exception {
        public final UpdateError reason;

        InvalidManifestException(UpdateError reason) {
            super(reason.name());
            this.reason = reason;
        }
    }

    public UpdateInfo parse(String body) throws InvalidManifestException {
        if (body == null
                || body.isEmpty()
                || body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_MANIFEST_BYTES) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (JSONException exception) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }

        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion > SUPPORTED_SCHEMA_VERSION) {
            throw new InvalidManifestException(UpdateError.UNSUPPORTED_SCHEMA);
        }
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        String channel = requireString(root, "channel");
        if (!"stable".equals(channel)) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        String packageName = requireString(root, "packageName");
        if (!expectedPackageName.equals(packageName)) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        String versionName = requireString(root, "versionName");
        if (!VERSION_NAME_PATTERN.matcher(versionName).matches()) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        int versionCode = requireInt(root, "versionCode");
        if (versionCode <= 0 || versionCodeFromVersionName(versionName) != versionCode) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        int minSdk = requireInt(root, "minSdk");
        if (minSdk < 0) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        int minSupportedVersionCode = requireInt(root, "minSupportedVersionCode");
        if (minSupportedVersionCode < 0) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        String publishedAt = requireString(root, "publishedAt");
        if (!isValidUtcTimestamp(publishedAt)) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }

        JSONObject apk = optObject(root, "apk");
        String apkFileName = requireString(apk, "fileName");
        String expectedFileName = "Polaris-" + versionName + "-release.apk";
        if (!expectedFileName.equals(apkFileName)) {
            // 固定命名同时排除了路径分隔符与路径穿越片段（如 ..）。
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        String apkUrl = requireString(apk, "url");
        if (!isValidHttpsUrl(apkUrl) || !urlContainsTagSegment(apkUrl, versionName)) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        long apkSize = requireLong(apk, "size");
        if (apkSize <= 0 || apkSize > MAX_APK_BYTES) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        String apkSha256 = requireString(apk, "sha256");
        if (!SHA256_PATTERN.matcher(apkSha256).matches()) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }

        List<String> releaseNotes = requireReleaseNotes(root);
        String releaseNotesUrl = requireString(root, "releaseNotesUrl");
        if (!isValidHttpsUrl(releaseNotesUrl)) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        boolean required = requireBoolean(root, "required");

        return new UpdateInfo(schemaVersion, channel, packageName, versionCode, versionName,
                minSdk, minSupportedVersionCode, publishedAt, apkFileName, apkUrl, apkSize,
                apkSha256, releaseNotes, releaseNotesUrl, required);
    }

    /** versionCode 严格按 major × 10000 + minor × 100 + patch 映射；
     *  minor/patch 必须在 0..99（防 1.2.100 与 1.3.0 碰撞），major ≥ 0，
     *  结果必须 &gt; 0 且不超过 Android versionCode 上限 2_100_000_000；越界返回 -1 视为非法。 */
    public static int versionCodeFromVersionName(String versionName) {
        if (versionName == null || !VERSION_NAME_PATTERN.matcher(versionName).matches()) {
            return -1;
        }
        String[] parts = versionName.split("\\.");
        try {
            long major = Long.parseLong(parts[0]);
            long minor = Long.parseLong(parts[1]);
            long patch = Long.parseLong(parts[2]);
            if (major < 0 || minor < 0 || minor > 99 || patch < 0 || patch > 99) {
                return -1;
            }
            long code = major * 10000L + minor * 100L + patch;
            if (code <= 0 || code > MAX_VERSION_CODE) {
                return -1;
            }
            return (int) code;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /** 主机必须在内置白名单内；未来迁移分发源需随发版更新客户端允许列表。 */
    public static boolean isAllowedHost(String host) {
        return host != null && ALLOWED_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    /** 校验并规范化更新相关 URL：必须 HTTPS 且主机在白名单内；明文 HTTP 直接拒绝。 */
    public static URL toAllowedHttpsUrl(String spec) throws MalformedURLException {
        URL url;
        try {
            url = new URL(spec);
        } catch (MalformedURLException exception) {
            throw new MalformedURLException("invalid url");
        }
        if (!"https".equalsIgnoreCase(url.getProtocol()) || !isAllowedHost(url.getHost())) {
            throw new MalformedURLException("url host not allowed");
        }
        return url;
    }

    private static List<String> requireReleaseNotes(JSONObject root) throws InvalidManifestException {
        JSONArray array;
        try {
            array = root.getJSONArray("releaseNotes");
        } catch (JSONException exception) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        if (array.length() < 1 || array.length() > MAX_RELEASE_NOTES) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        List<String> notes = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            String note;
            try {
                note = array.getString(index);
            } catch (JSONException exception) {
                throw new InvalidManifestException(UpdateError.INVALID_METADATA);
            }
            if (note == null || note.isEmpty() || note.length() > MAX_RELEASE_NOTE_CHARS) {
                throw new InvalidManifestException(UpdateError.INVALID_METADATA);
            }
            notes.add(note);
        }
        return notes;
    }

    // org.json 的 getInt 会把数字字符串宽松转换；协议要求“字段类型错误时失败”，
    // 这里对原始 JSON 类型做 instanceof 严格校验。
    private static int requireInt(JSONObject object, String key) throws InvalidManifestException {
        Object value = requireRawValue(object, key);
        if (!(value instanceof Integer)) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        return (Integer) value;
    }

    private static long requireLong(JSONObject object, String key) throws InvalidManifestException {
        // 只接受 JSON 整数（Integer/Long）；小数（如 1.5 → 1）静默截断不可接受。
        Object value = requireRawValue(object, key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        throw new InvalidManifestException(UpdateError.INVALID_METADATA);
    }

    private static boolean requireBoolean(JSONObject object, String key)
            throws InvalidManifestException {
        // 布尔字符串（"false"）会被 getBoolean 宽松转换，这里同样要求原始 JSON 布尔类型。
        Object value = requireRawValue(object, key);
        if (!(value instanceof Boolean)) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        return (Boolean) value;
    }

    private static Object requireRawValue(JSONObject object, String key)
            throws InvalidManifestException {
        if (!object.has(key) || object.isNull(key)) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        try {
            return object.get(key);
        } catch (JSONException exception) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
    }

    private static String requireString(JSONObject object, String key)
            throws InvalidManifestException {
        // getString 会把数字/布尔等非字符串值转换成字符串；协议要求类型错误必须失败，
        // 这里同样要求原始 JSON 字符串类型。
        Object value = requireRawValue(object, key);
        if (!(value instanceof String)) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        return (String) value;
    }

    private static JSONObject optObject(JSONObject object, String key)
            throws InvalidManifestException {
        JSONObject value = object.optJSONObject(key);
        if (value == null) {
            throw new InvalidManifestException(UpdateError.INVALID_METADATA);
        }
        return value;
    }

    private static boolean isValidHttpsUrl(String spec) {
        try {
            toAllowedHttpsUrl(spec);
            return true;
        } catch (MalformedURLException exception) {
            return false;
        }
    }

    /** APK URL 中必须包含 v&lt;versionName&gt; 标签段，实现清单、标签与文件名四方核对。 */
    private static boolean urlContainsTagSegment(String spec, String versionName) {
        try {
            URL url = new URL(spec);
            String path = url.getPath() == null ? "" : url.getPath();
            return path.contains("/v" + versionName + "/");
        } catch (MalformedURLException exception) {
            return false;
        }
    }

    private static boolean isValidUtcTimestamp(String value) {
        if (value == null || value.isEmpty() || !value.endsWith("Z")) {
            return false;
        }
        synchronized (PUBLISHED_AT_FORMAT) {
            // SimpleDateFormat.parse 不保证消费完整字符串（如 "…Z尾巴" 会通过），
            // 这里用 ParsePosition 严格校验整串被消费且无歧义。
            java.text.ParsePosition position = new java.text.ParsePosition(0);
            PUBLISHED_AT_FORMAT.parse(value, position);
            return position.getIndex() == value.length() && position.getErrorIndex() == -1;
        }
    }

    private static SimpleDateFormat buildUtcFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        return format;
    }

    /** 供 UI 层格式化发布日期：仅保留 yyyy-MM-dd 展示段，解析失败返回原文。 */
    public static String publishedDatePart(String publishedAt) {
        if (publishedAt != null && publishedAt.length() >= 10 && !publishedAt.isEmpty()) {
            return publishedAt.substring(0, 10);
        }
        return publishedAt == null ? "" : publishedAt;
    }
}
