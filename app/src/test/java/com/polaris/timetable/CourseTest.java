package com.polaris.timetable;

import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.CourseTimeMode;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CourseTest {
    @Test
    public void practiceWithoutFixedTimeUsesBannerOnly() {
        Course course = new Course(-1, 0, 0, "工程训练", "1-2周", "", "", "",
                "", "", CourseType.PRACTICE);

        assertFalse(course.hasFixedTime());
        assertTrue(course.isPracticeBannerOnly());
        assertTrue(course.isBannerOnlyCourse());
    }

    @Test
    public void fixedPracticeStillHasGridTime() {
        Course course = new Course(2, 3, 4, "工程训练", "1-2周", "", "", "",
                "", "", CourseType.PRACTICE);

        assertTrue(course.hasFixedTime());
        assertFalse(course.isPracticeBannerOnly());
        assertFalse(course.isBannerOnlyCourse());
    }

    @Test
    public void experimentWithoutFixedTimeAlsoUsesBanner() {
        Course course = new Course(-1, 0, 0, "智能计算实验", "9-16周", "", "", "",
                "", "", CourseType.EXPERIMENT);

        assertTrue(course.isBannerOnlyCourse());
        assertFalse(course.isPracticeBannerOnly());
    }

    @Test
    public void exactClockPracticeIsScheduledInsteadOfBannerOnly() {
        Course course = new Course(2, 0, 0, "工程训练", "1-2周", "", "", "",
                "", "", CourseType.PRACTICE, "", "", CourseTimeMode.CLOCK,
                9 * 60 + 10, 10 * 60 + 35);

        assertFalse(course.hasFixedTime());
        assertTrue(course.hasExactTime());
        assertTrue(course.hasScheduledTime());
        assertFalse(course.isPracticeBannerOnly());
        assertFalse(course.isBannerOnlyCourse());
    }
}
