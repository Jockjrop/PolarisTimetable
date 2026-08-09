package com.polaris.timetable.export;

import com.polaris.timetable.Course;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Resolves the current week's visible courses and overlap lanes without Android APIs. */
public final class ScheduleExportLayout {
    private ScheduleExportLayout() {
    }

    public static final class Item {
        public final Course course;
        public final int visibleDayIndex;
        public final int startSection;
        public final int endSection;
        public final int lane;
        public final int laneCount;

        Item(Course course, int visibleDayIndex, int startSection, int endSection,
             int lane, int laneCount) {
            this.course = course;
            this.visibleDayIndex = visibleDayIndex;
            this.startSection = startSection;
            this.endSection = endSection;
            this.lane = lane;
            this.laneCount = laneCount;
        }
    }

    public static final class Result {
        public final List<Integer> visibleDays;
        public final List<Item> items;
        public final List<Course> bannerCourses;

        Result(List<Integer> visibleDays, List<Item> items, List<Course> bannerCourses) {
            this.visibleDays = Collections.unmodifiableList(visibleDays);
            this.items = Collections.unmodifiableList(items);
            this.bannerCourses = Collections.unmodifiableList(bannerCourses);
        }

        public boolean hasContent() {
            return !items.isEmpty() || !bannerCourses.isEmpty();
        }
    }

    public static Result create(List<Course> courses, int week,
                                boolean showSaturday, boolean showSunday,
                                int sectionCount) {
        return createInternal(courses, week, showSaturday, showSunday,
                sectionCount, false);
    }

    public static Result createAllWeeks(List<Course> courses,
                                        boolean showSaturday, boolean showSunday,
                                        int sectionCount) {
        return createInternal(courses, 1, showSaturday, showSunday,
                sectionCount, true);
    }

    private static Result createInternal(List<Course> courses, int week,
                                         boolean showSaturday, boolean showSunday,
                                         int sectionCount, boolean includeAllWeeks) {
        List<Integer> visibleDays = visibleDays(showSaturday, showSunday);
        List<MutableItem> candidates = new ArrayList<>();
        List<Course> bannerCourses = new ArrayList<>();
        int boundedSections = Math.max(1, Math.min(20, sectionCount));
        if (courses != null) {
            for (Course course : courses) {
                if (course == null || (!includeAllWeeks
                        && !CourseTimeResolver.isActiveInWeek(course, week))) {
                    continue;
                }
                if (!course.hasFixedTime()) {
                    if (course.isBannerOnlyCourse()) {
                        bannerCourses.add(course);
                    }
                    continue;
                }
                int visibleDayIndex = visibleDays.indexOf(course.day);
                if (visibleDayIndex < 0 || course.startSection > boundedSections) {
                    continue;
                }
                int start = Math.max(1, course.startSection);
                int end = Math.max(start, Math.min(boundedSections, course.endSection));
                candidates.add(new MutableItem(course, visibleDayIndex, start, end));
            }
        }
        Collections.sort(candidates, new Comparator<MutableItem>() {
            @Override
            public int compare(MutableItem first, MutableItem second) {
                int byDay = Integer.compare(first.visibleDayIndex, second.visibleDayIndex);
                if (byDay != 0) {
                    return byDay;
                }
                int byStart = Integer.compare(first.startSection, second.startSection);
                if (byStart != 0) {
                    return byStart;
                }
                int byEnd = Integer.compare(first.endSection, second.endSection);
                if (byEnd != 0) {
                    return byEnd;
                }
                return safe(first.course.name).compareTo(safe(second.course.name));
            }
        });
        assignLanes(candidates);
        List<Item> items = new ArrayList<>();
        for (MutableItem candidate : candidates) {
            items.add(new Item(candidate.course, candidate.visibleDayIndex,
                    candidate.startSection, candidate.endSection,
                    candidate.lane, candidate.laneCount));
        }
        return new Result(new ArrayList<>(visibleDays), items, bannerCourses);
    }

    private static List<Integer> visibleDays(boolean showSaturday, boolean showSunday) {
        List<Integer> result = new ArrayList<>();
        for (int day = 0; day < 5; day++) {
            result.add(day);
        }
        if (showSaturday) {
            result.add(5);
        }
        if (showSunday) {
            result.add(6);
        }
        return result;
    }

    private static void assignLanes(List<MutableItem> items) {
        int groupStart = 0;
        while (groupStart < items.size()) {
            int day = items.get(groupStart).visibleDayIndex;
            int groupEnd = groupStart + 1;
            int furthestSection = items.get(groupStart).endSection;
            while (groupEnd < items.size()) {
                MutableItem next = items.get(groupEnd);
                if (next.visibleDayIndex != day || next.startSection > furthestSection) {
                    break;
                }
                furthestSection = Math.max(furthestSection, next.endSection);
                groupEnd++;
            }
            List<Integer> laneEnds = new ArrayList<>();
            for (int index = groupStart; index < groupEnd; index++) {
                MutableItem item = items.get(index);
                int lane = firstAvailableLane(laneEnds, item.startSection);
                if (lane == laneEnds.size()) {
                    laneEnds.add(item.endSection);
                } else {
                    laneEnds.set(lane, item.endSection);
                }
                item.lane = lane;
            }
            int laneCount = Math.max(1, laneEnds.size());
            for (int index = groupStart; index < groupEnd; index++) {
                items.get(index).laneCount = laneCount;
            }
            groupStart = groupEnd;
        }
    }

    private static int firstAvailableLane(List<Integer> laneEnds, int startSection) {
        for (int lane = 0; lane < laneEnds.size(); lane++) {
            if (laneEnds.get(lane) < startSection) {
                return lane;
            }
        }
        return laneEnds.size();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class MutableItem {
        final Course course;
        final int visibleDayIndex;
        final int startSection;
        final int endSection;
        int lane;
        int laneCount = 1;

        MutableItem(Course course, int visibleDayIndex, int startSection, int endSection) {
            this.course = course;
            this.visibleDayIndex = visibleDayIndex;
            this.startSection = startSection;
            this.endSection = endSection;
        }
    }
}
