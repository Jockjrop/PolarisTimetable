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
 * 仅首段携带课名和地点，续段留空，但保留完整无障碍语义。
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

    /** 单个课程格：text 为课程名；color 为格子底色，0 表示空格（隐藏底图）。 */
    static final class Cell {
        static final Cell EMPTY = new Cell("", "", 0, "", false, false);
        final String text;
        /** 完整课程名；续段视觉文字留空时仍供无障碍描述使用。 */
        final String courseName;
        final int color;
        /** 上课地点（取课程级地点）；空格与无地点课程为空串。 */
        final String location;
        /** 是否为该课程的起始节次（决定此格是否展示地点行）。 */
        final boolean startOfCourse;
        /** 是否为该课程的末节（决定底部圆角）。 */
        final boolean endOfCourse;

        Cell(String text, String courseName, int color, String location,
             boolean startOfCourse, boolean endOfCourse) {
            this.text = text;
            this.courseName = courseName;
            this.color = color;
            this.location = location;
            this.startOfCourse = startOfCourse;
            this.endOfCourse = endOfCourse;
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
                Course picked = pickCourseAt(source, settings, week, day, section);
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
                            startOfCourse, endOfCourse);
                }
            }
            rows.add(row);
        }
        return anyCourse ? rows : Collections.emptyList();
    }

    /**
     * 计算周视图每次刷新时应停留的首行：优先当前正在上的课程，其次是今天下一门课，
     * 今天课程已结束时停在最后一门课；今天没有课程时才按当前时间轴定位。
     * 返回值是 ListView 的零基行号。
     */
    static int autoScrollPosition(
            List<Course> source, ScheduleRepository.Config config, Calendar now) {
        if (source == null || config == null || now == null) {
            return 0;
        }
        int week = CourseTimeResolver.weekForDate(config.firstWeekDay, now);
        if (week < 1 || week > Math.max(1, config.semesterWeeks)) {
            return 0;
        }

        int sectionCount = Math.max(1, Math.min(20, config.sectionCount));
        int today = CourseTimeResolver.mondayBasedDay(now);
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
                    || !course.hasScheduledTime() || course.day != today
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
}
