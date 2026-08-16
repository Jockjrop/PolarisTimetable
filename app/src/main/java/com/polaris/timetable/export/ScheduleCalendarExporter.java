package com.polaris.timetable.export;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;
import com.polaris.timetable.time.CourseTimeResolver;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Exports the full-semester schedule to iCalendar (RFC 5545) and CSV text.
 *
 * <p>Pure Java: the returned strings can be written to files by the caller.
 * Meeting times are resolved from section anchors when the meeting uses the
 * section time mode, or from the exact clock times otherwise. Weekly recurrence
 * is expanded into one VEVENT per active week so that every week rule
 * (range / odd / even / explicit / all) maps to calendar events deterministically.</p>
 */
public final class ScheduleCalendarExporter {
    private static final String DEFAULT_FIRST_WEEK_DAY = "2026/3/3";
    private static final String ICAL_PRODID = "-//Polaris课程表//CN";
    private static final int ICAL_LINE_LIMIT = 75;
    private static final String[] DAY_NAMES = {
            "周一", "周二", "周三", "周四", "周五", "周六", "周日"
    };

    private ScheduleCalendarExporter() {
    }

    /** Everything the exporters need to resolve dates and times. */
    public static final class ExportContext {
        public final String scheduleName;
        public final String semesterName;
        public final String firstWeekDay;
        public final int semesterWeeks;
        public final CourseTimeResolver.Settings timeSettings;

        public ExportContext(String scheduleName, String semesterName,
                             String firstWeekDay, int semesterWeeks,
                             CourseTimeResolver.Settings timeSettings) {
            this.scheduleName = scheduleName == null ? "" : scheduleName;
            this.semesterName = semesterName == null ? "" : semesterName;
            this.firstWeekDay = firstWeekDay == null || firstWeekDay.length() == 0
                    ? DEFAULT_FIRST_WEEK_DAY : firstWeekDay;
            this.semesterWeeks = Math.max(1, semesterWeeks);
            this.timeSettings = timeSettings == null
                    ? CourseTimeResolver.defaultSettings() : timeSettings;
        }
    }

    /** Builds an iCalendar (RFC 5545) document with one VEVENT per active week. */
    public static String buildICal(ExportContext context, List<StructuredCourse> courses) {
        ExportContext safe = context == null
                ? new ExportContext("", "", null, 1, null) : context;
        Calendar firstMonday = firstWeekMonday(safe.firstWeekDay);
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:" + ICAL_PRODID);
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        if (safe.scheduleName.length() > 0) {
            lines.add("X-WR-CALNAME:" + safe.scheduleName);
        }
        lines.add("BEGIN:VTIMEZONE");
        lines.add("TZID:" + TimeZone.getDefault().getID());
        lines.add("END:VTIMEZONE");

        for (StructuredCourse course : safeCourseList(courses)) {
            for (CourseMeeting meeting : course.meetings) {
                if (meeting == null) {
                    continue;
                }
                List<Integer> activeWeeks = activeWeeks(meeting.weekRule, safe.semesterWeeks);
                if (activeWeeks.isEmpty()) {
                    continue;
                }
                TimeRange range = meetingRange(meeting, safe.timeSettings);
                for (int week : activeWeeks) {
                    List<String> event = buildEvent(safe, course, meeting, range, week, firstMonday);
                    if (!event.isEmpty()) {
                        lines.addAll(event);
                    }
                }
            }
        }

        List<String> folded = new ArrayList<>(lines.size());
        for (String line : lines) {
            folded.add(foldLine(line));
        }
        StringBuilder builder = new StringBuilder();
        for (String line : folded) {
            builder.append(line).append("\r\n");
        }
        builder.append("END:VCALENDAR\r\n");
        return builder.toString();
    }

    /** Builds a UTF-8 CSV document (with BOM for Excel compatibility). */
    public static String buildCsv(ExportContext context, List<StructuredCourse> courses) {
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("课程名称,教师,学分,课程类型,星期,开始节次,结束节次,"
                + "开始时间,结束时间,周次,地点\r\n");
        for (StructuredCourse course : safeCourseList(courses)) {
            for (CourseMeeting meeting : course.meetings) {
                if (meeting == null) {
                    continue;
                }
                TimeRange range = meetingRange(meeting,
                        context == null ? CourseTimeResolver.defaultSettings()
                                : context.timeSettings);
                String day = meeting.day >= 0 && meeting.day <= 6
                        ? DAY_NAMES[meeting.day] : "";
                String startSection = meeting.timeMode == CourseTimeMode.SECTION
                        ? String.valueOf(meeting.startSection) : "";
                String endSection = meeting.timeMode == CourseTimeMode.SECTION
                        ? String.valueOf(meeting.endSection) : "";
                String startTime = range == null ? "" : timeText(range.startMinutes);
                String endTime = range == null ? "" : timeText(range.endMinutes);
                String weeks = meeting.weekRule == null
                        ? "" : meeting.weekRule.displayText();
                builder.append(csvField(course.name)).append(',')
                        .append(csvField(meeting.teacher.length() > 0
                                ? meeting.teacher : course.teacher)).append(',')
                        .append(csvField(course.credit)).append(',')
                        .append(csvField(course.courseType.displayName)).append(',')
                        .append(csvField(day)).append(',')
                        .append(csvField(startSection)).append(',')
                        .append(csvField(endSection)).append(',')
                        .append(csvField(startTime)).append(',')
                        .append(csvField(endTime)).append(',')
                        .append(csvField(weeks)).append(',')
                        .append(csvField(meeting.location)).append("\r\n");
            }
        }
        return builder.toString();
    }

    /** Sanitizes a user-provided name so it is safe to use in a file name. */
    public static String safeFileName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.length() == 0) {
            return "课表";
        }
        return value.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private static List<StructuredCourse> safeCourseList(List<StructuredCourse> courses) {
        return courses == null
                ? java.util.Collections.<StructuredCourse>emptyList() : courses;
    }

    private static List<String> buildEvent(ExportContext context, StructuredCourse course,
                                           CourseMeeting meeting, TimeRange range,
                                           int week, Calendar firstMonday) {
        if (range == null && !isWeekUnknown(meeting.weekRule)) {
            return java.util.Collections.emptyList();
        }
        Calendar date = dateFor(firstMonday, week, meeting.day);
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VEVENT");
        lines.add("UID:" + eventUid(course, meeting, week));
        lines.add("DTSTAMP:" + utcStamp());
        if (range == null) {
            lines.add("DTSTART;VALUE=DATE:" + dateStamp(date));
            lines.add("DTEND;VALUE=DATE:" + dateStamp(nextDay(date)));
        } else {
            lines.add("DTSTART:" + dateTimeStamp(date, range.startMinutes));
            lines.add("DTEND:" + dateTimeStamp(date, range.endMinutes));
        }
        String summary = course.courseType == CourseType.PRACTICE
                ? "实践·" + course.name : course.name;
        lines.add("SUMMARY:" + escapeText(summary));
        if (meeting.location.length() > 0) {
            lines.add("LOCATION:" + escapeText(meeting.location));
        }
        StringBuilder description = new StringBuilder();
        if (meeting.teacher.length() > 0) {
            description.append("教师：").append(meeting.teacher).append('\n');
        }
        if (course.credit.length() > 0) {
            description.append("学分：").append(course.credit).append('\n');
        }
        if (meeting.weekRule != null) {
            description.append("周次：").append(meeting.weekRule.displayText()).append('\n');
        }
        if (meeting.day >= 0 && meeting.day <= 6) {
            description.append("星期：").append(DAY_NAMES[meeting.day]).append('\n');
        }
        if (isWeekUnknown(meeting.weekRule)) {
            description.append("提示：周次未能识别，请以实际课表为准\n");
        }
        if (description.length() > 0) {
            lines.add("DESCRIPTION:" + escapeText(description.toString().trim()));
        }
        lines.add("END:VEVENT");
        return lines;
    }

    /** Weeks this meeting is active in, expanded to one entry per week. */
    private static List<Integer> activeWeeks(WeekRule rule, int semesterWeeks) {
        List<Integer> weeks = new ArrayList<>();
        if (rule == null || isWeekUnknown(rule)) {
            weeks.add(1);
            return weeks;
        }
        for (int week = 1; week <= semesterWeeks; week++) {
            if (rule.containsWeek(week)) {
                weeks.add(week);
            }
        }
        if (weeks.isEmpty()) {
            weeks.add(1);
        }
        return weeks;
    }

    /** Week rules whose referenced weeks cannot be determined from the PDF. */
    private static boolean isWeekUnknown(WeekRule rule) {
        return rule != null && rule.lastReferencedWeek() <= 0
                && (rule.type == WeekRule.Type.PROJECT
                || rule.type == WeekRule.Type.UNKNOWN);
    }

    private static String eventUid(StructuredCourse course, CourseMeeting meeting, int week) {
        String meetingPart = meeting.id != null && meeting.id.length() > 0
                ? meeting.id
                : "d" + meeting.day + "-s" + meeting.startSection
                + "-e" + meeting.endSection;
        String coursePart = course.id != null && course.id.length() > 0
                ? course.id : String.valueOf(course.name.hashCode());
        return "polaris-" + coursePart + "-" + meetingPart + "-w" + week
                + "@polaris.timetable";
    }

    private static TimeRange meetingRange(CourseMeeting meeting,
                                          CourseTimeResolver.Settings settings) {
        if (meeting == null) {
            return null;
        }
        if (meeting.timeMode == CourseTimeMode.CLOCK
                && meeting.startMinuteOfDay >= 0
                && meeting.endMinuteOfDay > meeting.startMinuteOfDay) {
            return new TimeRange(meeting.startMinuteOfDay, meeting.endMinuteOfDay);
        }
        if (meeting.timeMode == CourseTimeMode.SECTION
                && meeting.day >= 0 && meeting.day <= 6
                && meeting.startSection >= 1
                && meeting.endSection >= meeting.startSection) {
            CourseTimeResolver.TimeRange start =
                    CourseTimeResolver.sectionTimeRange(settings, meeting.startSection);
            CourseTimeResolver.TimeRange end =
                    CourseTimeResolver.sectionTimeRange(settings, meeting.endSection);
            if (start != null && end != null && end.endMinutes > start.startMinutes) {
                return new TimeRange(start.startMinutes, end.endMinutes);
            }
        }
        return null;
    }

    private static Calendar firstWeekMonday(String firstWeekDay) {
        Calendar date = parseDate(firstWeekDay);
        int dayOfWeek = date.get(Calendar.DAY_OF_WEEK);
        int mondayOffset = dayOfWeek == Calendar.SUNDAY
                ? -6 : Calendar.MONDAY - dayOfWeek;
        date.add(Calendar.DATE, mondayOffset);
        return date;
    }

    private static Calendar parseDate(String value) {
        Calendar date = Calendar.getInstance();
        String[] parts = value == null ? new String[0] : value.split("/");
        try {
            date.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                    Integer.parseInt(parts[2]), 0, 0, 0);
        } catch (Exception ignored) {
            date.set(2026, Calendar.MARCH, 3, 0, 0, 0);
        }
        date.set(Calendar.MILLISECOND, 0);
        return date;
    }

    private static Calendar dateFor(Calendar firstMonday, int week, int day) {
        Calendar date = (Calendar) firstMonday.clone();
        date.add(Calendar.DATE, (week - 1) * 7 + Math.max(0, day));
        return date;
    }

    private static Calendar nextDay(Calendar date) {
        Calendar next = (Calendar) date.clone();
        next.add(Calendar.DATE, 1);
        return next;
    }

    private static String dateStamp(Calendar date) {
        return new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(date.getTime());
    }

    private static String dateTimeStamp(Calendar date, int minutes) {
        Calendar stamp = (Calendar) date.clone();
        stamp.set(Calendar.HOUR_OF_DAY, minutes / 60);
        stamp.set(Calendar.MINUTE, minutes % 60);
        stamp.set(Calendar.SECOND, 0);
        return new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.ROOT)
                .format(stamp.getTime());
    }

    private static String utcStamp() {
        return new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
                .format(new Date(System.currentTimeMillis()
                        - TimeZone.getDefault().getOffset(System.currentTimeMillis())));
    }

    private static String escapeText(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    /** Folds a line to at most 75 octets per RFC 5545 without splitting UTF-8 code points. */
    static String foldLine(String line) {
        if (line == null || line.length() == 0) {
            return line == null ? "" : line;
        }
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= ICAL_LINE_LIMIT) {
            return line;
        }
        StringBuilder builder = new StringBuilder();
        StringBuilder current = new StringBuilder();
        int currentBytes = 0;
        boolean first = true;
        for (int index = 0; index < line.length(); ) {
            int codePoint = line.codePointAt(index);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = character.getBytes(StandardCharsets.UTF_8).length;
            int limit = first ? ICAL_LINE_LIMIT : ICAL_LINE_LIMIT - 1;
            if (currentBytes > 0 && currentBytes + characterBytes > limit) {
                if (first) {
                    builder.append(current);
                    first = false;
                } else {
                    builder.append("\r\n ").append(current);
                }
                current.setLength(0);
                currentBytes = 0;
            }
            current.append(character);
            currentBytes += characterBytes;
            index += Character.charCount(codePoint);
        }
        if (current.length() > 0) {
            if (first) {
                builder.append(current);
            } else {
                builder.append("\r\n ").append(current);
            }
        }
        return builder.toString();
    }

    private static String csvField(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0
                && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static String timeText(int minutes) {
        int normalized = ((minutes % (24 * 60)) + (24 * 60)) % (24 * 60);
        return twoDigits(normalized / 60) + ":" + twoDigits(normalized % 60);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static final class TimeRange {
        final int startMinutes;
        final int endMinutes;

        TimeRange(int startMinutes, int endMinutes) {
            this.startMinutes = startMinutes;
            this.endMinutes = endMinutes;
        }
    }
}
