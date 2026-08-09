package com.polaris.timetable.validation;

import com.polaris.timetable.Course;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds course pairs that occupy overlapping sections in at least one common week. */
public final class CourseConflictDetector {
    private CourseConflictDetector() {
    }

    public static final class Conflict {
        public final Course first;
        public final Course second;
        public final int day;
        public final int overlapStartSection;
        public final int overlapEndSection;
        public final List<Integer> commonWeeks;

        Conflict(Course first, Course second, int day,
                 int overlapStartSection, int overlapEndSection,
                 List<Integer> commonWeeks) {
            this.first = first;
            this.second = second;
            this.day = day;
            this.overlapStartSection = overlapStartSection;
            this.overlapEndSection = overlapEndSection;
            this.commonWeeks = Collections.unmodifiableList(new ArrayList<>(commonWeeks));
        }

        public boolean occursInWeek(int week) {
            return commonWeeks.contains(week);
        }

        public String commonWeeksText() {
            return formatWeeks(commonWeeks);
        }
    }

    public static List<Conflict> findAll(List<Course> source, int semesterWeeks) {
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
            for (int secondIndex = firstIndex + 1; secondIndex < source.size(); secondIndex++) {
                Course second = source.get(secondIndex);
                if (!isFixedCourse(second) || first.day != second.day
                        || !sectionsOverlap(first, second)) {
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
                        Math.max(first.startSection, second.startSection),
                        Math.min(first.endSection, second.endSection),
                        commonWeeks));
            }
        }
        return result;
    }

    public static List<Conflict> forWeek(List<Course> source, int semesterWeeks, int week) {
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
            for (int secondIndex = firstIndex + 1; secondIndex < source.size(); secondIndex++) {
                Course second = source.get(secondIndex);
                if (!isFixedCourse(second) || first.day != second.day
                        || !sectionsOverlap(first, second)
                        || !CourseTimeResolver.isActiveInWeek(second, week)) {
                    continue;
                }
                result.add(new Conflict(
                        first,
                        second,
                        first.day,
                        Math.max(first.startSection, second.startSection),
                        Math.min(first.endSection, second.endSection),
                        commonWeeks(first, second, lastWeek)));
            }
        }
        return result;
    }

    public static List<Course> conflictingCoursesForWeek(
            List<Course> source, int semesterWeeks, int week) {
        List<Course> result = new ArrayList<>();
        for (Conflict conflict : forWeek(source, semesterWeeks, week)) {
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
        return course != null && course.hasFixedTime();
    }

    private static boolean sectionsOverlap(Course first, Course second) {
        return first.startSection <= second.endSection
                && second.startSection <= first.endSection;
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
