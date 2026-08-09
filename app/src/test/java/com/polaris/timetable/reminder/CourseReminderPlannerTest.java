package com.polaris.timetable.reminder;

import com.polaris.timetable.Course;
import com.polaris.timetable.time.CourseTimeResolver;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class CourseReminderPlannerTest {
    @Test
    public void plan_returnsFutureEntriesInTimeOrder() {
        Calendar now = dateTime(2026, Calendar.MARCH, 2, 7, 0);
        Course afternoon = course(0, 5, 6, "大学物理", "1-2周");
        Course morning = course(0, 1, 2, "高等数学", "1-2周");

        List<CourseReminderPlanner.Entry> entries = CourseReminderPlanner.plan(
                Arrays.asList(afternoon, morning), settings(),
                dateTime(2026, Calendar.MARCH, 2, 0, 0).getTimeInMillis(),
                2, 15, now, 10);

        assertEquals(4, entries.size());
        assertSame(morning, entries.get(0).course);
        assertEquals(dateTime(2026, Calendar.MARCH, 2, 7, 45).getTimeInMillis(),
                entries.get(0).triggerAtMillis);
        assertSame(afternoon, entries.get(1).course);
        assertEquals("14:30–16:20", entries.get(1).classTimeText);
    }

    @Test
    public void plan_respectsOddWeekRuleAndLimit() {
        Course course = course(1, 1, 2, "单周实验", "1-8周 单周");
        Calendar now = dateTime(2026, Calendar.MARCH, 1, 12, 0);

        List<CourseReminderPlanner.Entry> entries = CourseReminderPlanner.plan(
                Collections.singletonList(course), settings(),
                dateTime(2026, Calendar.MARCH, 2, 0, 0).getTimeInMillis(),
                8, 10, now, 2);

        assertEquals(2, entries.size());
        assertEquals(1, entries.get(0).week);
        assertEquals(3, entries.get(1).week);
    }

    @Test
    public void plan_doesNotBackfillMissedReminder() {
        Course course = course(0, 1, 2, "高等数学", "1周");
        Calendar now = dateTime(2026, Calendar.MARCH, 2, 7, 55);

        List<CourseReminderPlanner.Entry> entries = CourseReminderPlanner.plan(
                Collections.singletonList(course), settings(),
                dateTime(2026, Calendar.MARCH, 2, 0, 0).getTimeInMillis(),
                1, 15, now, 10);

        assertEquals(0, entries.size());
    }

    private CourseTimeResolver.Settings settings() {
        return new CourseTimeResolver.Settings(
                "08:00", 50, 10, 30, "14:30", "16:35", "");
    }

    private Course course(int day, int start, int end, String name, String weeks) {
        return new Course(day, start, end, name, weeks, "A101", "教师", "");
    }

    private Calendar dateTime(int year, int month, int day, int hour, int minute) {
        Calendar value = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        value.clear();
        value.set(year, month, day, hour, minute, 0);
        return value;
    }
}
