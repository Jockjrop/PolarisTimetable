package com.polaris.timetable.widget;

import com.polaris.timetable.Course;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 整周课表小组件的网格数据：按「节次行 × 天列」铺排课程，与应用内课程表
 * 同款布局语义。跨节课程仍由每个节次行提供一段，但段位置会标记为
 * 首段/中段/末段，远程视图可将它们无缝拼成一个连续课程块，避免视觉上被拆成多节课。
 * 时间列取该节次的上课时间；隐藏天列（超出 5/6/7 选择）不填充内容。
 * 视觉文案由 {@link #layOutCourseText} 在整块连续课程格上从上到下铺排：
 * 课名、老师、地点依次占用各节次段，连堂课下方原本空白的色块也参与显示。
 */
final class ScheduleWidgetWeekData {
    private ScheduleWidgetWeekData() {
    }

    /** 周一基星期名（下标=day 0..6），表头、行无障碍描述与标题共用。 */
    static final String[] DAY_NAMES = {
            "周一", "周二", "周三", "周四", "周五", "周六", "周日"
    };

    /** 单字星期名（下标=day 0..6），整周表头「单字星期 + 日期号」两行样式共用。 */
    static final String[] DAY_CHARS = {"一", "二", "三", "四", "五", "六", "日"};

    /** 单个课程格：color 为格子底色，0 表示空格（隐藏底图）。 */
    static final class Cell {
        static final Cell EMPTY = new Cell("", "", 0, "", "", false, false);
        /** 首段课程名（续段为空），保留给既有调用与测试；渲染请用 displayText。 */
        final String text;
        /** 完整课程名；续段视觉文字留空时仍供无障碍描述使用。 */
        final String courseName;
        final int color;
        /** 上课地点（取课程级地点）；空格与无地点课程为空串。 */
        final String location;
        /** 任课教师（取课程级教师）；空格与无教师课程为空串。 */
        final String teacher;
        /** 是否为该课程的起始节次（决定此格是否展示地点行）。 */
        final boolean startOfCourse;
        /** 是否为该课程的末节（决定底部圆角）。 */
        final boolean endOfCourse;
        /**
         * 本段实际显示的文案：由 {@link #layOutCourseText} 按整块课程格可用空间
         * 统一分配后写入，非 final 是因为需要跨节次行回填。
         */
        String displayText;

        Cell(String text, String courseName, int color, String location, String teacher,
             boolean startOfCourse, boolean endOfCourse) {
            this.text = text;
            this.courseName = courseName;
            this.color = color;
            this.location = location;
            this.teacher = teacher == null ? "" : teacher;
            this.startOfCourse = startOfCourse;
            this.endOfCourse = endOfCourse;
            this.displayText = text;
        }
    }

    /** 单个节次行：节号、上课时间与 7 个天列格子（下标=周一基 day 0..6）。 */
    static final class Row {
        final int section;
        final String sectionTime;
        final Cell[] cells = new Cell[7];

        Row(int section, String sectionTime) {
            this.section = section;
            this.sectionTime = sectionTime;
        }
    }

    /**
     * 构建当前周的课程网格。周不在学期范围内或整周无课时返回空列表，
     * 由小组件空态文案兜底。
     */
    static List<Row> buildRows(
            List<Course> source, ScheduleRepository.Config config, Calendar now, int dayCount) {
        return buildRows(source, config, now, dayCount, null);
    }

    /**
     * 带学业日历的整周网格：放假列留空，补班列显示补的那一天的课。
     */
    static List<Row> buildRows(
            List<Course> source, ScheduleRepository.Config config, Calendar now, int dayCount,
            com.polaris.timetable.time.AcademicCalendar calendar) {
        if (source == null || config == null || now == null
                || dayCount < 5 || dayCount > 7) {
            return Collections.emptyList();
        }
        int week = CourseTimeResolver.weekForDate(config.firstWeekDay, now);
        if (week < 1 || week > Math.max(1, config.semesterWeeks)) {
            return Collections.emptyList();
        }
        CourseTimeResolver.Settings settings = settings(config);
        Map<String, Integer> colors = ScheduleWidgetData.buildCourseColors(source);
        int sectionCount = Math.max(1, Math.min(20, config.sectionCount));
        Calendar[] dates = weekDates(now);

        List<Row> rows = new ArrayList<>();
        boolean anyCourse = false;
        for (int section = 1; section <= sectionCount; section++) {
            CourseTimeResolver.TimeRange range =
                    CourseTimeResolver.sectionTimeRange(settings, section);
            Row row = new Row(section, range == null ? "" : hhmm(range.startMinutes));
            for (int day = 0; day < 7; day++) {
                if (day >= dayCount) {
                    row.cells[day] = Cell.EMPTY;
                    continue;
                }
                int effectiveDay = day;
                if (calendar != null) {
                    effectiveDay = calendar.effectiveWeekday(
                            com.polaris.timetable.time.AcademicCalendar.dateKey(dates[day]), day);
                    if (effectiveDay
                            == com.polaris.timetable.time.AcademicCalendar.WEEKDAY_UNSPECIFIED) {
                        row.cells[day] = Cell.EMPTY;
                        continue;
                    }
                }
                Course picked = pickCourseAt(source, settings, week, effectiveDay, section);
                if (picked == null) {
                    row.cells[day] = Cell.EMPTY;
                } else {
                    anyCourse = true;
                    boolean startOfCourse = picked.startSection == section;
                    boolean endOfCourse = picked.endSection == section;
                    String courseName = safeName(picked);
                    row.cells[day] = new Cell(
                            startOfCourse ? courseName : "", courseName,
                            ScheduleWidgetData.courseColor(picked, colors),
                            startOfCourse ? safeLocation(picked) : "",
                            startOfCourse ? safeTeacher(picked) : "",
                            startOfCourse, endOfCourse);
                }
            }
            rows.add(row);
        }
        if (!anyCourse) {
            return Collections.emptyList();
        }
        layOutCourseText(rows, dayCount);
        return rows;
    }

    /**
     * 把「课名 → 老师 → @地点」按段容量铺到整块连续课程格上。
     * 远程列表的每一行是独立 RemoteViews，文字无法真正跨行绘制；改为让每个
     * 节次段各自承载一段文字，拼接后的圆角块在视觉上就是自上而下的完整信息。
     */
    private static void layOutCourseText(List<Row> rows, int dayCount) {
        // 每段 3 行 × 每行可容纳字符数，扣掉信息行之间的换行损耗；
        // 列数越多格子越窄，预算越保守，宁可多占一段也不让文字被省略号截断。
        int charsPerLine = dayCount <= 5 ? 4 : 3;
        int capacity = charsPerLine * 3 - 2;
        for (int day = 0; day < dayCount; day++) {
            int index = 0;
            while (index < rows.size()) {
                Cell first = rows.get(index).cells[day];
                if (first.color == 0 || !first.startOfCourse) {
                    index++;
                    continue;
                }
                List<Cell> block = new ArrayList<>();
                while (index < rows.size()) {
                    Cell cell = rows.get(index).cells[day];
                    if (cell.color == 0 || (!block.isEmpty() && cell.startOfCourse)) {
                        break;
                    }
                    block.add(cell);
                    boolean last = cell.endOfCourse;
                    index++;
                    if (last) {
                        break;
                    }
                }
                distributeText(block, first.courseName, first.teacher,
                        first.location, capacity);
            }
        }
    }

    /**
     * 按段预算切分信息行：整行优先，教师名与地点不会被拆成孤字；
     * 只有单行本身超长（长课名）才硬切到下一段续写，段数不足时把余下文字
     * 并入最后一段（由 TextView 的 ellipsize 兜底）。
     */
    private static void distributeText(List<Cell> block, String name, String teacher,
                                       String location, int capacity) {
        List<String> lines = new ArrayList<>();
        lines.add(name);
        if (teacher != null && teacher.length() > 0) {
            lines.add(teacher);
        }
        if (location != null && location.length() > 0) {
            lines.add("@" + location);
        }

        List<StringBuilder> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String remaining = line;
            boolean firstSlice = true;
            while (remaining.length() > 0) {
                int separator = current.length() == 0 ? 0 : 1;
                int room = capacity - current.length() - separator;
                if (room >= remaining.length()) {
                    if (separator > 0) {
                        current.append('\n');
                    }
                    current.append(remaining);
                    remaining = "";
                    break;
                }
                // 一整行放不进当前段时另起一段，避免把教师名/地点拆开。
                if (firstSlice && current.length() > 0) {
                    segments.add(current);
                    current = new StringBuilder();
                    continue;
                }
                if (room <= 0) {
                    segments.add(current);
                    current = new StringBuilder();
                    continue;
                }
                int take = Math.min(room, remaining.length());
                if (separator > 0) {
                    current.append('\n');
                }
                current.append(remaining, 0, take);
                remaining = remaining.substring(take);
                firstSlice = false;
            }
        }
        if (current.length() > 0) {
            segments.add(current);
        }

        for (int i = 0; i < block.size(); i++) {
            if (i >= segments.size()) {
                block.get(i).displayText = "";
                continue;
            }
            StringBuilder value = new StringBuilder(segments.get(i));
            // 段数不够时余下内容并入最后一段，至少课名与老师排在前面可见。
            if (i == block.size() - 1) {
                for (int j = i + 1; j < segments.size(); j++) {
                    if (value.length() > 0 && segments.get(j).length() > 0) {
                        value.append('\n');
                    }
                    value.append(segments.get(j));
                }
            }
            block.get(i).displayText = value.toString();
        }
    }

    /**
     * 计算周视图每次刷新时应停留的首行：优先当前正在上的课程，其次是今天下一门课，
     * 今天课程已结束时停在最后一门课；今天没有课程时才按当前时间轴定位。
     * 返回值是 ListView 的零基行号。
     */
    static int autoScrollPosition(
            List<Course> source, ScheduleRepository.Config config, Calendar now) {
        return autoScrollPosition(source, config, now, null);
    }

    static int autoScrollPosition(
            List<Course> source, ScheduleRepository.Config config, Calendar now,
            com.polaris.timetable.time.AcademicCalendar calendar) {
        if (source == null || config == null || now == null) {
            return 0;
        }
        int week = CourseTimeResolver.weekForDate(config.firstWeekDay, now);
        if (week < 1 || week > Math.max(1, config.semesterWeeks)) {
            return 0;
        }

        int sectionCount = Math.max(1, Math.min(20, config.sectionCount));
        int today = CourseTimeResolver.mondayBasedDay(now);
        int effectiveToday = calendar == null ? today
                : calendar.effectiveWeekday(
                        com.polaris.timetable.time.AcademicCalendar.dateKey(now), today);
        if (effectiveToday == com.polaris.timetable.time.AcademicCalendar.WEEKDAY_UNSPECIFIED) {
            return 0;
        }
        int nowMinutes = CourseTimeResolver.minutesOfDay(now);
        CourseTimeResolver.Settings settings = settings(config);
        Course current = null;
        CourseTimeResolver.TimeRange currentRange = null;
        Course next = null;
        CourseTimeResolver.TimeRange nextRange = null;
        Course last = null;
        CourseTimeResolver.TimeRange lastRange = null;

        for (Course course : source) {
            if (course == null || course.isBannerOnlyCourse()
                    || !course.hasScheduledTime() || course.day != effectiveToday
                    || !CourseTimeResolver.isActiveInWeek(course, week)) {
                continue;
            }
            CourseTimeResolver.TimeRange range = CourseTimeResolver.timeRange(course, settings);
            if (range == null) {
                continue;
            }
            if (range.startMinutes <= nowMinutes && nowMinutes < range.endMinutes
                    && (currentRange == null || range.endMinutes < currentRange.endMinutes)) {
                current = course;
                currentRange = range;
            }
            if (range.startMinutes > nowMinutes
                    && (nextRange == null || range.startMinutes < nextRange.startMinutes)) {
                next = course;
                nextRange = range;
            }
            if (lastRange == null || range.startMinutes > lastRange.startMinutes
                    || (range.startMinutes == lastRange.startMinutes
                    && range.endMinutes > lastRange.endMinutes)) {
                last = course;
                lastRange = range;
            }
        }

        Course target = current != null ? current : (next != null ? next : last);
        CourseTimeResolver.TimeRange targetRange = current != null
                ? currentRange : (next != null ? nextRange : lastRange);
        if (target != null && targetRange != null) {
            return targetRow(target, targetRange, settings, sectionCount);
        }
        return sectionRowAtMinute(settings, nowMinutes, sectionCount);
    }

    /** 选定某天某节次上的课程：同时段重叠时取开始最早的课程，与看板行为一致。 */
    private static Course pickCourseAt(
            List<Course> source, CourseTimeResolver.Settings settings,
            int week, int day, int section) {
        Course picked = null;
        int pickedStart = Integer.MAX_VALUE;
        for (Course course : source) {
            if (course == null || course.isBannerOnlyCourse() || !course.hasScheduledTime()) {
                continue;
            }
            if (course.day != day || !CourseTimeResolver.isActiveInWeek(course, week)) {
                continue;
            }
            if (course.startSection > section || course.endSection < section) {
                continue;
            }
            CourseTimeResolver.TimeRange range = CourseTimeResolver.timeRange(course, settings);
            int start = range == null ? course.startSection : range.startMinutes;
            if (start < pickedStart) {
                picked = course;
                pickedStart = start;
            }
        }
        return picked;
    }

    /**
     * 当前周 7 列的日期（下标=day 0..6，以周一为列 0）。表头日期号与月份槽共用，
     * 与网格数据（buildRows 以同一 now 计算）保持同一周。
     */
    static Calendar[] weekDates(Calendar now) {
        Calendar monday = (Calendar) now.clone();
        monday.add(Calendar.DATE, -CourseTimeResolver.mondayBasedDay(now));
        Calendar[] dates = new Calendar[7];
        for (int day = 0; day < 7; day++) {
            dates[day] = (Calendar) monday.clone();
            dates[day].add(Calendar.DATE, day);
        }
        return dates;
    }

    private static CourseTimeResolver.Settings settings(ScheduleRepository.Config config) {
        return new CourseTimeResolver.Settings(
                config.firstClassStartTime,
                config.classDurationMinutes,
                config.classBreakMinutes,
                config.classBigBreakMinutes,
                config.afternoonStartTime,
                config.lateAfternoonStartTime,
                config.classTimeConfig);
    }

    private static String hhmm(int minutesOfDay) {
        int hour = minutesOfDay / 60;
        int minute = minutesOfDay % 60;
        return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
    }

    private static int targetRow(Course course, CourseTimeResolver.TimeRange range,
                                 CourseTimeResolver.Settings settings, int sectionCount) {
        int section = course.hasSectionTime()
                ? course.startSection : sectionAtMinute(settings, range.startMinutes, sectionCount);
        return clamp(section - 1, 0, sectionCount - 1);
    }

    private static int sectionRowAtMinute(CourseTimeResolver.Settings settings,
                                           int minute, int sectionCount) {
        return sectionAtMinute(settings, minute, sectionCount) - 1;
    }

    private static int sectionAtMinute(CourseTimeResolver.Settings settings,
                                       int minute, int sectionCount) {
        int closest = 1;
        for (int section = 1; section <= sectionCount; section++) {
            CourseTimeResolver.TimeRange range =
                    CourseTimeResolver.sectionTimeRange(settings, section);
            if (range == null) {
                continue;
            }
            if (minute < range.startMinutes) {
                return section;
            }
            if (minute < range.endMinutes) {
                return section;
            }
            closest = section;
        }
        return closest;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String safeName(Course course) {
        String name = course.name == null ? "" : course.name.trim();
        return name.length() == 0 ? "未命名课程" : name;
    }

    private static String safeLocation(Course course) {
        return course.location == null ? "" : course.location.trim();
    }

    private static String safeTeacher(Course course) {
        return course.teacher == null ? "" : course.teacher.trim();
    }
}
