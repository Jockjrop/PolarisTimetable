package com.polaris.timetable.model;

import static org.junit.Assert.assertEquals;

import com.polaris.timetable.Course;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CourseStructureMapperTest {
    @Test
    public void roundTrip_preservesMultipleMeetingsAndDisplayFields() {
        Course first = new Course(
                0, 1, 2, "数字信号处理", "1-16周(单)", "A101", "张老师", "raw-1", "3.0", "#4FA4F3");
        Course second = new Course(
                2, 3, 4, "数字信号处理", "2-16周(双)", "B202", "张老师", "raw-2", "3.0", "#4FA4F3");
        CourseStructureMapper mapper = new CourseStructureMapper();

        List<StructuredCourse> structured = mapper.fromLegacyCourses(Arrays.asList(first, second));

        assertEquals(1, structured.size());
        assertEquals(2, structured.get(0).meetings.size());
        assertEquals("3.0", structured.get(0).credit);
        assertEquals("#4FA4F3", structured.get(0).color);

        List<Course> restored = mapper.toLegacyCourses(structured);
        assertEquals(2, restored.size());
        assertCourse(first, restored.get(0));
        assertCourse(second, restored.get(1));
    }

    @Test
    public void fromLegacy_doesNotMergeDifferentCustomColors() {
        Course blue = new Course(0, 1, 2, "高等数学", "1-20周", "", "李老师", "", "5", "#4FA4F3");
        Course green = new Course(2, 1, 2, "高等数学", "1-20周", "", "李老师", "", "5", "#36B889");

        List<StructuredCourse> structured = new CourseStructureMapper()
                .fromLegacyCourses(Arrays.asList(blue, green));

        assertEquals(2, structured.size());
    }

    private void assertCourse(Course expected, Course actual) {
        assertEquals(expected.day, actual.day);
        assertEquals(expected.startSection, actual.startSection);
        assertEquals(expected.endSection, actual.endSection);
        assertEquals(expected.name, actual.name);
        assertEquals(expected.weeks, actual.weeks);
        assertEquals(expected.location, actual.location);
        assertEquals(expected.teacher, actual.teacher);
        assertEquals(expected.raw, actual.raw);
        assertEquals(expected.credit, actual.credit);
        assertEquals(expected.color, actual.color);
    }
}
