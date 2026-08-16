package com.polaris.timetable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CourseBatchEditTest {
    private static final String FIRST = "11111111-1111-4111-8111-111111111111";
    private static final String SECOND = "22222222-2222-4222-8222-222222222222";
    private static final String THIRD = "33333333-3333-4333-8333-333333333333";

    private static StructuredCourse course(String id, String name, String teacher,
                                           String color, CourseType type) {
        CourseMeeting meeting = new CourseMeeting(
                "meeting-" + id, 0, 1, 2,
                new WeekRule(WeekRule.Type.RANGE, 1, 16,
                        Collections.emptyList(), "1-16周"),
                "A101", teacher, "raw");
        return new StructuredCourse(id, name, teacher, "A101",
                Collections.singletonList(meeting), "raw", "3", color, type);
    }

    @Test
    public void batchUpdateTeacher_updatesSelectedCoursesAndTheirMeetings() {
        List<StructuredCourse> courses = new ArrayList<>(Arrays.asList(
                course(FIRST, "高数", "张老师", "#4FA4F3", CourseType.LECTURE),
                course(SECOND, "英语", "李老师", "#5AC8A6", CourseType.LECTURE),
                course(THIRD, "体育", "王老师", "#F3A14F", CourseType.LECTURE)));
        Set<String> selected = new HashSet<>(Arrays.asList(FIRST, THIRD));

        int updated = CourseEditManager.batchUpdateTeacher(courses, selected, "赵老师");

        assertEquals(2, updated);
        assertEquals("赵老师", courses.get(0).teacher);
        assertEquals("赵老师", courses.get(0).meetings.get(0).teacher);
        assertEquals("李老师", courses.get(1).teacher);
        assertEquals("李老师", courses.get(1).meetings.get(0).teacher);
        assertEquals("赵老师", courses.get(2).teacher);
        // Stable ids and meeting ids are preserved.
        assertEquals(FIRST, courses.get(0).id);
        assertEquals("meeting-" + FIRST, courses.get(0).meetings.get(0).id);
    }

    @Test
    public void batchUpdateColor_updatesOnlySelectedCourses() {
        List<StructuredCourse> courses = new ArrayList<>(Arrays.asList(
                course(FIRST, "高数", "张老师", "#4FA4F3", CourseType.LECTURE),
                course(SECOND, "英语", "李老师", "#5AC8A6", CourseType.LECTURE)));
        Set<String> selected = Collections.singleton(SECOND);

        int updated = CourseEditManager.batchUpdateColor(courses, selected, "#E56A6A");

        assertEquals(1, updated);
        assertEquals("#4FA4F3", courses.get(0).color);
        assertEquals("#E56A6A", courses.get(1).color);
        assertEquals(SECOND, courses.get(1).id);
    }

    @Test
    public void batchUpdates_ignoreEmptySelectionAndNullInput() {
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(
                course(FIRST, "高数", "张老师", "#4FA4F3", CourseType.LECTURE)));
        String originalTeacher = courses.get(0).teacher;

        assertEquals(0, CourseEditManager.batchUpdateTeacher(
                courses, Collections.emptySet(), "赵老师"));
        assertEquals(0, CourseEditManager.batchUpdateColor(
                courses, null, "#E56A6A"));
        assertEquals(0, CourseEditManager.batchUpdateTeacher(
                null, Collections.singleton(FIRST), "赵老师"));
        assertEquals(originalTeacher, courses.get(0).teacher);
        assertEquals("#4FA4F3", courses.get(0).color);
        assertTrue(courses.size() == 1);
    }
}
