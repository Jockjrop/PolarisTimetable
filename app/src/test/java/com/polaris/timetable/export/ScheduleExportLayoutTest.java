package com.polaris.timetable.export;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseType;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScheduleExportLayoutTest {
    @Test
    public void create_filtersWeekAndHiddenWeekend() {
        Course mondayOdd = course(0, 1, 2, "单周课", "1-8周 单周");
        Course saturday = course(5, 1, 2, "周六课", "1-8周");

        ScheduleExportLayout.Result result = ScheduleExportLayout.create(
                Arrays.asList(mondayOdd, saturday), 2, false, false, 11);

        assertFalse(result.hasContent());
        assertEquals(5, result.visibleDays.size());
    }

    @Test
    public void create_assignsSeparateLanesToConflicts() {
        Course first = course(0, 1, 4, "高等数学", "1-20周");
        Course second = course(0, 2, 3, "大学物理", "1-20周");
        Course later = course(0, 5, 6, "大学英语", "1-20周");

        ScheduleExportLayout.Result result = ScheduleExportLayout.create(
                Arrays.asList(first, second, later), 3, false, false, 11);

        assertEquals(3, result.items.size());
        assertEquals(0, result.items.get(0).lane);
        assertEquals(2, result.items.get(0).laneCount);
        assertEquals(1, result.items.get(1).lane);
        assertEquals(2, result.items.get(1).laneCount);
        assertEquals(0, result.items.get(2).lane);
        assertEquals(1, result.items.get(2).laneCount);
    }

    @Test
    public void create_keepsActiveBannerCourse() {
        Course practice = new Course(-1, -1, -1, "金工实习", "3周", "工程训练中心",
                "教师", "", "", "", CourseType.PRACTICE);

        ScheduleExportLayout.Result result = ScheduleExportLayout.create(
                Collections.singletonList(practice), 3, false, false, 11);

        assertTrue(result.hasContent());
        assertEquals(1, result.bannerCourses.size());
    }

    @Test
    public void createAllWeeks_combinesDifferentWeekRulesInOneGrid() {
        Course odd = course(0, 1, 2, "单周课程", "1-16周 单周");
        Course even = course(0, 1, 2, "双周课程", "1-16周 双周");
        Course practice = new Course(-1, -1, -1, "课程设计", "18周", "实验中心",
                "教师", "", "", "", CourseType.PRACTICE);

        ScheduleExportLayout.Result result = ScheduleExportLayout.createAllWeeks(
                Arrays.asList(odd, even, practice), false, false, 11);

        assertEquals(2, result.items.size());
        assertEquals(2, result.items.get(0).laneCount);
        assertEquals(2, result.items.get(1).laneCount);
        assertEquals(1, result.bannerCourses.size());
    }

    private Course course(int day, int start, int end, String name, String weeks) {
        return new Course(day, start, end, name, weeks, "A101", "教师", "");
    }
}
