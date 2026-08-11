package com.polaris.timetable.time;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CourseTimeResolverTest {
    @Test
    public void timeRange_usesSharedSectionSettings() {
        Course course = course(0, 1, 2, "高等数学", "1-20周");

        CourseTimeResolver.TimeRange range = CourseTimeResolver.timeRange(course, settings());

        assertEquals(8 * 60, range.startMinutes);
        assertEquals(9 * 60 + 50, range.endMinutes);
        assertEquals("08:00–09:50", range.displayText());
    }

    @Test
    public void timeRange_exactClockTimeOverridesSectionSettings() {
        Course course = exactCourse(0, 9 * 60 + 10, 10 * 60 + 35, "讨论课", "1-20周");

        CourseTimeResolver.TimeRange range = CourseTimeResolver.timeRange(course, null);

        assertEquals(9 * 60 + 10, range.startMinutes);
        assertEquals(10 * 60 + 35, range.endMinutes);
        assertEquals("09:10–10:35", CourseTimeResolver.format(course, settings()));
    }

    @Test
    public void activeWeek_respectsOddAndEvenRules() {
        assertTrue(CourseTimeResolver.isActiveInWeek(
                course(0, 1, 2, "单周课", "1-8周 单周"), 3));
        assertFalse(CourseTimeResolver.isActiveInWeek(
                course(0, 1, 2, "单周课", "1-8周 单周"), 4));
        assertTrue(CourseTimeResolver.isActiveInWeek(
                course(0, 1, 2, "双周课", "1-8周 双周"), 4));
    }

    @Test
    public void resolveToday_returnsOngoingCourseAndMinutesToEnd() {
        Course morning = course(0, 1, 2, "高等数学", "1-20周");
        Calendar now = dateTime("Asia/Shanghai", 2026, Calendar.MARCH, 2, 9, 30);

        CourseTimeResolver.TodayOverview overview = CourseTimeResolver.resolveToday(
                Collections.singletonList(morning), settings(),
                mondayMillis(now, 2026, Calendar.MARCH, 2), 20, now);

        assertEquals(CourseTimeResolver.TodayStatus.ONGOING, overview.status);
        assertSame(morning, overview.course);
        assertEquals(20, overview.minutesToBoundary);
    }

    @Test
    public void resolveToday_returnsNextCourseBeforeItStarts() {
        Course morning = course(0, 1, 2, "高等数学", "1-20周");
        Course afternoon = course(0, 5, 6, "大学物理", "1-20周");
        Calendar now = dateTime("Asia/Shanghai", 2026, Calendar.MARCH, 2, 12, 0);

        CourseTimeResolver.TodayOverview overview = CourseTimeResolver.resolveToday(
                Arrays.asList(morning, afternoon), settings(),
                mondayMillis(now, 2026, Calendar.MARCH, 2), 20, now);

        assertEquals(CourseTimeResolver.TodayStatus.NEXT, overview.status);
        assertSame(afternoon, overview.course);
        assertEquals(150, overview.minutesToBoundary);
    }

    @Test
    public void resolveToday_distinguishesFinishedAndNoCourse() {
        Course morning = course(0, 1, 2, "高等数学", "1-20周");
        Calendar mondayNight = dateTime("Asia/Shanghai", 2026, Calendar.MARCH, 2, 22, 0);
        Calendar tuesday = dateTime("Asia/Shanghai", 2026, Calendar.MARCH, 3, 12, 0);
        long firstWeek = mondayMillis(mondayNight, 2026, Calendar.MARCH, 2);

        assertEquals(CourseTimeResolver.TodayStatus.FINISHED,
                CourseTimeResolver.resolveToday(Collections.singletonList(morning), settings(),
                        firstWeek, 20, mondayNight).status);
        assertEquals(CourseTimeResolver.TodayStatus.NO_COURSES,
                CourseTimeResolver.resolveToday(Collections.singletonList(morning), settings(),
                        firstWeek, 20, tuesday).status);
    }

    @Test
    public void weekForDate_isStableAcrossDaylightSavingBoundary() {
        Calendar firstWeek = dateTime("America/Los_Angeles",
                2026, Calendar.MARCH, 2, 0, 0);
        Calendar secondWeek = dateTime("America/Los_Angeles",
                2026, Calendar.MARCH, 9, 0, 0);

        assertEquals(2, CourseTimeResolver.weekForDate(
                firstWeek.getTimeInMillis(), secondWeek));
    }

    @Test
    public void inferSemesterWeeks_usesLargestReliableReferencedWeek() {
        assertEquals(16, CourseTimeResolver.inferSemesterWeeks(
                Collections.singletonList(course(0, 1, 2, "课程", "1-16周")), 20, 20));
        assertEquals(18, CourseTimeResolver.inferSemesterWeeks(
                Collections.singletonList(course(0, 1, 2, "课程", "1-18周")), 20, 20));
        assertEquals(18, CourseTimeResolver.inferSemesterWeeks(Arrays.asList(
                course(0, 1, 2, "课程一", "1-16周"),
                course(1, 1, 2, "课程二", "2、18周")), 20, 20));
    }

    @Test
    public void inferSemesterWeeks_usesDefaultWithoutReliableNumericRule() {
        assertEquals(20, CourseTimeResolver.inferSemesterWeeks(Arrays.asList(
                course(0, 1, 2, "课程一", "全周"),
                course(1, 1, 2, "课程二", "周次见PDF")), 20, 20));
    }

    private CourseTimeResolver.Settings settings() {
        return new CourseTimeResolver.Settings(
                "08:00", 50, 10, 30, "14:30", "16:35", "");
    }

    private Course course(int day, int start, int end, String name, String weeks) {
        return new Course(day, start, end, name, weeks, "A101", "教师", "");
    }

    private Course exactCourse(int day, int startMinute, int endMinute,
                               String name, String weeks) {
        return new Course(day, 0, 0, name, weeks, "A101", "教师", "", "", "",
                CourseType.LECTURE, "", "", CourseTimeMode.CLOCK,
                startMinute, endMinute);
    }

    private long mondayMillis(Calendar template, int year, int month, int day) {
        return dateTime(template.getTimeZone().getID(), year, month, day, 0, 0)
                .getTimeInMillis();
    }

    private Calendar dateTime(String zone, int year, int month, int day, int hour, int minute) {
        Calendar value = Calendar.getInstance(TimeZone.getTimeZone(zone));
        value.clear();
        value.set(year, month, day, hour, minute, 0);
        return value;
    }
}
