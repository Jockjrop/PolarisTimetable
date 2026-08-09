package com.polaris.timetable.widget;

import com.polaris.timetable.Course;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.time.CourseTimeResolver;

final class ScheduleWidgetTimeFormatter {
    private ScheduleWidgetTimeFormatter() {
    }

    static String format(Course course, ScheduleRepository.Config config) {
        return CourseTimeResolver.format(course, settings(config));
    }

    static int endMinutes(Course course, ScheduleRepository.Config config) {
        return CourseTimeResolver.endMinutes(course, settings(config));
    }

    private static CourseTimeResolver.Settings settings(ScheduleRepository.Config config) {
        if (config == null) {
            return null;
        }
        return new CourseTimeResolver.Settings(
                config.firstClassStartTime,
                config.classDurationMinutes,
                config.classBreakMinutes,
                config.classBigBreakMinutes,
                config.afternoonStartTime,
                config.lateAfternoonStartTime,
                config.classTimeConfig);
    }
}
