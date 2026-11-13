package com.polaris.timetable.time;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学业日历：法定节假日与调休补班。
 *
 * <p>课表本身只认「第几周 + 星期几」，不知道 10/1 是假期、10/10 是补班日。
 * 这层负责把「真实日期」翻译成「这天该按星期几上课」：
 * <ul>
 *   <li>{@link #KIND_HOLIDAY}：放假，当天不排课；</li>
 *   <li>{@link #KIND_MAKEUP}：补班，按学校指定的星期上课（未指定时退回真实星期，不臆测）；</li>
 *   <li>{@link #KIND_NORMAL}：用户显式取消某天内置规则。</li>
 * </ul>
 *
 * <p>内置数据来自 {@code res/raw/holidays.json}（国办发明电〔2025〕7号），
 * 用户覆盖表以同样的日期粒度覆盖内置值。解析与判定为纯逻辑，不依赖 Android，
 * 便于单元测试。
 */
public final class AcademicCalendar {

    public static final int KIND_NORMAL = 0;
    public static final int KIND_HOLIDAY = 1;
    public static final int KIND_MAKEUP = 2;

    /** 补班日未指定补哪一天时使用：按真实星期显示，并提示用户去设置。 */
    public static final int WEEKDAY_UNSPECIFIED = -1;

    public static final class DayRule {
        public final String date;
        public final int kind;
        public final int makeupWeekday;
        public final String label;
        /** 数值排序键：{@code yyyyMMdd}，避免字符串序把 10/10 排到 2/14 前面。 */
        public final int sortKey;

        DayRule(String date, int kind, int makeupWeekday, String label) {
            this.date = date;
            this.kind = kind;
            this.makeupWeekday = makeupWeekday;
            this.label = label == null ? "" : label;
            this.sortKey = sortKeyOf(date);
        }

        public DayRule withMakeupWeekday(int weekday) {
            return new DayRule(date, KIND_MAKEUP, weekday, label);
        }
    }

    private static final AcademicCalendar EMPTY = new AcademicCalendar(new LinkedHashMap<>());

    private final Map<String, DayRule> rules;

    private AcademicCalendar(Map<String, DayRule> rules) {
        this.rules = rules;
    }

    public static AcademicCalendar empty() {
        return EMPTY;
    }

    /** 读取内置表并与用户覆盖表合并；任一侧损坏都退化为可用的最小集合。 */
    public static AcademicCalendar load(Context context, String overridesJson) {
        String builtIn = "";
        if (context != null) {
            builtIn = readRaw(context, com.polaris.timetable.R.raw.holidays);
        }
        return parse(builtIn, overridesJson);
    }

    /** 纯解析入口：先铺内置规则，再用覆盖规则逐日替换。 */
    public static AcademicCalendar parse(String builtInJson, String overridesJson) {
        Map<String, DayRule> merged = new LinkedHashMap<>();
        readInto(merged, builtInJson);
        readInto(merged, overridesJson);
        if (merged.isEmpty()) {
            return EMPTY;
        }
        return new AcademicCalendar(merged);
    }

    private static void readInto(Map<String, DayRule> target, String json) {
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        try {
            Object root = new org.json.JSONTokener(json).nextValue();
            JSONArray days;
            if (root instanceof JSONObject) {
                days = ((JSONObject) root).optJSONArray("days");
            } else if (root instanceof JSONArray) {
                days = (JSONArray) root;
            } else {
                return;
            }
            if (days == null) {
                return;
            }
            for (int i = 0; i < days.length(); i++) {
                JSONObject item = days.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String date = normalizeDate(item.optString("date", ""));
                if (date.isEmpty()) {
                    continue;
                }
                int kind = parseKind(item.optString("kind", ""));
                int weekday = item.has("weekday")
                        ? clampWeekday(item.optInt("weekday", WEEKDAY_UNSPECIFIED))
                        : WEEKDAY_UNSPECIFIED;
                String label = item.optString("label", "");
                // 覆盖项通常只写 date/kind/weekday，标签沿用内置项，
                // 否则「2026/10/10 国庆补班」会在用户改一次后变成光秃秃的日期。
                DayRule previous = target.get(date);
                if (label.isEmpty() && previous != null) {
                    label = previous.label;
                }
                target.put(date, new DayRule(date, kind, weekday, label));
            }
        } catch (Exception ignored) {
            // 数据损坏时保留已解析部分，不阻断课表渲染。
        }
    }

    private static int parseKind(String value) {
        if ("holiday".equalsIgnoreCase(value)) {
            return KIND_HOLIDAY;
        }
        if ("makeup".equalsIgnoreCase(value)) {
            return KIND_MAKEUP;
        }
        return KIND_NORMAL;
    }

    private static int clampWeekday(int value) {
        return value < 0 || value > 6 ? WEEKDAY_UNSPECIFIED : value;
    }

    public DayRule ruleFor(String date) {
        return rules.get(normalizeDate(date));
    }

    public boolean isHoliday(String date) {
        DayRule rule = ruleFor(date);
        return rule != null && rule.kind == KIND_HOLIDAY;
    }

    public boolean isMakeup(String date) {
        DayRule rule = ruleFor(date);
        return rule != null && rule.kind == KIND_MAKEUP;
    }

    /**
     * 该日期实际应按星期几上课。
     *
     * @param actualWeekday 真实星期（0=周一 … 6=周日）
     * @return 0-6；返回 {@link #WEEKDAY_UNSPECIFIED} 表示当天放假、不排课
     */
    public int effectiveWeekday(String date, int actualWeekday) {
        DayRule rule = ruleFor(date);
        if (rule == null) {
            return actualWeekday;
        }
        if (rule.kind == KIND_HOLIDAY) {
            return WEEKDAY_UNSPECIFIED;
        }
        if (rule.kind == KIND_MAKEUP && rule.makeupWeekday != WEEKDAY_UNSPECIFIED) {
            return rule.makeupWeekday;
        }
        return actualWeekday;
    }

    /** 补班但用户还没指定补哪一天：UI 应提示去设置，而不是猜一个结果。 */
    public boolean needsMakeupWeekday(String date) {
        DayRule rule = ruleFor(date);
        return rule != null && rule.kind == KIND_MAKEUP
                && rule.makeupWeekday == WEEKDAY_UNSPECIFIED;
    }

    /** 规则内容签名：供看板渲染缓存判断是否需要重建。 */
    public int signature() {
        int hash = 1;
        for (Map.Entry<String, DayRule> entry : rules.entrySet()) {
            hash = 31 * hash + entry.getKey().hashCode();
            hash = 31 * hash + entry.getValue().kind;
            hash = 31 * hash + entry.getValue().makeupWeekday;
        }
        return hash;
    }

    /** 当前生效的全部规则，按日期正序（数值序，非字符串序）。 */
    public java.util.List<DayRule> allRules() {
        java.util.List<DayRule> list = new java.util.ArrayList<>(rules.values());
        java.util.Collections.sort(list, (first, second) ->
                Integer.compare(first.sortKey, second.sortKey));
        return list;
    }

    /** 把 {@code yyyy/M/d} 折算成可比较的整数；解析失败排到最后。 */
    private static int sortKeyOf(String date) {
        String[] parts = date == null ? new String[0] : date.split("/");
        if (parts.length != 3) {
            return Integer.MAX_VALUE;
        }
        try {
            int year = Integer.parseInt(parts[0].trim());
            int month = Integer.parseInt(parts[1].trim());
            int day = Integer.parseInt(parts[2].trim());
            return year * 10000 + month * 100 + day;
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * 在既有覆盖表上写入/替换一条规则，返回新的 JSON。
     *
     * @param existingJson 现有覆盖表（可为空）
     * @param date         目标日期 {@code yyyy/M/d}
     * @param kind         holiday / makeup / normal
     * @param weekday      补班目标星期，-1 表示未指定；非 makeup 时忽略
     */
    public static String withOverride(String existingJson, String date,
                                      String kind, int weekday) {
        String normalizedDate = normalizeDate(date);
        if (normalizedDate.isEmpty()) {
            return existingJson == null ? "" : existingJson;
        }
        JSONArray array = new JSONArray();
        try {
            if (existingJson != null && !existingJson.trim().isEmpty()) {
                Object root = new org.json.JSONTokener(existingJson).nextValue();
                if (root instanceof JSONArray) {
                    array = (JSONArray) root;
                }
            }
        } catch (Exception ignored) {
            array = new JSONArray();
        }
        JSONArray next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (normalizedDate.equals(normalizeDate(item.optString("date", "")))) {
                next.put(overrideItem(normalizedDate, kind, weekday));
                replaced = true;
            } else {
                next.put(item);
            }
        }
        if (!replaced) {
            next.put(overrideItem(normalizedDate, kind, weekday));
        }
        return next.toString();
    }

    /** 移除某条覆盖，回到内置安排。 */
    public static String withoutOverride(String existingJson, String date) {
        String normalizedDate = normalizeDate(date);
        if (normalizedDate.isEmpty() || existingJson == null || existingJson.trim().isEmpty()) {
            return existingJson == null ? "" : existingJson;
        }
        try {
            Object root = new org.json.JSONTokener(existingJson).nextValue();
            if (!(root instanceof JSONArray)) {
                return existingJson;
            }
            JSONArray array = (JSONArray) root;
            JSONArray next = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                if (!normalizedDate.equals(normalizeDate(item.optString("date", "")))) {
                    next.put(item);
                }
            }
            return next.toString();
        } catch (Exception ignored) {
            return existingJson;
        }
    }

    private static JSONObject overrideItem(String date, String kind, int weekday) {
        JSONObject item = new JSONObject();
        try {
            item.put("date", date);
            item.put("kind", kind == null ? "normal" : kind);
            if (weekday >= 0 && weekday <= 6) {
                item.put("weekday", weekday);
            }
        } catch (Exception ignored) {
            // JSONObject.put 仅在 key 为 null 时抛错，这里的 key 都是常量。
        }
        return item;
    }

    /** 统一为 {@code yyyy/M/d}（项目既有的首周日期格式）。 */
    public static String dateKey(int year, int month, int day) {
        return year + "/" + month + "/" + day;
    }

    public static String dateKey(Calendar calendar) {
        return dateKey(calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    /** 0=周一 … 6=周日，与 {@code WeekdayLabels} 的索引一致。 */
    public static int weekdayOf(Calendar calendar) {
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    private static String normalizeDate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = trimmed.replace('-', '/').split("/");
        if (parts.length != 3) {
            return "";
        }
        try {
            int year = Integer.parseInt(parts[0].trim());
            int month = Integer.parseInt(parts[1].trim());
            int day = Integer.parseInt(parts[2].trim());
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return "";
            }
            return dateKey(year, month, day);
        } catch (NumberFormatException exception) {
            return "";
        }
    }

    private static String readRaw(Context context, int resId) {
        try (InputStream stream = context.getResources().openRawResource(resId)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            return "";
        }
    }
}
