package com.polaris.timetable.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ScheduleTimeAxisTest {
    @Test
    public void exactCourseOutsideSchoolHoursDefinesVisibleBounds() {
        Course midnight = exactCourse(0, 30, 90);

        ScheduleTimeAxis.Axis axis = ScheduleTimeAxis.create(
                Collections.singletonList(midnight), settings(), 11, 100f);

        assertEquals(30, axis.startMinute);
        assertEquals(CourseTimeResolver.sectionTimeRange(settings(), 11).endMinutes,
                axis.endMinute);
        assertEquals(120, axis.heightForRange(30, 90));
    }

    @Test
    public void blockHeightIsProportionalToDuration() {
        ScheduleTimeAxis.Axis axis = ScheduleTimeAxis.create(
                Collections.emptyList(),
                settings(), 11, 100f);

        assertEquals(100, axis.heightForRange(8 * 60, 8 * 60 + 50));
        assertEquals(200, axis.heightForRange(8 * 60, 9 * 60 + 50));
    }

    @Test
    public void emptyClassBreakDoesNotUseVisibleHeight() {
        ScheduleTimeAxis.Axis axis = ScheduleTimeAxis.create(
                Collections.emptyList(), settings(), 11, 100f);

        assertEquals(axis.yForMinute(8 * 60 + 50), axis.yForMinute(9 * 60));
    }

    @Test
    public void courseInsideClassBreakDoesNotReopenThatBreak() {
        ScheduleTimeAxis.Axis axis = ScheduleTimeAxis.create(
                Collections.singletonList(exactCourse(0, 8 * 60 + 52, 8 * 60 + 58)),
                settings(), 11, 100f);

        assertEquals(8 * 60, axis.startMinute);
        assertEquals(0, axis.heightForRange(8 * 60 + 52, 8 * 60 + 58));
    }

    @Test
    public void exactCourseDisplaySnapsOutwardToNearestClassTimes() {
        ScheduleTimeAxis.Axis axis = ScheduleTimeAxis.create(
                Arrays.asList(exactCourse(0, 10 * 60 + 25, 11 * 60 + 5)),
                settings(), 11, 100f);

        assertEquals(8 * 60, axis.startMinute);
        assertEquals(CourseTimeResolver.sectionTimeRange(settings(), 11).endMinutes,
                axis.endMinute);
    }

    @Test
    public void exactTwoHourCourseDoesNotChangeTimetableGeometry() {
        ScheduleTimeAxis.Axis baseline = ScheduleTimeAxis.create(
                Collections.emptyList(), settings(), 11, 100f);
        ScheduleTimeAxis.Axis withExactCourse = ScheduleTimeAxis.create(
                Collections.singletonList(exactCourse(0, 10 * 60, 12 * 60)),
                settings(), 11, 100f);

        assertEquals(baseline.startMinute, withExactCourse.startMinute);
        assertEquals(baseline.endMinute, withExactCourse.endMinute);
        assertEquals(baseline.contentHeight(), withExactCourse.contentHeight());
        assertEquals(180, withExactCourse.heightForRange(10 * 60, 12 * 60));
    }

    @Test
    public void exactOneHourCourseUsesOneClassRowOfHeight() {
        ScheduleTimeAxis.Axis axis = ScheduleTimeAxis.create(
                Collections.singletonList(exactCourse(0, 17 * 60, 18 * 60)),
                xautSettings(), 13, 100f);

        assertEquals(100, axis.heightForRange(17 * 60, 18 * 60));
    }

    @Test
    public void lunchBreakCanBeCollapsedWithoutMovingCourseMinutes() {
        ScheduleTimeAxis.Axis expanded = ScheduleTimeAxis.create(
                Collections.emptyList(), settings(), 11, 100f, false);
        ScheduleTimeAxis.Axis collapsed = ScheduleTimeAxis.create(
                Collections.emptyList(), settings(), 11, 100f, true);

        assertTrue(collapsed.isLunchBreakCollapsed());
        assertEquals(12 * 60 + 10, collapsed.collapsedStartMinute);
        assertEquals(14 * 60 + 30, collapsed.collapsedEndMinute);
        assertTrue(collapsed.contentHeight() < expanded.contentHeight());
        assertEquals(collapsed.yForMinute(12 * 60 + 10),
                collapsed.yForMinute(14 * 60 + 30));
        assertEquals(15 * 60, collapsed.minuteForY(collapsed.yForMinute(15 * 60)));
    }

    @Test
    public void lunchCourseKeepsLunchBreakExpanded() {
        ScheduleTimeAxis.Axis axis = ScheduleTimeAxis.create(
                Collections.singletonList(exactCourse(0, 13 * 60, 13 * 60 + 30)),
                settings(), 11, 100f, true);

        assertTrue(!axis.isLunchBreakCollapsed());
    }

    @Test
    public void unusedMiddaySectionsCanBeCollapsed() {
        ScheduleTimeAxis.Axis axis = ScheduleTimeAxis.create(
                Collections.emptyList(), xautSettings(), 13, 100f, true);

        assertTrue(axis.isLunchBreakCollapsed());
        assertEquals(12 * 60, axis.collapsedStartMinute);
        assertEquals(14 * 60 + 10, axis.collapsedEndMinute);
    }

    private Course exactCourse(int day, int startMinute, int endMinute) {
        return new Course(day, 0, 0, "课程", "1-20周", "", "", "", "", "",
                CourseType.LECTURE, "", "", CourseTimeMode.CLOCK,
                startMinute, endMinute);
    }

    private CourseTimeResolver.Settings settings() {
        return new CourseTimeResolver.Settings(
                "08:00", 50, 10, 30, "14:30", "16:35", "");
    }

    private CourseTimeResolver.Settings xautSettings() {
        return new CourseTimeResolver.Settings(
                "08:00", 50, 10, 30, "14:30", "16:35",
                "1 08:00-08:50\n"
                        + "2 09:00-09:50\n"
                        + "3 10:10-11:00\n"
                        + "4 11:10-12:00\n"
                        + "5 12:10-13:00\n"
                        + "6 13:10-14:00\n"
                        + "7 14:10-15:00");
    }
}
