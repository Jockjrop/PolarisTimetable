package com.polaris.timetable.sharing;

import android.net.Uri;
import android.util.Base64;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
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

    private ScheduleShareCodec() {
    }

    public static String encodeLink(List<Course> courses) throws JSONException {
        StringBuilder builder = new StringBuilder();
        if (courses != null) {
            for (Course course : courses) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(course.day).append('\t')
                        .append(course.startSection).append('\t')
                        .append(course.endSection).append('\t')
                        .append(escape(safeText(course.weeks))).append('\t')
                        .append(escape(safeText(course.location))).append('\t')
                        .append(escape(safeText(course.teacher))).append('\t')
                        .append(escape(safeText(course.name))).append('\t')
                        .append(escape(safeText(course.credit))).append('\t')
                        .append(escape(safeText(course.color))).append('\t')
                        .append(course.courseType.name()).append('\t')
                        .append(course.timeMode.name()).append('\t')
                        .append(course.startMinuteOfDay).append('\t')
                        .append(course.endMinuteOfDay);
            }
        }
        String payload = "z" + Base64.encodeToString(deflate(builder.toString().getBytes(StandardCharsets.UTF_8)),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return new Uri.Builder()
                .scheme(SCHEME)
                .authority(SHORT_HOST)
                .appendQueryParameter(QUERY_DATA, payload)
                .build()
                .toString();
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
