package com.polaris.timetable;

import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CourseEditManagerTest {
    private static final String SHARED_ID = "d1c6f52b-d9a3-40bb-a0c0-750804fdb7b1";
    private static final String OTHER_ID = "a13ac5f2-41de-4de4-ac73-38fe4037fbf1";

    @Test
    public void editMeeting_updatesCourseLevelFieldsForEveryMeetingWithSameStableId() {
        Course firstMeeting = course(
                0, 1, 2, "操作系统", "张老师", "A101", "#OLD", SHARED_ID);
        Course selectedMeeting = course(
                2, 5, 6, "操作系统", "张老师", "B202", "#OLD", SHARED_ID);
        Course sameNameDifferentCourse = course(
                4, 3, 4, "操作系统", "王老师", "C303", "#OTHER", OTHER_ID);
        List<Course> courses = new ArrayList<>(Arrays.asList(
                firstMeeting, selectedMeeting, sameNameDifferentCourse));
        Course edited = new Course(
                2, 5, 6, "操作系统（新版）", "1-16周", "B205", "张老师", "",
                "3.5", "#NEW", CourseType.LECTURE, "");

        assertTrue(CourseEditManager.applyEdit(courses, selectedMeeting, edited));

        Course updatedFirstMeeting = courses.get(0);
        assertEquals("操作系统（新版）", updatedFirstMeeting.name);
        assertEquals("#NEW", updatedFirstMeeting.color);
        assertEquals("3.5", updatedFirstMeeting.credit);
        assertEquals(0, updatedFirstMeeting.day);
        assertEquals(1, updatedFirstMeeting.startSection);
        assertEquals("张老师", updatedFirstMeeting.teacher);
        assertEquals("A101", updatedFirstMeeting.location);
        assertEquals("#NEW", courses.get(1).color);
        assertEquals("B205", courses.get(1).location);
        assertEquals(SHARED_ID, courses.get(1).structuredCourseId);
        assertEquals("#OTHER", courses.get(2).color);
    }

    @Test
    public void editedColor_survivesStructuredRoundTripUsedByPersistence() {
        Course firstMeeting = course(
                0, 1, 2, "操作系统", "张老师", "A101", "#OLD", SHARED_ID);
        Course selectedMeeting = course(
                2, 5, 6, "操作系统", "张老师", "B202", "#OLD", SHARED_ID);
        List<Course> courses = new ArrayList<>(Arrays.asList(firstMeeting, selectedMeeting));
        Course edited = course(
                2, 5, 6, "操作系统", "张老师", "B202", "#NEW", SHARED_ID);

        CourseEditManager.applyEdit(courses, selectedMeeting, edited);
        CourseStructureMapper mapper = new CourseStructureMapper();
        List<Course> reloaded = mapper.toLegacyCourses(mapper.fromLegacyCourses(courses));

        assertEquals(2, reloaded.size());
        assertEquals("#NEW", reloaded.get(0).color);
        assertEquals("#NEW", reloaded.get(1).color);
        assertEquals(SHARED_ID, reloaded.get(0).structuredCourseId);
        assertEquals(SHARED_ID, reloaded.get(1).structuredCourseId);
    }

    @Test
    public void editColor_splitsDifferentTeachersThatPreviouslySharedStableId() {
        Course firstTeacher = course(
                0, 1, 2, "大学英语", "张老师", "A101", "#ZHANG", SHARED_ID);
        Course selected = course(
                2, 3, 4, "大学英语", "李老师", "B202", "#OLD", SHARED_ID);
        Course sameTeacherOtherMeeting = course(
                4, 5, 6, "大学英语", "李老师", "C303", "#OLD", SHARED_ID);
        List<Course> courses = new ArrayList<>(Arrays.asList(
                firstTeacher, selected, sameTeacherOtherMeeting));
        Course edited = course(
                2, 3, 4, "大学英语", "李老师", "B202", "#LI", SHARED_ID);

        CourseEditManager.applyEdit(courses, selected, edited);

        assertEquals("#ZHANG", courses.get(0).color);
        assertEquals(SHARED_ID, courses.get(0).structuredCourseId);
        assertEquals("#LI", courses.get(1).color);
        assertEquals("#LI", courses.get(2).color);
        assertTrue(!SHARED_ID.equals(courses.get(1).structuredCourseId));
        assertEquals(courses.get(1).structuredCourseId, courses.get(2).structuredCourseId);

        CourseStructureMapper mapper = new CourseStructureMapper();
        List<Course> reloaded = mapper.toLegacyCourses(mapper.fromLegacyCourses(courses));
        assertEquals(3, reloaded.size());
        assertEquals("#ZHANG", reloaded.get(0).color);
        assertEquals("#LI", reloaded.get(1).color);
        assertEquals("#LI", reloaded.get(2).color);
    }

    @Test
    public void changingTeacher_detachesEditedMeetingFromOriginalCourseId() {
        Course firstMeeting = course(
                0, 1, 2, "大学英语", "张老师", "A101", "#OLD", SHARED_ID);
        Course selected = course(
                2, 3, 4, "大学英语", "张老师", "B202", "#OLD", SHARED_ID);
        List<Course> courses = new ArrayList<>(Arrays.asList(firstMeeting, selected));
        Course edited = course(
                2, 3, 4, "大学英语", "李老师", "B202", "#LI", SHARED_ID);

        CourseEditManager.applyEdit(courses, selected, edited);

        assertEquals("#OLD", courses.get(0).color);
        assertEquals(SHARED_ID, courses.get(0).structuredCourseId);
        assertEquals("李老师", courses.get(1).teacher);
        assertEquals("#LI", courses.get(1).color);
        assertTrue(!SHARED_ID.equals(courses.get(1).structuredCourseId));
    }

    private Course course(
            int day, int start, int end, String name, String teacher, String location,
            String color, String stableId) {
        return new Course(
                day, start, end, name, "1-16周", location, teacher, "", "2.0", color,
                CourseType.LECTURE, stableId);
    }
}
