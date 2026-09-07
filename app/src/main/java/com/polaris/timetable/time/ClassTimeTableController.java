package com.polaris.timetable.time;

import com.polaris.timetable.parser.SchoolParserModel;
import com.polaris.timetable.state.ScheduleViewState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 课程时间表编辑逻辑控制器：承载「设置 → 课程时间表」编辑器的行推导、
 * 校验与回写。纯逻辑部分（时间文本解析、行推导、行校验）全部为静态、
 * 无 Android 依赖的方法，便于单元测试；Activity 侧仅保留 Toast / 字符串等
 * UI 动作。
 *
 * <p>抽取目标：阶段 2-3。将 MainActivity 中散落的课程时间表纯逻辑迁出，
 * 行为零变化。
 */
public final class ClassTimeTableController {

    private static final int DEFAULT_START_MINUTES = 8 * 60;
    private static final int DEFAULT_DURATION_MINUTES = 50;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private final ScheduleViewState scheduleViewState;

    public ClassTimeTableController(ScheduleViewState scheduleViewState) {
        this.scheduleViewState = scheduleViewState;
    }

    /** 读取当前配置的课程时间表行，供编辑器初始化与摘要展示。 */
    public List<int[]> loadClassTimeRows() {
        return loadRows(scheduleViewState.classTimeConfig, settings(),
                scheduleViewState.courseSectionCount);
    }

    /** 将编辑后的行写回 {@link ScheduleViewState}（配置文本 + 派生字段）。 */
    public void applyClassTimeRows(List<int[]> rows) {
        applyRows(rows, scheduleViewState);
    }

    private CourseTimeResolver.Settings settings() {
        return new CourseTimeResolver.Settings(
                scheduleViewState.firstClassStartTime,
                scheduleViewState.classDurationMinutes,
                scheduleViewState.classBreakMinutes,
                scheduleViewState.classBigBreakMinutes,
                scheduleViewState.afternoonStartTime,
                scheduleViewState.lateAfternoonStartTime,
                scheduleViewState.classTimeConfig);
    }

    // ===== 纯静态逻辑（无 Android 依赖，可单测） =====

    /** 校验失败类别：空列表或「结束早于开始」；成功时附带重叠告警标记。 */
    public enum ValidationError { NONE, EMPTY, END_BEFORE_START }

    /** 行校验结果：是否通过、失败类别、失败行号（1 起）、是否出现时间重叠。 */
    public static final class Validation {
        public final boolean valid;
        public final ValidationError error;
        public final int errorRow;
        public final boolean overlap;

        Validation(boolean valid, ValidationError error, int errorRow, boolean overlap) {
            this.valid = valid;
            this.error = error;
            this.errorRow = errorRow;
            this.overlap = overlap;
        }
    }

    /** 由课程时间配置与节次时间设置推导课程时间表行（与 MainActivity 原逻辑一致）。 */
    public static List<int[]> loadRows(String classTimeConfig,
                                       CourseTimeResolver.Settings settings,
                                       int sectionCount) {
        List<int[]> rows = new ArrayList<>();
        Map<Integer, int[]> anchors = CourseTimeResolver.parseSectionAnchors(classTimeConfig);
        int count = Math.max(1, Math.min(20, sectionCount));
        for (int section = 1; section <= count; section++) {
            int[] anchored = anchors.get(section);
            if (anchored != null) {
                rows.add(new int[]{anchored[0], anchored[1]});
                continue;
            }
            CourseTimeResolver.TimeRange range =
                    CourseTimeResolver.sectionTimeRange(settings, section);
            if (range == null) {
                continue;
            }
            rows.add(new int[]{range.startMinutes, range.endMinutes});
        }
        if (rows.isEmpty()) {
            rows.add(new int[]{DEFAULT_START_MINUTES,
                    DEFAULT_START_MINUTES + DEFAULT_DURATION_MINUTES});
        }
        return rows;
    }

    /** 从教务模型默认配置推导课程时间表行（「填充示例」用）。 */
    public static List<int[]> rowsFromSchoolModel(SchoolParserModel model) {
        List<int[]> rows = new ArrayList<>();
        if (model != null) {
            Map<Integer, int[]> anchors =
                    CourseTimeResolver.parseSectionAnchors(model.defaultClassTimeConfig);
            int count = model.defaultSectionCount();
            for (int section = 1; section <= count; section++) {
                int[] anchored = anchors.get(section);
                rows.add(anchored == null
                        ? new int[]{DEFAULT_START_MINUTES + (section - 1) * 60,
                                DEFAULT_START_MINUTES + (section - 1) * 60
                                        + DEFAULT_DURATION_MINUTES}
                        : new int[]{anchored[0], anchored[1]});
            }
        }
        if (rows.isEmpty()) {
            rows.add(new int[]{DEFAULT_START_MINUTES,
                    DEFAULT_START_MINUTES + DEFAULT_DURATION_MINUTES});
        }
        return rows;
    }

    /** 在现有行之后追加一行：以上一行结束时间顺延。 */
    public static int[] nextClassTimeRow(List<int[]> rows) {
        int[] last = rows.get(rows.size() - 1);
        int duration = Math.max(20, last[1] - last[0]);
        int start = last[1] + 10;
        return new int[]{start, start + duration};
    }

    /** 纯校验：不弹 Toast，由调用方根据结果决定 UI 提示。 */
    public static Validation validate(List<int[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return new Validation(false, ValidationError.EMPTY, 0, false);
        }
        boolean overlap = false;
        for (int i = 0; i < rows.size(); i++) {
            int[] row = rows.get(i);
            if (row[1] <= row[0]) {
                return new Validation(false, ValidationError.END_BEFORE_START, i + 1, false);
            }
            if (i > 0 && row[0] < rows.get(i - 1)[1]) {
                overlap = true;
            }
        }
        return new Validation(true, ValidationError.NONE, 0, overlap);
    }

    /** 将编辑后的行写回状态：配置文本 + 首节开始 + 时长 + 节次数。 */
    public static void applyRows(List<int[]> rows, ScheduleViewState state) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                text.append("\n");
            }
            text.append(i + 1).append(" ")
                    .append(timeTextMinute(rows.get(i)[0])).append("-")
                    .append(timeTextMinute(rows.get(i)[1]));
        }
        state.classTimeConfig = text.toString();
        state.firstClassStartTime = timeTextMinute(rows.get(0)[0]);
        state.classDurationMinutes = Math.max(20, Math.min(120, rows.get(0)[1] - rows.get(0)[0]));
        state.courseSectionCount = rows.size();
    }

    /** 分钟数 → "HH:mm"（夹紧到 0..23:59）。 */
    public static String timeTextMinute(int minutes) {
        int bounded = Math.max(0, Math.min(MINUTES_PER_DAY - 1, minutes));
        return twoDigits(bounded / 60) + ":" + twoDigits(bounded % 60);
    }

    /** "HH:mm" → 当天分钟数。 */
    public static int minutesFromTimeText(String value) {
        int[] time = timeFromText(value);
        return time[0] * 60 + time[1];
    }

    /** 配置文本是否含有效节次时间锚点。 */
    public static boolean hasClassTimeTable(String value) {
        return value != null && !CourseTimeResolver.parseSectionAnchors(value).isEmpty();
    }

    private static int[] timeFromText(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})")
                .matcher(value == null ? "" : value);
        if (matcher.find()) {
            return new int[]{
                    Math.max(0, Math.min(23, Integer.parseInt(matcher.group(1)))),
                    Math.max(0, Math.min(59, Integer.parseInt(matcher.group(2))))
            };
        }
        return new int[]{8, 0};
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
