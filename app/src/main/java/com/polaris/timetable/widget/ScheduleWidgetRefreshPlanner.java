package com.polaris.timetable.widget;

import com.polaris.timetable.Course;
import com.polaris.timetable.storage.ScheduleRepository;

import java.util.Calendar;
import java.util.List;

/**
 * Computes the next course-time boundary across every schedule used by a widget.
 * Kept separate from Android alarm and widget APIs so multi-schedule behavior is
 * covered by local unit tests.
 */
final class ScheduleWidgetRefreshPlanner {
    private ScheduleWidgetRefreshPlanner() {
    }

    static long nextBoundaryAfter(Iterable<ScheduleSource> sources, Calendar now) {
        if (now == null) {
            return -1L;
        }
        long nextBoundary = -1L;
        if (sources != null) {
            for (ScheduleSource source : sources) {
                if (source == null) {
                    continue;
                }
                nextBoundary = earliestFuture(nextBoundary,
                        ScheduleWidgetData.nextCourseStartAfter(source.courses, source.config, now));
                nextBoundary = earliestFuture(nextBoundary,
                        ScheduleWidgetData.nextCourseEndAfter(source.courses, source.config, now));
            }
        }
        return nextBoundary > 0
                ? nextBoundary : ScheduleWidgetData.nextDayBoundaryAfter(now);
    }

    private static long earliestFuture(long current, long candidate) {
        if (candidate <= 0) {
            return current;
        }
        return current <= 0 ? candidate : Math.min(current, candidate);
    }

    static final class ScheduleSource {
        final List<Course> courses;
        final ScheduleRepository.Config config;

        ScheduleSource(List<Course> courses, ScheduleRepository.Config config) {
            this.courses = courses;
            this.config = config;
        }
    }
}
