package com.polaris.timetable.time;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.WeekRule;
import com.polaris.timetable.parser.WeekRuleParser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Centralizes semester-week, active-week and section-time calculations used by
 * the schedule screen, widgets and future reminder scheduling.
 */
public final class CourseTimeResolver {
    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})");
    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s+((?:\\d{1,2})[:：](?:\\d{2}))\\s*-\\s*(?:\\d{1,2})[:：](?:\\d{2})");
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final WeekRuleParser WEEK_RULE_PARSER = new WeekRuleParser();
    private static final Settings DEFAULT_SETTINGS = new Settings(
            "08:00", 50, 10, 30, "14:30", "16:35", "");

    private CourseTimeResolver() {
    }

    public enum TodayStatus {
        ONGOING,
        NEXT,
        FINISHED,
        NO_COURSES,
        OUTSIDE_SEMESTER
    }

    public static final class Settings {
        public final String firstStartTime;
        public final int classMinutes;
        public final int breakMinutes;
        public final int bigBreakMinutes;
        public final String afternoonStartTime;
        public final String lateAfternoonStartTime;
        public final String anchorConfigText;

        public Settings(String firstStartTime, int classMinutes, int breakMinutes,
                        int bigBreakMinutes, String afternoonStartTime,
                        String lateAfternoonStartTime, String anchorConfigText) {
            this.firstStartTime = safe(firstStartTime);
            this.classMinutes = clamp(classMinutes, 1, 240);
            this.breakMinutes = clamp(breakMinutes, 0, 120);
            this.bigBreakMinutes = clamp(bigBreakMinutes, 0, 240);
            this.afternoonStartTime = safe(afternoonStartTime);
            this.lateAfternoonStartTime = safe(lateAfternoonStartTime);
            this.anchorConfigText = safe(anchorConfigText);
        }
    }

    public static final class TimeRange {
        public final int startMinutes;
        public final int endMinutes;

        TimeRange(int startMinutes, int endMinutes) {
            this.startMinutes = startMinutes;
            this.endMinutes = endMinutes;
        }

        public String displayText() {
            return timeText(startMinutes) + "–" + timeText(endMinutes);
        }
    }

    public static final class TodayOverview {
        public final TodayStatus status;
        public final Course course;
        public final int week;
        public final int day;
        public final int startMinutes;
        public final int endMinutes;
        public final int minutesToBoundary;
        public final int simultaneousCourseCount;

        TodayOverview(TodayStatus status, Course course, int week, int day,
                      int startMinutes, int endMinutes, int minutesToBoundary,
                      int simultaneousCourseCount) {
            this.status = status;
            this.course = course;
            this.week = week;
            this.day = day;
            this.startMinutes = startMinutes;
            this.endMinutes = endMinutes;
            this.minutesToBoundary = minutesToBoundary;
            this.simultaneousCourseCount = simultaneousCourseCount;
        }

        public boolean hasCourse() {
            return course != null;
        }

        public String timeText() {
            if (!hasCourse() || startMinutes < 0 || endMinutes < 0) {
                return "";
            }
            return CourseTimeResolver.timeText(startMinutes)
                    + "–" + CourseTimeResolver.timeText(endMinutes);
        }
    }

    public static TimeRange timeRange(Course course, Settings settings) {
        if (course == null || !course.hasScheduledTime()) {
            return null;
        }
        if (course.hasExactTime()) {
            return new TimeRange(course.startMinuteOfDay, course.endMinuteOfDay);
        }
        if (settings == null || !course.hasSectionTime()) {
            return null;
        }
        int count = Math.max(20, course.endSection);
        TimeRange[] ranges = buildRanges(settings, count, false);
        if (ranges == null || course.startSection >= ranges.length
                || course.endSection >= ranges.length) {
            return null;
        }
        return new TimeRange(ranges[course.startSection].startMinutes,
                ranges[course.endSection].endMinutes);
    }

    public static String format(Course course, Settings settings) {
        if (course == null) {
            return "";
        }
        if (course.isBannerOnlyCourse()) {
            return "本周实践";
        }
        TimeRange range = timeRange(course, settings);
        if (range == null) {
            return sectionFallback(course);
        }
        return range.displayText();
    }

    public static int endMinutes(Course course, Settings settings) {
        TimeRange range = timeRange(course, settings);
        return range == null ? -1 : range.endMinutes;
    }

    public static String[] sectionTimeLabels(Settings settings, int sectionCount) {
        int count = Math.max(1, sectionCount);
        TimeRange[] ranges = buildRanges(settings, count, true);
        String[] labels = new String[count];
        for (int section = 1; section <= count; section++) {
            TimeRange range = ranges[section];
            labels[section - 1] = timeText(range.startMinutes) + "\n" + timeText(range.endMinutes);
        }
        return labels;
    }

    public static TimeRange sectionTimeRange(Settings settings, int section) {
        if (section < 1) {
            return null;
        }
        TimeRange[] ranges = buildRanges(settings == null ? DEFAULT_SETTINGS : settings,
                section, true);
        return ranges[section];
    }

    public static Settings defaultSettings() {
        return DEFAULT_SETTINGS;
    }

    public static String formatMinuteOfDay(int minutes) {
        return timeText(minutes);
    }

    public static TodayOverview resolveToday(List<Course> source, Settings settings,
                                              long firstWeekStartMillis, int semesterWeeks,
                                              Calendar now) {
        Calendar current = now == null ? Calendar.getInstance() : (Calendar) now.clone();
        int week = weekForDate(firstWeekStartMillis, current);
        int day = mondayBasedDay(current);
        if (week < 1 || week > Math.max(1, semesterWeeks)) {
            return emptyOverview(TodayStatus.OUTSIDE_SEMESTER, week, day);
        }

        List<CourseWithTime> todayCourses = new ArrayList<>();
        if (source != null) {
            for (Course course : source) {
                if (course == null || !course.hasScheduledTime() || course.day != day
                        || !isActiveInWeek(course, week)) {
                    continue;
                }
                TimeRange range = timeRange(course, settings);
                if (range != null) {
                    todayCourses.add(new CourseWithTime(course, range));
                }
            }
        }
        if (todayCourses.isEmpty()) {
            return emptyOverview(TodayStatus.NO_COURSES, week, day);
        }
        Collections.sort(todayCourses, new Comparator<CourseWithTime>() {
            @Override
            public int compare(CourseWithTime first, CourseWithTime second) {
                int byStart = Integer.compare(first.range.startMinutes, second.range.startMinutes);
                if (byStart != 0) {
                    return byStart;
                }
                return Integer.compare(first.range.endMinutes, second.range.endMinutes);
            }
        });

        int nowMinutes = minutesOfDay(current);
        CourseWithTime ongoing = null;
        int simultaneousCount = 0;
        for (CourseWithTime item : todayCourses) {
            if (item.range.startMinutes <= nowMinutes && nowMinutes < item.range.endMinutes) {
                simultaneousCount++;
                if (ongoing == null || item.range.endMinutes < ongoing.range.endMinutes) {
                    ongoing = item;
                }
            }
        }
        if (ongoing != null) {
            return overview(TodayStatus.ONGOING, ongoing, week, day,
                    Math.max(0, ongoing.range.endMinutes - nowMinutes), simultaneousCount);
        }
        for (CourseWithTime item : todayCourses) {
            if (item.range.startMinutes > nowMinutes) {
                return overview(TodayStatus.NEXT, item, week, day,
                        item.range.startMinutes - nowMinutes, 1);
            }
        }
        return emptyOverview(TodayStatus.FINISHED, week, day);
    }

    public static boolean isActiveInWeek(Course course, int week) {
        return course != null && WEEK_RULE_PARSER.parse(course.weeks).containsWeek(week);
    }

    public static int inferSemesterWeeks(List<Course> source, int defaultWeeks, int maximumWeeks) {
        int upperBound = Math.max(1, maximumWeeks);
        int fallback = clamp(defaultWeeks, 1, upperBound);
        int lastReferencedWeek = 0;
        if (source != null) {
            for (Course course : source) {
                if (course == null) {
                    continue;
                }
                WeekRule rule = WEEK_RULE_PARSER.parse(course.weeks);
                lastReferencedWeek = Math.max(lastReferencedWeek, rule.lastReferencedWeek());
            }
        }
        return lastReferencedWeek > 0
                ? clamp(lastReferencedWeek, 1, upperBound)
                : fallback;
    }

    public static int weekForDate(String firstWeekDay, Calendar target) {
        Calendar first = calendarFromText(firstWeekDay,
                target == null ? TimeZone.getDefault() : target.getTimeZone());
        normalizeToMonday(first);
        return weekForCalendars(first, target == null ? Calendar.getInstance() : target);
    }

    public static int weekForDate(long firstWeekStartMillis, Calendar target) {
        Calendar current = target == null ? Calendar.getInstance() : target;
        Calendar first = Calendar.getInstance(current.getTimeZone());
        first.setTimeInMillis(firstWeekStartMillis);
        normalizeToMonday(first);
        return weekForCalendars(first, current);
    }

    public static int mondayBasedDay(Calendar date) {
        Calendar value = date == null ? Calendar.getInstance() : date;
        int day = value.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SUNDAY ? 6 : day - Calendar.MONDAY;
    }

    public static int minutesOfDay(Calendar date) {
        Calendar value = date == null ? Calendar.getInstance() : date;
        return value.get(Calendar.HOUR_OF_DAY) * 60 + value.get(Calendar.MINUTE);
    }

    public static String countdownText(int minutes) {
        int bounded = Math.max(0, minutes);
        if (bounded < 60) {
            return bounded + "分钟";
        }
        int hours = bounded / 60;
        int remainder = bounded % 60;
        return remainder == 0 ? hours + "小时" : hours + "小时" + remainder + "分钟";
    }

    private static TimeRange[] buildRanges(Settings settings, int sectionCount,
                                           boolean allowDefaultStart) {
        if (settings == null) {
            return allowDefaultStart
                    ? buildRanges(new Settings("08:00", 50, 10, 30,
                    "14:30", "16:35", ""), sectionCount, false)
                    : null;
        }
        Map<Integer, Integer> anchors = classTimeAnchors(settings.anchorConfigText);
        int start = minutesFromText(settings.firstStartTime);
        if (start < 0) {
            start = firstTimeFromText(settings.anchorConfigText);
        }
        if (start < 0) {
            if (!allowDefaultStart) {
                return null;
            }
            start = 8 * 60;
        }
        int afternoonStart = minutesFromText(settings.afternoonStartTime);
        int lateAfternoonStart = minutesFromText(settings.lateAfternoonStartTime);
        int count = Math.max(1, sectionCount);
        TimeRange[] ranges = new TimeRange[count + 1];
        for (int section = 1; section <= count; section++) {
            Integer anchoredStart = anchors.get(section);
            if (anchoredStart != null && section > 1) {
                start = anchoredStart;
            } else if (section == 5 && afternoonStart >= 0) {
                start = afternoonStart;
            } else if (section == 7 && lateAfternoonStart >= 0) {
                start = lateAfternoonStart;
            }
            int end = start + settings.classMinutes;
            ranges[section] = new TimeRange(start, end);
            start = end + (section % 2 == 0
                    ? settings.bigBreakMinutes : settings.breakMinutes);
        }
        return ranges;
    }

    private static Map<Integer, Integer> classTimeAnchors(String value) {
        Map<Integer, Integer> anchors = new LinkedHashMap<>();
        Matcher matcher = ANCHOR_PATTERN.matcher(safe(value));
        while (matcher.find()) {
            int section = Integer.parseInt(matcher.group(1));
            int start = minutesFromText(matcher.group(2));
            if (section > 0 && section <= 40 && start >= 0) {
                anchors.put(section, start);
            }
        }
        return anchors;
    }

    private static int firstTimeFromText(String value) {
        Matcher matcher = TIME_PATTERN.matcher(safe(value));
        if (!matcher.find()) {
            return -1;
        }
        return parsedMinutes(matcher.group(1), matcher.group(2));
    }

    private static int minutesFromText(String value) {
        Matcher matcher = TIME_PATTERN.matcher(safe(value));
        if (!matcher.find()) {
            return -1;
        }
        return parsedMinutes(matcher.group(1), matcher.group(2));
    }

    private static int parsedMinutes(String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText);
        int minute = Integer.parseInt(minuteText);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return -1;
        }
        return hour * 60 + minute;
    }

    private static String timeText(int minutes) {
        if (minutes == MINUTES_PER_DAY) {
            return "24:00";
        }
        int normalized = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        return twoDigits(normalized / 60) + ":" + twoDigits(normalized % 60);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static String sectionFallback(Course course) {
        if (course == null || !course.hasScheduledTime()) {
            return "时间待定";
        }
        if (course.hasExactTime()) {
            return timeText(course.startMinuteOfDay) + "–" + timeText(course.endMinuteOfDay);
        }
        return "第" + course.startSection + "–" + course.endSection + "节";
    }

    private static Calendar calendarFromText(String value, TimeZone timeZone) {
        Calendar date = Calendar.getInstance(timeZone);
        date.clear();
        String[] parts = safe(value).split("/");
        try {
            date.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                    Integer.parseInt(parts[2]), 0, 0, 0);
        } catch (Exception ignored) {
            date.set(2026, Calendar.MARCH, 3, 0, 0, 0);
        }
        return date;
    }

    private static void normalizeToMonday(Calendar date) {
        int dayOfWeek = date.get(Calendar.DAY_OF_WEEK);
        int mondayOffset = dayOfWeek == Calendar.SUNDAY ? -6 : Calendar.MONDAY - dayOfWeek;
        date.add(Calendar.DATE, mondayOffset);
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
    }

    private static int weekForCalendars(Calendar first, Calendar target) {
        long firstDay = utcDateMillis(first);
        long targetDay = utcDateMillis(target);
        long days = Math.floorDiv(targetDay - firstDay, 24L * 60L * 60L * 1000L);
        return 1 + (int) Math.floorDiv(days, 7L);
    }

    private static long utcDateMillis(Calendar source) {
        Calendar date = Calendar.getInstance(UTC);
        date.clear();
        date.set(source.get(Calendar.YEAR), source.get(Calendar.MONTH),
                source.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        return date.getTimeInMillis();
    }

    private static TodayOverview emptyOverview(TodayStatus status, int week, int day) {
        return new TodayOverview(status, null, week, day, -1, -1, 0, 0);
    }

    private static TodayOverview overview(TodayStatus status, CourseWithTime item,
                                          int week, int day, int minutesToBoundary,
                                          int simultaneousCourseCount) {
        return new TodayOverview(status, item.course, week, day,
                item.range.startMinutes, item.range.endMinutes,
                minutesToBoundary, simultaneousCourseCount);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class CourseWithTime {
        final Course course;
        final TimeRange range;

        CourseWithTime(Course course, TimeRange range) {
            this.course = course;
            this.range = range;
        }
    }
}
