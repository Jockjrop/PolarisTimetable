package com.polaris.timetable.statistics;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure weekly/semester statistics over the canonical course model.
 *
 * <p>Section counts are measured in class periods: section-mode meetings count
 * their section span, clock-mode meetings count ceil(duration / 45 minutes).
 * Unknown week rules are treated as a single week so the totals stay
 * conservative. All numbers are derived from the same rules the schedule
 * board renders with.</p>
 */
public final class ScheduleStatistics {
    private static final Pattern CREDIT_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)");
    private static final double CLOCK_SECTION_MINUTES = 45.0;

    private ScheduleStatistics() {
    }

    public static final class Statistics {
        public int courseCount;
        public int experimentCount;
        public int practiceCount;
        public int onlineCount;
        public double totalCredits;
        /** Class periods per week (one representative week). */
        public int weeklySections;
        /** Class periods across the whole semester. */
        public int semesterSections;
        public int teacherCount;
        public String topTeacher = "";
        public int topTeacherCount;
        /** Weekly class periods by day, Monday(0) .. Sunday(6). */
        public final int[] sectionsByDay = new int[7];
    }

    public static Statistics compute(List<StructuredCourse> courses, int semesterWeeks) {
        Statistics stats = new Statistics();
        if (courses == null || courses.isEmpty()) {
            return stats;
        }
        int safeWeeks = Math.max(1, semesterWeeks);
        Map<String, Integer> teacherCounts = new LinkedHashMap<>();
        for (StructuredCourse course : courses) {
            if (course == null) {
                continue;
            }
            stats.courseCount++;
            if (course.courseType == CourseType.EXPERIMENT) {
                stats.experimentCount++;
            } else if (course.courseType == CourseType.PRACTICE) {
                stats.practiceCount++;
            } else if (course.courseType == CourseType.ONLINE) {
                stats.onlineCount++;
            }
            stats.totalCredits += parseCredit(course.credit);
            String teacher = course.teacher == null ? "" : course.teacher.trim();
            if (teacher.length() > 0) {
                Integer count = teacherCounts.get(teacher);
                teacherCounts.put(teacher, count == null ? 1 : count + 1);
            }
            for (CourseMeeting meeting : course.meetings) {
                if (meeting == null) {
                    continue;
                }
                int sections = meetingSections(meeting);
                if (sections <= 0) {
                    continue;
                }
                int activeWeeks = activeWeekCount(meeting.weekRule, safeWeeks);
                stats.weeklySections += sections;
                stats.semesterSections += sections * activeWeeks;
                if (meeting.day >= 0 && meeting.day <= 6) {
                    stats.sectionsByDay[meeting.day] += sections;
                }
            }
        }
        stats.teacherCount = teacherCounts.size();
        for (Map.Entry<String, Integer> entry : teacherCounts.entrySet()) {
            if (entry.getValue() > stats.topTeacherCount) {
                stats.topTeacherCount = entry.getValue();
                stats.topTeacher = entry.getKey();
            }
        }
        return stats;
    }

    /** Weekly class periods for one meeting (a positive integer). */
    static int meetingSections(CourseMeeting meeting) {
        if (meeting == null) {
            return 0;
        }
        if (meeting.timeMode == CourseTimeMode.SECTION
                && meeting.startSection >= 1
                && meeting.endSection >= meeting.startSection) {
            return meeting.endSection - meeting.startSection + 1;
        }
        if (meeting.timeMode == CourseTimeMode.CLOCK
                && meeting.startMinuteOfDay >= 0
                && meeting.endMinuteOfDay > meeting.startMinuteOfDay) {
            double periods = (meeting.endMinuteOfDay - meeting.startMinuteOfDay)
                    / CLOCK_SECTION_MINUTES;
            return Math.max(1, (int) Math.ceil(periods));
        }
        return 0;
    }

    /** Number of weeks the meeting is active within the semester, at least 1. */
    static int activeWeekCount(WeekRule rule, int semesterWeeks) {
        int safeWeeks = Math.max(1, semesterWeeks);
        if (rule == null || rule.lastReferencedWeek() <= 0) {
            return 1;
        }
        int count = 0;
        for (int week = 1; week <= safeWeeks; week++) {
            if (rule.containsWeek(week)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /** Extracts the first decimal number from a credit string, e.g. "3.5 学分". */
    static double parseCredit(String credit) {
        if (credit == null) {
            return 0.0;
        }
        Matcher matcher = CREDIT_PATTERN.matcher(credit);
        if (!matcher.find()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
