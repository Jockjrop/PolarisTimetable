package com.polaris.timetable.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CourseTypeTest {
    @Test
    public void storageFallbackKeepsOldCoursesCompatible() {
        assertEquals(CourseType.PRACTICE, CourseType.fromStorage("PRACTICE"));
        assertEquals(CourseType.PRACTICE, CourseType.fromStorage("实践课程"));
        assertEquals(CourseType.LECTURE, CourseType.fromStorage(""));
        assertEquals(CourseType.LECTURE, CourseType.fromStorage("unknown"));
    }
}
