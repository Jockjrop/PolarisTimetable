package com.polaris.timetable.time;

import com.polaris.timetable.Course;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure time-to-pixel mapping shared by the timetable view and unit tests. */
public final class ScheduleTimeAxis {
    private static final int MIN_LUNCH_BREAK_MINUTES = 45;
    private static final int LUNCH_WINDOW_START_MINUTE = 10 * 60;
    private static final int LUNCH_WINDOW_END_MINUTE = 16 * 60;
    private static final int CORE_LUNCH_START_MAX_MINUTE = 12 * 60 + 30;
    private static final int CORE_LUNCH_END_MIN_MINUTE = 14 * 60;

    private ScheduleTimeAxis() {
    }

    public static Axis create(
            List<Course> courses,
            CourseTimeResolver.Settings settings,
            int sectionCount,
            float sectionHeightPx) {
        return create(courses, settings, sectionCount, sectionHeightPx, false);
    }

    public static Axis create(
            List<Course> courses,
            CourseTimeResolver.Settings settings,
            int sectionCount,
            float sectionHeightPx,
            boolean collapseLunchBreak) {
        CourseTimeResolver.Settings safeSettings = settings == null
                ? CourseTimeResolver.defaultSettings() : settings;
        int safeSectionCount = Math.max(1, sectionCount);
        CourseTimeResolver.TimeRange firstSection =
                CourseTimeResolver.sectionTimeRange(safeSettings, 1);
        CourseTimeResolver.TimeRange lastSection =
                CourseTimeResolver.sectionTimeRange(safeSettings, safeSectionCount);
        int sectionStart = firstSection == null ? 8 * 60 : firstSection.startMinutes;
        int sectionEnd = lastSection == null ? 18 * 60 : lastSection.endMinutes;
        int earliestCourse = Integer.MAX_VALUE;
        int latestCourse = Integer.MIN_VALUE;

        if (courses != null) {
            for (Course course : courses) {
                CourseTimeResolver.TimeRange range =
                        CourseTimeResolver.timeRange(course, safeSettings);
                if (range == null) {
                    continue;
                }
                earliestCourse = Math.min(earliestCourse, range.startMinutes);
                latestCourse = Math.max(latestCourse, range.endMinutes);
            }
        }

        int start = earliestCourse == Integer.MAX_VALUE
                ? sectionStart : Math.min(sectionStart, earliestCourse);
        int end = latestCourse == Integer.MIN_VALUE
                ? sectionEnd : Math.max(sectionEnd, latestCourse);
        if (end <= start) {
            start = earliestCourse == Integer.MAX_VALUE ? sectionStart : earliestCourse;
            end = latestCourse == Integer.MIN_VALUE ? sectionEnd : latestCourse;
        }
        float pixelsPerMinute = Math.max(0.1f,
                sectionHeightPx / Math.max(1, safeSettings.classMinutes));
        TimeGap lunchBreak = findLunchBreak(safeSettings, safeSectionCount);
        List<TimeGap> collapsedGaps = collapsedGaps(
                courses, safeSettings, safeSectionCount, lunchBreak, collapseLunchBreak);
        return new Axis(start, end, pixelsPerMinute, collapsedGaps,
                collapseLunchBreak ? lunchBreak : null);
    }

    private static List<TimeGap> collapsedGaps(
            List<Course> courses,
            CourseTimeResolver.Settings settings,
            int sectionCount,
            TimeGap lunchBreak,
            boolean collapseLunchBreak) {
        List<TimeGap> gaps = new ArrayList<>();
        for (int section = 1; section < sectionCount; section++) {
            CourseTimeResolver.TimeRange current =
                    CourseTimeResolver.sectionTimeRange(settings, section);
            CourseTimeResolver.TimeRange next =
                    CourseTimeResolver.sectionTimeRange(settings, section + 1);
            if (current == null || next == null) {
                continue;
            }
            TimeGap gap = new TimeGap(current.endMinutes, next.startMinutes);
            if (gap.durationMinutes() <= 0) {
                continue;
            }
            if (sameGap(gap, lunchBreak)) {
                continue;
            }
            gaps.add(gap);
        }
        if (collapseLunchBreak && lunchBreak != null
                && !overlapsCourse(courses, settings, lunchBreak)) {
            gaps.add(lunchBreak);
        }
        return mergeGaps(gaps);
    }

    private static TimeGap findLunchBreak(
            CourseTimeResolver.Settings settings,
            int sectionCount) {
        TimeGap best = null;
        for (int section = 1; section < sectionCount; section++) {
            CourseTimeResolver.TimeRange current =
                    CourseTimeResolver.sectionTimeRange(settings, section);
            CourseTimeResolver.TimeRange next =
                    CourseTimeResolver.sectionTimeRange(settings, section + 1);
            if (current == null || next == null) {
                continue;
            }
            int gapMinutes = next.startMinutes - current.endMinutes;
            if (gapMinutes < MIN_LUNCH_BREAK_MINUTES
                    || current.endMinutes < LUNCH_WINDOW_START_MINUTE
                    || next.startMinutes > LUNCH_WINDOW_END_MINUTE) {
                continue;
            }
            if (best == null || gapMinutes > best.durationMinutes()) {
                best = new TimeGap(current.endMinutes, next.startMinutes);
            }
        }
        return best == null ? findCoreLunchWindow(settings, sectionCount) : best;
    }

    private static TimeGap findCoreLunchWindow(
            CourseTimeResolver.Settings settings,
            int sectionCount) {
        int start = -1;
        int end = -1;
        for (int section = 1; section <= sectionCount; section++) {
            CourseTimeResolver.TimeRange range =
                    CourseTimeResolver.sectionTimeRange(settings, section);
            if (range == null) {
                continue;
            }
            if (range.endMinutes >= LUNCH_WINDOW_START_MINUTE
                    && range.endMinutes <= CORE_LUNCH_START_MAX_MINUTE) {
                start = Math.max(start, range.endMinutes);
            }
            if (range.startMinutes >= CORE_LUNCH_END_MIN_MINUTE
                    && range.startMinutes <= LUNCH_WINDOW_END_MINUTE) {
                end = end < 0 ? range.startMinutes : Math.min(end, range.startMinutes);
            }
        }
        return start >= 0 && end - start >= MIN_LUNCH_BREAK_MINUTES
                ? new TimeGap(start, end) : null;
    }

    private static boolean overlapsCourse(
            List<Course> courses,
            CourseTimeResolver.Settings settings,
            TimeGap gap) {
        if (courses == null) {
            return false;
        }
        for (Course course : courses) {
            CourseTimeResolver.TimeRange range =
                    CourseTimeResolver.timeRange(course, settings);
            if (range != null && range.startMinutes < gap.endMinute
                    && gap.startMinute < range.endMinutes) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameGap(TimeGap first, TimeGap second) {
        return first != null && second != null
                && first.startMinute == second.startMinute
                && first.endMinute == second.endMinute;
    }

    private static List<TimeGap> mergeGaps(List<TimeGap> source) {
        if (source.isEmpty()) {
            return Collections.emptyList();
        }
        List<TimeGap> sorted = new ArrayList<>(source);
        Collections.sort(sorted, Comparator.comparingInt(gap -> gap.startMinute));
        List<TimeGap> merged = new ArrayList<>();
        for (TimeGap gap : sorted) {
            if (gap.endMinute <= gap.startMinute) {
                continue;
            }
            if (merged.isEmpty()) {
                merged.add(gap);
                continue;
            }
            TimeGap previous = merged.get(merged.size() - 1);
            if (gap.startMinute <= previous.endMinute) {
                merged.set(merged.size() - 1, new TimeGap(
                        previous.startMinute, Math.max(previous.endMinute, gap.endMinute)));
            } else {
                merged.add(gap);
            }
        }
        return merged;
    }

    public static final class Axis {
        public final int startMinute;
        public final int endMinute;
        public final float pixelsPerMinute;
        public final int collapsedStartMinute;
        public final int collapsedEndMinute;
        private final List<TimeGap> collapsedGaps;

        Axis(int startMinute, int endMinute, float pixelsPerMinute) {
            this(startMinute, endMinute, pixelsPerMinute,
                    Collections.emptyList(), null);
        }

        Axis(int startMinute, int endMinute, float pixelsPerMinute,
             List<TimeGap> collapsedGaps, TimeGap lunchBreak) {
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.pixelsPerMinute = pixelsPerMinute;
            this.collapsedGaps = clippedGaps(collapsedGaps, startMinute, endMinute);
            this.collapsedStartMinute = containsGap(this.collapsedGaps, lunchBreak)
                    ? lunchBreak.startMinute : -1;
            this.collapsedEndMinute = containsGap(this.collapsedGaps, lunchBreak)
                    ? lunchBreak.endMinute : -1;
        }

        public int contentHeight() {
            return Math.max(1, yForMinute(endMinute));
        }

        public int yForMinute(int minute) {
            int bounded = Math.max(startMinute, Math.min(endMinute, minute));
            int removedMinutes = 0;
            for (TimeGap gap : collapsedGaps) {
                if (bounded <= gap.startMinute) {
                    break;
                }
                removedMinutes += Math.max(0,
                        Math.min(bounded, gap.endMinute) - gap.startMinute);
            }
            return Math.round((bounded - startMinute - removedMinutes) * pixelsPerMinute);
        }

        public int minuteForY(int y) {
            int bounded = Math.max(0, Math.min(contentHeight(), y));
            int cursorMinute = startMinute;
            float cursorY = 0f;
            for (TimeGap gap : collapsedGaps) {
                int visibleEnd = Math.max(cursorMinute, Math.min(endMinute, gap.startMinute));
                float segmentHeight = (visibleEnd - cursorMinute) * pixelsPerMinute;
                if (bounded < cursorY + segmentHeight) {
                    return boundedMinute(cursorMinute
                            + Math.round((bounded - cursorY) / pixelsPerMinute));
                }
                cursorY += segmentHeight;
                cursorMinute = Math.max(cursorMinute, Math.min(endMinute, gap.endMinute));
            }
            return boundedMinute(cursorMinute
                    + Math.round((bounded - cursorY) / pixelsPerMinute));
        }

        public int heightForRange(int start, int end) {
            if (end <= start) {
                return 0;
            }
            int top = yForMinute(start);
            int bottom = yForMinute(end);
            return Math.max(0, bottom - top);
        }

        public boolean isLunchBreakCollapsed() {
            return collapsedStartMinute >= startMinute
                    && collapsedEndMinute > collapsedStartMinute
                    && collapsedEndMinute <= endMinute;
        }

        private int boundedMinute(int minute) {
            return Math.max(startMinute, Math.min(endMinute, minute));
        }

        private static List<TimeGap> clippedGaps(
                List<TimeGap> source, int startMinute, int endMinute) {
            if (source == null || source.isEmpty()) {
                return Collections.emptyList();
            }
            List<TimeGap> clipped = new ArrayList<>();
            for (TimeGap gap : source) {
                int start = Math.max(startMinute, gap.startMinute);
                int end = Math.min(endMinute, gap.endMinute);
                if (end > start) {
                    clipped.add(new TimeGap(start, end));
                }
            }
            return clipped;
        }

        private static boolean containsGap(List<TimeGap> source, TimeGap target) {
            if (target == null) {
                return false;
            }
            for (TimeGap gap : source) {
                if (gap.startMinute <= target.startMinute
                        && gap.endMinute >= target.endMinute) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class TimeGap {
        final int startMinute;
        final int endMinute;

        TimeGap(int startMinute, int endMinute) {
            this.startMinute = startMinute;
            this.endMinute = endMinute;
        }

        int durationMinutes() {
            return endMinute - startMinute;
        }
    }
}
