package com.polaris.timetable.widget;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.storage.ScheduleRepository;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ScheduleWidgetDataTest {
    @Test
    public void forDate_showsOnlyActiveCoursesForThatDay() {
        ScheduleRepository.Config config = config();
        Course monday = course(0, 1, 2, "高等数学", "1-2周");
        Course tuesday = course(1, 3, 4, "大学英语", "1-2周");
        Course ended = course(0, 5, 6, "大学物理", "2周");

        List<ScheduleWidgetEntry> entries = ScheduleWidgetData.forDate(
                Arrays.asList(monday, tuesday, ended), config,
                date(2026, Calendar.MARCH, 2));

        assertEquals(1, entries.size());
        assertEquals("高等数学", entries.get(0).name);
        assertEquals("08:00–09:50", entries.get(0).time);
    }

    @Test
    public void forDate_placesWeeklyPracticeBeforeTimedCourses() {
        ScheduleRepository.Config config = config();
        Course lesson = course(0, 1, 2, "高等数学", "1-2周");
        Course practice = new Course(
                -1, 0, 0, "工程训练", "1-2周", "", "", "", "", "", CourseType.PRACTICE);

        List<ScheduleWidgetEntry> entries = ScheduleWidgetData.forDate(
                Arrays.asList(lesson, practice), config,
                date(2026, Calendar.MARCH, 2));

        assertEquals(2, entries.size());
        assertEquals("工程训练", entries.get(0).name);
        assertEquals("本周实践", entries.get(0).time);
        assertEquals("无固定地点", entries.get(0).location);
    }

    @Test
    public void weekForDate_normalizesConfiguredDateToMonday() {
        assertEquals(1, ScheduleWidgetData.weekForDate(
                "2026/3/3", date(2026, Calendar.MARCH, 2)));
        assertEquals(2, ScheduleWidgetData.weekForDate(
                "2026/3/3", date(2026, Calendar.MARCH, 9)));
    }

    @Test
    public void timeFormatter_fallsBackToSectionsWhenStartTimeIsInvalid() {
        ScheduleRepository.Config config = config();
        config.firstClassStartTime = "待定";
        config.classTimeConfig = "待定";

        assertEquals("第1–2节", ScheduleWidgetTimeFormatter.format(
                course(0, 1, 2, "高等数学", "1-2周"), config));
    }

    @Test
    public void forDate_removesOnlyCoursesThatAlreadyEndedToday() {
        ScheduleRepository.Config config = config();
        Course morning = course(0, 1, 2, "高等数学", "1-2周");
        Course afternoon = course(0, 5, 6, "大学物理", "1-2周");
        Calendar noon = dateTime(2026, Calendar.MARCH, 2, 12, 0);

        List<ScheduleWidgetEntry> entries = ScheduleWidgetData.forDate(
                Arrays.asList(morning, afternoon), config, noon, noon);

        assertEquals(1, entries.size());
        assertEquals("大学物理", entries.get(0).name);
    }

    @Test
    public void forDate_removesCourseAtItsExactEndTime() {
        ScheduleRepository.Config config = config();
        Course morning = course(0, 1, 2, "高等数学", "1-2周");
        Calendar endTime = dateTime(2026, Calendar.MARCH, 2, 9, 50);

        List<ScheduleWidgetEntry> entries = ScheduleWidgetData.forDate(
                Arrays.asList(morning), config, endTime, endTime);

        assertEquals(0, entries.size());
    }

    @Test
    public void forDate_keepsOngoingCourseAndTomorrowMorningCourse() {
        ScheduleRepository.Config config = config();
        Course morning = course(0, 1, 2, "高等数学", "1-2周");
        Calendar mondayDuringClass = dateTime(2026, Calendar.MARCH, 2, 9, 49);
        Calendar sundayNoon = dateTime(2026, Calendar.MARCH, 1, 12, 0);
        Calendar mondayNoon = dateTime(2026, Calendar.MARCH, 2, 12, 0);

        assertEquals(1, ScheduleWidgetData.forDate(
                Arrays.asList(morning), config, mondayDuringClass, mondayDuringClass).size());
        assertEquals(1, ScheduleWidgetData.forDate(
                Arrays.asList(morning), config, mondayNoon, sundayNoon).size());
    }

    @Test
    public void nextCourseEndAfter_returnsNearestFutureEnd() {
        ScheduleRepository.Config config = config();
        Course morning = course(0, 1, 2, "高等数学", "1-2周");
        Course afternoon = course(0, 5, 6, "大学物理", "1-2周");
        Calendar now = dateTime(2026, Calendar.MARCH, 2, 9, 0);
        Calendar expected = dateTime(2026, Calendar.MARCH, 2, 9, 50);
        expected.set(Calendar.SECOND, 1);

        assertEquals(expected.getTimeInMillis(), ScheduleWidgetData.nextCourseEndAfter(
                Arrays.asList(morning, afternoon), config, now));
    }

    @Test
    public void endFiltering_usesCustomClassDurationAndBreak() {
        ScheduleRepository.Config config = config();
        config.firstClassStartTime = "07:30";
        config.classDurationMinutes = 45;
        config.classBreakMinutes = 5;
        Course morning = course(0, 1, 2, "高等数学", "1-2周");
        Calendar beforeEnd = dateTime(2026, Calendar.MARCH, 2, 9, 4);
        Calendar atEnd = dateTime(2026, Calendar.MARCH, 2, 9, 5);

        assertEquals(1, ScheduleWidgetData.forDate(
                Arrays.asList(morning), config, beforeEnd, beforeEnd).size());
        assertEquals(0, ScheduleWidgetData.forDate(
                Arrays.asList(morning), config, atEnd, atEnd).size());
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

    private Calendar date(int year, int month, int day) {
        return dateTime(year, month, day, 12, 0);
    }

    private Calendar dateTime(int year, int month, int day, int hour, int minute) {
        Calendar value = Calendar.getInstance();
        value.clear();
        value.set(year, month, day, hour, minute, 0);
        return value;
    }
}
