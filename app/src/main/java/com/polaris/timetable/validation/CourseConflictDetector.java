package com.polaris.timetable.validation;

import com.polaris.timetable.Course;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds course pairs whose resolved minute ranges overlap in at least one common week. */
public final class CourseConflictDetector {
    private CourseConflictDetector() {
    }

    public static final class Conflict {
        public final Course first;
        public final Course second;
        public final int day;
        public final int overlapStartSection;
        public final int overlapEndSection;
        public final int overlapStartMinutes;
        public final int overlapEndMinutes;
        public final List<Integer> commonWeeks;

        Conflict(Course first, Course second, int day,
                 int overlapStartSection, int overlapEndSection,
                 int overlapStartMinutes, int overlapEndMinutes,
                 List<Integer> commonWeeks) {
            this.first = first;
            this.second = second;
            this.day = day;
            this.overlapStartSection = overlapStartSection;
            this.overlapEndSection = overlapEndSection;
            this.overlapStartMinutes = overlapStartMinutes;
            this.overlapEndMinutes = overlapEndMinutes;
            this.commonWeeks = Collections.unmodifiableList(new ArrayList<>(commonWeeks));
        }

        public boolean occursInWeek(int week) {
            return commonWeeks.contains(week);
        }

        public String commonWeeksText() {
            return formatWeeks(commonWeeks);
        }

        public String overlapTimeText() {
            if (overlapStartMinutes < 0 || overlapEndMinutes <= overlapStartMinutes) {
                return overlapStartSection == overlapEndSection
                        ? "第" + overlapStartSection + "节"
                        : "第" + overlapStartSection + "–" + overlapEndSection + "节";
            }
            return CourseTimeResolver.formatMinuteOfDay(overlapStartMinutes)
                    + "–" + CourseTimeResolver.formatMinuteOfDay(overlapEndMinutes);
        }
    }

    public static List<Conflict> findAll(List<Course> source, int semesterWeeks) {
        return findAll(source, semesterWeeks, CourseTimeResolver.defaultSettings());
    }

    public static List<Conflict> findAll(
            List<Course> source,
            int semesterWeeks,
            CourseTimeResolver.Settings settings) {
        if (source == null || source.size() < 2) {
            return Collections.emptyList();
        }
        int lastWeek = Math.max(1, semesterWeeks);
        List<Conflict> result = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < source.size(); firstIndex++) {
            Course first = source.get(firstIndex);
            if (!isFixedCourse(first)) {
                continue;
            }
            CourseTimeResolver.TimeRange firstRange =
                    CourseTimeResolver.timeRange(first, settings);
            for (int secondIndex = firstIndex + 1; secondIndex < source.size(); secondIndex++) {
                Course second = source.get(secondIndex);
                CourseTimeResolver.TimeRange secondRange =
                        CourseTimeResolver.timeRange(second, settings);
                if (!isFixedCourse(second) || first.day != second.day
                        || !timeRangesOverlap(firstRange, secondRange)) {
                    continue;
                }
                List<Integer> commonWeeks = commonWeeks(first, second, lastWeek);
                if (commonWeeks.isEmpty()) {
                    continue;
                }
                result.add(new Conflict(
                        first,
                        second,
                        first.day,
                        overlapStartSection(first, second),
                        overlapEndSection(first, second),
                        Math.max(firstRange.startMinutes, secondRange.startMinutes),
                        Math.min(firstRange.endMinutes, secondRange.endMinutes),
                        commonWeeks));
            }
        }
        return result;
    }

    public static List<Conflict> forWeek(List<Course> source, int semesterWeeks, int week) {
        return forWeek(source, semesterWeeks, week, CourseTimeResolver.defaultSettings());
    }

    public static List<Conflict> forWeek(
            List<Course> source,
            int semesterWeeks,
            int week,
            CourseTimeResolver.Settings settings) {
        int lastWeek = Math.max(1, semesterWeeks);
        if (source == null || source.size() < 2 || week <= 0 || week > lastWeek) {
            return Collections.emptyList();
        }
        List<Conflict> result = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < source.size(); firstIndex++) {
            Course first = source.get(firstIndex);
            if (!isFixedCourse(first) || !CourseTimeResolver.isActiveInWeek(first, week)) {
                continue;
            }
            CourseTimeResolver.TimeRange firstRange =
                    CourseTimeResolver.timeRange(first, settings);
            for (int secondIndex = firstIndex + 1; secondIndex < source.size(); secondIndex++) {
                Course second = source.get(secondIndex);
                CourseTimeResolver.TimeRange secondRange =
                        CourseTimeResolver.timeRange(second, settings);
                if (!isFixedCourse(second) || first.day != second.day
                        || !timeRangesOverlap(firstRange, secondRange)
                        || !CourseTimeResolver.isActiveInWeek(second, week)) {
                    continue;
                }
                result.add(new Conflict(
                        first,
                        second,
                        first.day,
                        overlapStartSection(first, second),
                        overlapEndSection(first, second),
                        Math.max(firstRange.startMinutes, secondRange.startMinutes),
                        Math.min(firstRange.endMinutes, secondRange.endMinutes),
                        commonWeeks(first, second, lastWeek)));
            }
        }
        return result;
    }

    public static List<Course> conflictingCoursesForWeek(
            List<Course> source, int semesterWeeks, int week) {
        return conflictingCoursesForWeek(
                source, semesterWeeks, week, CourseTimeResolver.defaultSettings());
    }

    public static List<Course> conflictingCoursesForWeek(
            List<Course> source,
            int semesterWeeks,
            int week,
            CourseTimeResolver.Settings settings) {
        List<Course> result = new ArrayList<>();
        for (Conflict conflict : forWeek(source, semesterWeeks, week, settings)) {
            addIdentity(result, conflict.first);
            addIdentity(result, conflict.second);
        }
        return result;
    }

    public static String formatWeeks(List<Integer> weeks) {
        if (weeks == null || weeks.isEmpty()) {
            return "无共同周次";
        }
        List<Integer> normalized = new ArrayList<>();
        for (Integer week : weeks) {
            if (week != null && week > 0 && !normalized.contains(week)) {
                normalized.add(week);
            }
        }
        Collections.sort(normalized);
        if (normalized.isEmpty()) {
            return "无共同周次";
        }
        StringBuilder text = new StringBuilder("第");
        int rangeStart = normalized.get(0);
        int previous = rangeStart;
        for (int index = 1; index <= normalized.size(); index++) {
            boolean end = index == normalized.size();
            int current = end ? Integer.MIN_VALUE : normalized.get(index);
            if (!end && current == previous + 1) {
                previous = current;
                continue;
            }
            if (text.length() > 1) {
                text.append('、');
            }
            text.append(rangeStart);
            if (previous != rangeStart) {
                text.append('–').append(previous);
            }
            if (!end) {
                rangeStart = current;
                previous = current;
            }
        }
        return text.append('周').toString();
    }

    private static List<Integer> commonWeeks(Course first, Course second, int lastWeek) {
        List<Integer> result = new ArrayList<>();
        for (int week = 1; week <= lastWeek; week++) {
            if (CourseTimeResolver.isActiveInWeek(first, week)
                    && CourseTimeResolver.isActiveInWeek(second, week)) {
                result.add(week);
            }
        }
        return result;
    }

    private static boolean isFixedCourse(Course course) {
        return course != null && course.hasScheduledTime();
    }

    private static boolean timeRangesOverlap(
            CourseTimeResolver.TimeRange first,
            CourseTimeResolver.TimeRange second) {
        return first != null && second != null
                && first.startMinutes < second.endMinutes
                && second.startMinutes < first.endMinutes;
    }

    private static int overlapStartSection(Course first, Course second) {
        return first.hasSectionTime() && second.hasSectionTime()
                ? Math.max(first.startSection, second.startSection) : 0;
    }

    private static int overlapEndSection(Course first, Course second) {
        return first.hasSectionTime() && second.hasSectionTime()
                ? Math.min(first.endSection, second.endSection) : 0;
    }

    private static void addIdentity(List<Course> result, Course course) {
        for (Course existing : result) {
            if (existing == course) {
                return;
            }
        }
        result.add(course);
    }
}
