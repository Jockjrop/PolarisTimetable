package com.polaris.timetable.widget;

import com.polaris.timetable.Course;
import com.polaris.timetable.storage.ScheduleRepository;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;

import static org.junit.Assert.assertEquals;

public class ScheduleWidgetRefreshPlannerTest {
    @Test
    public void nextBoundaryAfter_usesEveryBoundSchedule() {
        ScheduleRepository.Config config = config();
        Calendar now = dateTime(2026, Calendar.MARCH, 2, 9, 0);
        Course activeScheduleCourse = course(0, 5, 6, "下午课", "1-2周");
        Course otherScheduleCourse = course(0, 3, 4, "上午课", "1-2周");

        Calendar expected = dateTime(2026, Calendar.MARCH, 2, 10, 20);

        assertEquals(expected.getTimeInMillis(), ScheduleWidgetRefreshPlanner.nextBoundaryAfter(
                Arrays.asList(
                        new ScheduleWidgetRefreshPlanner.ScheduleSource(
                                Arrays.asList(activeScheduleCourse), config),
                        new ScheduleWidgetRefreshPlanner.ScheduleSource(
                                Arrays.asList(otherScheduleCourse), config)),
                now));
    }

    @Test
    public void nextBoundaryAfter_usesNextDayFallbackWhenNoBoundaryExists() {
        ScheduleRepository.Config config = config();
        Calendar now = dateTime(2026, Calendar.MARCH, 2, 22, 30);
        Course endedCourse = course(0, 1, 2, "早课", "1-2周");
        Calendar expected = dateTime(2026, Calendar.MARCH, 3, 0, 2);

        assertEquals(expected.getTimeInMillis(), ScheduleWidgetRefreshPlanner.nextBoundaryAfter(
                Arrays.asList(new ScheduleWidgetRefreshPlanner.ScheduleSource(
                        Arrays.asList(endedCourse), config)), now));
    }

    private ScheduleRepository.Config config() {
        ScheduleRepository.Config config = new ScheduleRepository.Config();
        config.firstWeekDay = "2026/3/3";
        config.semesterWeeks = 20;
        config.firstClassStartTime = "08:00";
        config.classDurationMinutes = 50;
        config.classBreakMinutes = 10;
        config.classBigBreakMinutes = 30;
        config.afternoonStartTime = "14:30";
        config.lateAfternoonStartTime = "16:35";
        return config;
    }

    private Course course(int day, int start, int end, String name, String weeks) {
        return new Course(day, start, end, name, weeks, "A101", "教师", "");
    }

    private Calendar dateTime(int year, int month, int day, int hour, int minute) {
        Calendar value = Calendar.getInstance();
        value.clear();
        value.set(year, month, day, hour, minute, 0);
        return value;
    }
}
