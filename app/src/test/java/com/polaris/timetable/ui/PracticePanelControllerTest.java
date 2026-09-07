package com.polaris.timetable.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseType;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PracticePanelControllerTest {

    private static Course course(int day, int start, int end, String weeks,
                                 CourseType type) {
        return new Course(day, start, end, "课程", weeks, "", "", "", "", "", type);
    }

    @Test
    public void forPanel_returnsAllPracticeAndBannerOnlyCourses() {
        List<Course> source = Arrays.asList(
                course(0, 1, 2, "1-8周", CourseType.LECTURE),
                course(0, 3, 4, "1-8周", CourseType.PRACTICE),
                course(-1, 0, 0, "1-20周", CourseType.PRACTICE), // 集中实践 banner-only
                course(-1, 0, 0, "1-20周", CourseType.EXPERIMENT)); // 实验 banner-only

        List<Course> result = PracticePanelController.forPanel(source);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(c ->
                c.courseType == CourseType.PRACTICE || c.courseType == CourseType.EXPERIMENT));
    }

    @Test
    public void forCurrentWeek_filtersByActiveWeek() {
        Course practiceInRange = course(0, 1, 2, "1-8周", CourseType.PRACTICE);
        Course practiceOutOfRange = course(0, 1, 2, "10-16周", CourseType.PRACTICE);
        Course lecture = course(0, 3, 4, "1-8周", CourseType.LECTURE);
        Course banner = course(-1, 0, 0, "1-8周", CourseType.PRACTICE);

        List<Course> result = PracticePanelController.forCurrentWeek(
                Arrays.asList(practiceInRange, practiceOutOfRange, lecture, banner), 3);

        assertEquals(2, result.size());
        assertTrue(result.contains(practiceInRange));
        assertTrue(result.contains(banner));
        assertFalse(result.contains(practiceOutOfRange));
        assertFalse(result.contains(lecture));
    }
}
