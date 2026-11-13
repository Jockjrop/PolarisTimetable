package com.polaris.timetable.sharing;

import android.net.Uri;
import android.util.Base64;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class ScheduleShareCodec {
    private static final String SCHEME = "polaris";
    private static final String HOST = "schedule";
    private static final String SHORT_HOST = "s";
    private static final String PATH_IMPORT = "/import";
    private static final String QUERY_PAYLOAD = "payload";
    private static final String QUERY_DATA = "d";
    /** v2：加密载荷前缀（v1 是 "z" 表示压缩，无前缀表示裸 Base64）。 */
    private static final String ENCRYPTED_PREFIX = "e";
    private static final int FORMAT_VERSION = 2;

    private ScheduleShareCodec() {
    }

    /**
     * 生成分享链接：二进制打包 → deflate → AES-GCM 加密 → Base64URL。
     *
     * <p>链接里只有密文，不含课程明文、账户名、学校或设备标识；
     * 同一份课表每次生成的链接都不同（随机盐 + IV）。
     */
    public static String encodeLink(List<Course> courses) throws JSONException {
        try {
            byte[] encrypted = LinkCipher.encrypt(deflate(pack(courses)));
            String payload = ENCRYPTED_PREFIX + Base64.encodeToString(encrypted,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            return new Uri.Builder()
                    .scheme(SCHEME)
                    .authority(SHORT_HOST)
                    .appendQueryParameter(QUERY_DATA, payload)
                    .build()
                    .toString();
        } catch (GeneralSecurityException exception) {
            throw new JSONException("分享链接加密失败：" + exception.getMessage());
        }
    }

    public static boolean isImportLink(Uri uri) {
        if (uri == null || !SCHEME.equals(uri.getScheme())) {
            return false;
        }
        return SHORT_HOST.equals(uri.getHost())
                || (HOST.equals(uri.getHost()) && PATH_IMPORT.equals(uri.getPath()));
    }

    public static List<Course> decodeLink(Uri uri) throws JSONException, IllegalArgumentException {
        if (!isImportLink(uri)) {
            throw new IllegalArgumentException("不是 Polaris 课表分享链接");
        }
        String payload = SHORT_HOST.equals(uri.getHost())
                ? uri.getQueryParameter(QUERY_DATA)
                : uri.getQueryParameter(QUERY_PAYLOAD);
        if (payload == null || payload.length() == 0) {
            throw new IllegalArgumentException("分享链接缺少课程数据");
        }
        if (payload.startsWith(ENCRYPTED_PREFIX)) {
            return decodeEncrypted(payload.substring(ENCRYPTED_PREFIX.length()));
        }
        byte[] bytes;
        if (payload.startsWith("z")) {
            bytes = inflate(Base64.decode(payload.substring(1),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING));
        } else {
            bytes = Base64.decode(payload, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (SHORT_HOST.equals(uri.getHost())) {
            return decodeCompact(text);
        }
        JSONObject root = new JSONObject(text);
        org.json.JSONArray items = root.optJSONArray("c");
        List<Course> courses = new ArrayList<>();
        if (items == null) {
            return courses;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String name = item.optString("n", "").trim();
            if (name.length() == 0) {
                continue;
            }
            int day = item.optInt("d", -1);
            int start = item.optInt("s", 1);
            int end = item.optInt("e", start);
            int startMinute = item.optInt("sm", -1);
            int endMinute = item.optInt("em", -1);
            CourseTimeMode timeMode = CourseTimeMode.fromStorage(
                    item.optString("m", ""), day, start, end, startMinute, endMinute);
            CourseType courseType = CourseType.fromStorage(
                    item.optString("courseType", item.optString("type", "")));
            boolean bannerOnly = courseType.supportsBannerOnly() && timeMode == CourseTimeMode.NONE;
            boolean scheduled = timeMode == CourseTimeMode.CLOCK
                    ? day >= 0 && day <= 6 && startMinute >= 0 && startMinute < endMinute
                    : day >= 0 && day <= 6 && start >= 1 && end >= start;
            if (!bannerOnly && !scheduled) {
                continue;
            }
            courses.add(new Course(day, start, end, name, item.optString("w", "周次见PDF"),
                    item.optString("l", ""), item.optString("t", ""), "",
                    item.optString("credit", item.optString("c", "")),
                    item.optString("color", ""),
                    courseType, "", "", timeMode, startMinute, endMinute));
        }
        return courses;
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    // ---- v2：加密的紧凑二进制格式 ----

    private static List<Course> decodeEncrypted(String body) {
        if (body.length() == 0) {
            throw new IllegalArgumentException("分享链接缺少课程数据");
        }
        byte[] encrypted;
        try {
            encrypted = Base64.decode(body, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("分享链接格式不正确");
        }
        byte[] packed;
        try {
            packed = inflate(LinkCipher.decrypt(encrypted));
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("分享链接已损坏或被修改，请重新获取");
        }
        return unpack(packed);
    }

    /**
     * 二进制打包。相比 v1 的制表符文本，省掉了分隔符、枚举名和重复字段，
     * 也为后续加密腾出长度余量。包级可见：供单元测试直接验证往返。
     */
    static byte[] pack(List<Course> courses) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(FORMAT_VERSION);
        List<Course> list = courses == null ? new ArrayList<Course>() : courses;
        writeVarint(out, list.size());
        // 不做手动字典化：deflate 对重复字符串的处理已经足够，
        // 去重后压缩率反而下降（实测 302 → 305 字节），只剩元数据开销。
        for (Course course : list) {
            out.write(clampInt(course.day + 1, 0, 255));
            out.write(clampInt(course.startSection, 0, 255));
            out.write(clampInt(course.endSection, 0, 255));
            out.write(timeModeCode(course.timeMode));
            writeVarint(out, course.startMinuteOfDay + 1);
            writeVarint(out, course.endMinuteOfDay + 1);
            out.write(typeCode(course.courseType));
            writeString(out, course.name);
            writeString(out, course.weeks);
            writeString(out, course.location);
            writeString(out, course.teacher);
            writeString(out, course.credit);
            writeColor(out, course.color);
        }
        return out.toByteArray();
    }

    static List<Course> unpack(byte[] data) {
        List<Course> courses = new ArrayList<>();
        if (data == null || data.length < 2) {
            throw new IllegalArgumentException("分享数据为空");
        }
        int[] cursor = {0};
        int version = data[cursor[0]++] & 0xFF;
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("不支持的分享数据版本：" + version);
        }
        int count = readVarint(data, cursor);
        for (int i = 0; i < count && cursor[0] < data.length; i++) {
            int day = (data[cursor[0]++] & 0xFF) - 1;
            int start = data[cursor[0]++] & 0xFF;
            int end = data[cursor[0]++] & 0xFF;
            CourseTimeMode timeMode = timeModeFromCode(data[cursor[0]++] & 0xFF);
            int startMinute = readVarint(data, cursor) - 1;
            int endMinute = readVarint(data, cursor) - 1;
            CourseType courseType = typeFromCode(data[cursor[0]++] & 0xFF);
            String name = readString(data, cursor);
            String weeks = readString(data, cursor);
            String location = readString(data, cursor);
            String teacher = readString(data, cursor);
            String credit = readString(data, cursor);
            String color = readColor(data, cursor);
            addIfValid(courses, day, start, end, name, weeks, location, teacher,
                    credit, color, courseType, timeMode, startMinute, endMinute);
        }
        return courses;
    }

    private static void addIfValid(List<Course> target, int day, int start, int end,
                                   String name, String weeks, String location, String teacher,
                                   String credit, String color, CourseType courseType,
                                   CourseTimeMode timeMode, int startMinute, int endMinute) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() == 0) {
            return;
        }
        boolean bannerOnly = courseType.supportsBannerOnly() && timeMode == CourseTimeMode.NONE;
        boolean scheduled = timeMode == CourseTimeMode.CLOCK
                ? day >= 0 && day <= 6 && startMinute >= 0 && startMinute < endMinute
                : day >= 0 && day <= 6 && start >= 1 && end >= start;
        if (!bannerOnly && !scheduled) {
            return;
        }
        target.add(new Course(day, start, end, trimmed, weeks, location, teacher, "",
                credit, color, courseType, "", "", timeMode, startMinute, endMinute));
    }

    private static int timeModeCode(CourseTimeMode mode) {
        if (mode == CourseTimeMode.CLOCK) {
            return 1;
        }
        return mode == CourseTimeMode.NONE ? 2 : 0;
    }

    private static CourseTimeMode timeModeFromCode(int code) {
        if (code == 1) {
            return CourseTimeMode.CLOCK;
        }
        return code == 2 ? CourseTimeMode.NONE : CourseTimeMode.SECTION;
    }

    private static int typeCode(CourseType type) {
        if (type == CourseType.EXPERIMENT) {
            return 1;
        }
        if (type == CourseType.PRACTICE) {
            return 2;
        }
        return type == CourseType.ONLINE ? 3 : 0;
    }

    private static CourseType typeFromCode(int code) {
        switch (code) {
            case 1:
                return CourseType.EXPERIMENT;
            case 2:
                return CourseType.PRACTICE;
            case 3:
                return CourseType.ONLINE;
            default:
                return CourseType.LECTURE;
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void writeVarint(ByteArrayOutputStream out, int value) {
        int remaining = Math.max(0, value);
        while (remaining >= 0x80) {
            out.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.write(remaining);
    }

    private static int readVarint(byte[] data, int[] cursor) {
        int result = 0;
        int shift = 0;
        while (cursor[0] < data.length && shift < 32) {
            int value = data[cursor[0]++] & 0xFF;
            result |= (value & 0x7F) << shift;
            if ((value & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        return result;
    }

    private static void writeString(ByteArrayOutputStream out, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        writeVarint(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private static String readString(byte[] data, int[] cursor) {
        int length = readVarint(data, cursor);
        if (length <= 0 || cursor[0] + length > data.length) {
            return "";
        }
        String value = new String(data, cursor[0], length, StandardCharsets.UTF_8);
        cursor[0] += length;
        return value;
    }

    /** 颜色按 RGB 三字节存，比 "#4FA4F3" 少 4 字节；无法解析时存 0。 */
    private static void writeColor(ByteArrayOutputStream out, String color) {
        int rgb = parseRgb(color);
        if (rgb < 0) {
            writeVarint(out, 0);
            return;
        }
        writeVarint(out, 1);
        out.write((rgb >> 16) & 0xFF);
        out.write((rgb >> 8) & 0xFF);
        out.write(rgb & 0xFF);
    }

    private static String readColor(byte[] data, int[] cursor) {
        if (readVarint(data, cursor) != 1) {
            return "";
        }
        if (cursor[0] + 3 > data.length) {
            return "";
        }
        int red = data[cursor[0]++] & 0xFF;
        int green = data[cursor[0]++] & 0xFF;
        int blue = data[cursor[0]++] & 0xFF;
        return String.format(java.util.Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }

    /** 支持 #RGB / #RRGGBB / #AARRGGBB；解析失败返回 -1。 */
    private static int parseRgb(String color) {
        if (color == null) {
            return -1;
        }
        String value = color.trim();
        if (!value.startsWith("#")) {
            return -1;
        }
        String hex = value.substring(1);
        try {
            if (hex.length() == 6) {
                return Integer.parseInt(hex, 16);
            }
            if (hex.length() == 8) {
                return Integer.parseInt(hex.substring(2), 16);
            }
        } catch (NumberFormatException ignored) {
            return -1;
        }
        return -1;
    }

    private static List<Course> decodeCompact(String text) {
        List<Course> courses = new ArrayList<>();
        if (text == null || text.length() == 0) {
            return courses;
        }
        String[] rows = text.split("\n");
        for (String row : rows) {
            String[] parts = row.split("\t", -1);
            if (parts.length < 7) {
                continue;
            }
            int day = parseInt(parts[0], -1);
            int start = parseInt(parts[1], 1);
            int end = parseInt(parts[2], start);
            String name = unescape(parts[6]).trim();
            String credit = parts.length > 7 ? unescape(parts[7]) : "";
            String color = parts.length > 8 ? unescape(parts[8]) : "";
            CourseType courseType = parts.length > 9
                    ? CourseType.fromStorage(unescape(parts[9]))
                    : CourseType.LECTURE;
            int startMinute = parts.length > 11 ? parseInt(parts[11], -1) : -1;
            int endMinute = parts.length > 12 ? parseInt(parts[12], -1) : -1;
            CourseTimeMode timeMode = CourseTimeMode.fromStorage(
                    parts.length > 10 ? unescape(parts[10]) : "",
                    day, start, end, startMinute, endMinute);
            boolean bannerOnly = courseType.supportsBannerOnly() && timeMode == CourseTimeMode.NONE;
            boolean scheduled = timeMode == CourseTimeMode.CLOCK
                    ? day >= 0 && day <= 6 && startMinute >= 0 && startMinute < endMinute
                    : day >= 0 && day <= 6 && start >= 1 && end >= start;
            if (name.length() == 0
                    || (!bannerOnly && !scheduled)) {
                continue;
            }
            courses.add(new Course(day, start, end, name,
                    unescape(parts[3]), unescape(parts[4]), unescape(parts[5]), "",
                    credit, color, courseType, "", "", timeMode, startMinute, endMinute));
        }
        return courses;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescape(String text) {
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (escaping) {
                if (value == 't') {
                    builder.append('\t');
                } else if (value == 'n') {
                    builder.append('\n');
                } else {
                    builder.append(value);
                }
                escaping = false;
            } else if (value == '\\') {
                escaping = true;
            } else {
                builder.append(value);
            }
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
    }

    private static byte[] deflate(byte[] source) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(source);
        deflater.finish();
        byte[] buffer = new byte[Math.max(128, source.length)];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            output.write(buffer, 0, count);
        }
        deflater.end();
        return output.toByteArray();
    }

    private static byte[] inflate(byte[] source) throws IllegalArgumentException {
        Inflater inflater = new Inflater();
        inflater.setInput(source);
        byte[] buffer = new byte[512];
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalArgumentException("分享链接数据无法解压");
        } finally {
            inflater.end();
        }
    }
}
