package com.polaris.timetable;

import org.junit.Test;

import com.polaris.timetable.model.CourseType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CourseDeletionManagerTest {
    @Test
    public void currentWeek_removesOnlySelectedOccurrence() {
        Course target = course(0, 1, 2, "高等数学", "1-4周");
        List<Course> courses = new ArrayList<>(Arrays.asList(target));

        int deleted = CourseDeletionManager.delete(
                courses, target, CourseDeletionScope.CURRENT_WEEK, 3, 20);

        assertEquals(1, deleted);
        assertEquals(1, courses.size());
        assertEquals("1-2、4周", courses.get(0).weeks);
    }

    @Test
    public void currentWeek_respectsOddWeekRule() {
        Course target = course(0, 1, 2, "高等数学", "1-7周(单)");
        List<Course> courses = new ArrayList<>(Arrays.asList(target));

        int deleted = CourseDeletionManager.delete(
                courses, target, CourseDeletionScope.CURRENT_WEEK, 4, 20);

        assertEquals(0, deleted);
        assertEquals(1, courses.size());
    }

    @Test
    public void currentWeek_usesUnifiedExplicitWeekRule() {
        Course target = course(0, 1, 2, "高等数学", "2、5-6周");
        List<Course> courses = new ArrayList<>(Arrays.asList(target));

        int deleted = CourseDeletionManager.delete(
                courses, target, CourseDeletionScope.CURRENT_WEEK, 2, 20);

        assertEquals(1, deleted);
        assertEquals(1, courses.size());
        assertEquals("5-6周", courses.get(0).weeks);
    }

    @Test
    public void currentWeek_keepsOddWeeksInOneCompactCourse() {
        Course target = course(0, 1, 2, "高等数学", "1-7周 单周");
        List<Course> courses = new ArrayList<>(Arrays.asList(target));

        int deleted = CourseDeletionManager.delete(
                courses, target, CourseDeletionScope.CURRENT_WEEK, 3, 20);

        assertEquals(1, deleted);
        assertEquals(1, courses.size());
        assertEquals("1、5、7周", courses.get(0).weeks);
    }

    @Test
    public void compactSplitWeekEntries_repairsPreviouslyExpandedCourses() {
        List<Course> courses = new ArrayList<>(Arrays.asList(
                course(0, 1, 2, "高等数学", "1周"),
                course(0, 1, 2, "高等数学", "2周"),
                course(0, 1, 2, "高等数学", "4周")));

        boolean changed = CourseDeletionManager.compactSplitWeekEntries(courses);

        assertEquals(true, changed);
        assertEquals(1, courses.size());
        assertEquals("1-2、4周", courses.get(0).weeks);
    }

    @Test
    public void compactSplitWeekEntries_keepsDifferentLocationsSeparate() {
        Course first = course(0, 1, 2, "高等数学", "1周");
        Course second = new Course(
                0, 1, 2, "高等数学", "2周", "另一教室", "教师", "");
        List<Course> courses = new ArrayList<>(Arrays.asList(first, second));

        boolean changed = CourseDeletionManager.compactSplitWeekEntries(courses);

        assertEquals(false, changed);
        assertEquals(2, courses.size());
    }

    @Test
    public void currentMeeting_removesSameSlotButKeepsOtherSlotsAndCourses() {
        Course target = course(0, 1, 2, "高等数学", "1-10周");
        Course sameSlot = course(0, 1, 2, "高等数学", "11-20周");
        Course otherSlot = course(2, 3, 4, "高等数学", "1-20周");
        Course otherCourse = course(0, 1, 2, "大学英语", "1-20周");
        List<Course> courses = new ArrayList<>(
                Arrays.asList(target, sameSlot, otherSlot, otherCourse));

        int deleted = CourseDeletionManager.delete(
                courses, target, CourseDeletionScope.CURRENT_MEETING, 3, 20);

        assertEquals(2, deleted);
        assertEquals(Arrays.asList(otherSlot, otherCourse), courses);
    }

    @Test
    public void allMeetings_removesEverySlotWithSameCourseName() {
        Course target = course(0, 1, 2, "高等数学", "1-20周");
        Course otherSlot = course(2, 3, 4, "高等数学", "1-20周");
        Course otherCourse = course(0, 1, 2, "大学英语", "1-20周");
        List<Course> courses = new ArrayList<>(Arrays.asList(target, otherSlot, otherCourse));

        int deleted = CourseDeletionManager.delete(
                courses, target, CourseDeletionScope.ALL_MEETINGS, 3, 20);

        assertEquals(2, deleted);
        assertEquals(Arrays.asList(otherCourse), courses);
    }

    @Test
    public void allMeetings_legacyCoursesKeepSameNameFromDifferentTeacher() {
        Course target = courseWithTeacher(0, 1, 2, "大学英语", "张老师", "1-20周");
        Course targetOtherSlot = courseWithTeacher(2, 3, 4, "大学英语", "张老师", "1-20周");
        Course differentTeacher = courseWithTeacher(4, 5, 6, "大学英语", "李老师", "1-20周");
        List<Course> courses = new ArrayList<>(
                Arrays.asList(target, targetOtherSlot, differentTeacher));

        int deleted = CourseDeletionManager.delete(
                courses, target, CourseDeletionScope.ALL_MEETINGS, 3, 20);

        assertEquals(2, deleted);
        assertEquals(Arrays.asList(differentTeacher), courses);
    }

    @Test
    public void allMeetings_stableIdRemovesOnlyTheSelectedStructuredCourse() {
        String targetId = "a5ba4efc-f166-48da-9f8e-97b73ef1c943";
        Course target = courseWithId(0, 1, 2, "大学英语", "张老师", targetId);
        Course targetOtherSlot = courseWithId(2, 3, 4, "大学英语", "张老师", targetId);
        Course differentCourse = courseWithId(
                4, 5, 6, "大学英语", "张老师",
                "9d83d73d-f12a-41df-95fb-55b1dc0cc7de");
        List<Course> courses = new ArrayList<>(
                Arrays.asList(target, targetOtherSlot, differentCourse));

        int deleted = CourseDeletionManager.delete(
                courses, target, CourseDeletionScope.ALL_MEETINGS, 3, 20);

        assertEquals(2, deleted);
        assertEquals(Arrays.asList(differentCourse), courses);
    }

    @Test
    public void allMeetings_keepsDifferentTeacherEvenWhenStableIdWasPreviouslyShared() {
        String sharedId = "f3585c91-079d-44b9-a767-82d3fbdb1067";
        Course target = courseWithId(0, 1, 2, "大学英语", "张老师", sharedId);
        Course targetOtherSlot = courseWithId(2, 3, 4, "大学英语", "张老师", sharedId);
        Course differentTeacher = courseWithId(4, 5, 6, "大学英语", "李老师", sharedId);
        List<Course> courses = new ArrayList<>(
                Arrays.asList(target, targetOtherSlot, differentTeacher));

        int deleted = CourseDeletionManager.delete(
                courses, target, CourseDeletionScope.ALL_MEETINGS, 3, 20);

        assertEquals(2, deleted);
        assertEquals(Arrays.asList(differentTeacher), courses);
    }

    private Course course(int day, int start, int end, String name, String weeks) {
        return new Course(day, start, end, name, weeks, "教室", "教师", "");
    }

    private Course courseWithTeacher(
            int day, int start, int end, String name, String teacher, String weeks) {
        return new Course(day, start, end, name, weeks, "教室", teacher, "");
    }

    private Course courseWithId(
            int day, int start, int end, String name, String teacher, String stableId) {
        return new Course(
                day, start, end, name, "1-20周", "教室", teacher, "", "", "",
                CourseType.LECTURE, stableId);
    }
}
