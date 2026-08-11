package com.polaris.timetable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableMeetingId;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CourseMeetingIdentityTest {
    private static final String COURSE_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    public void newMeeting_automaticallyGetsValidUuid() {
        assertTrue(StableMeetingId.isValid(meeting().id));
    }

    @Test
    public void changingDay_keepsMeetingId() {
        CourseMeeting original = meeting();
        CourseMeeting edited = copy(original, 4, original.startSection, original.endSection,
                original.weekRule, original.teacher, original.location);
        assertEditKeepsId(original, edited);
    }

    @Test
    public void changingSections_keepsMeetingId() {
        CourseMeeting original = meeting();
        CourseMeeting edited = copy(original, original.day, 7, 8,
                original.weekRule, original.teacher, original.location);
        assertEditKeepsId(original, edited);
    }

    @Test
    public void changingWeekRule_keepsMeetingId() {
        CourseMeeting original = meeting();
        WeekRule changedWeeks = new WeekRule(
                WeekRule.Type.EVEN, 2, 16, Collections.emptyList(), "2-16周(双)");
        CourseMeeting edited = copy(original, original.day, original.startSection,
                original.endSection, changedWeeks, original.teacher, original.location);
        assertEditKeepsId(original, edited);
    }

    @Test
    public void changingTeacher_keepsMeetingId() {
        CourseMeeting original = meeting();
        CourseMeeting edited = copy(original, original.day, original.startSection,
                original.endSection, original.weekRule, "新教师", original.location);
        assertEditKeepsId(original, edited);
    }

    @Test
    public void changingLocation_keepsMeetingId() {
        CourseMeeting original = meeting();
        CourseMeeting edited = copy(original, original.day, original.startSection,
                original.endSection, original.weekRule, original.teacher, "新地点");
        assertEditKeepsId(original, edited);
    }

    @Test
    public void changingParentCourseFields_keepsEveryMeetingId() {
        StructuredCourse source = threeMeetingCourse();
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(source));
        List<String> ids = meetingIds(source);

        assertTrue(CourseEditManager.updateCourseFields(
                courses, COURSE_ID, "新课程名", "新默认教师", "新默认地点",
                "5.0", "#36B889", CourseType.LECTURE));

        assertEquals(ids, meetingIds(courses.get(0)));
    }

    @Test
    public void threeMeetings_haveThreeDifferentIds() {
        List<String> ids = meetingIds(threeMeetingCourse());
        Set<String> unique = new HashSet<>(ids);

        assertEquals(3, unique.size());
        for (String id : ids) {
            assertTrue(StableMeetingId.isValid(id));
        }
    }

    @Test
    public void identicalMeetings_stillHaveDifferentIds() {
        CourseMeeting first = meeting();
        CourseMeeting second = meeting();

        assertNotEquals(first.id, second.id);
        assertEquals(first.day, second.day);
        assertEquals(first.startSection, second.startSection);
        assertEquals(first.teacher, second.teacher);
    }

    @Test
    public void deletingOneOfTwoIdenticalMeetings_keepsTheOtherId() {
        CourseMeeting first = meeting();
        CourseMeeting second = meeting();
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(
                course(Arrays.asList(first, second))));
        Course selectedSecond = new CourseStructureMapper().toLegacyCourses(courses).get(1);

        assertEquals(1, CourseDeletionManager.deleteStructured(
                courses, selectedSecond, CourseDeletionScope.CURRENT_MEETING, 1, 20));

        assertEquals(1, courses.get(0).meetings.size());
        assertEquals(first.id, courses.get(0).meetings.get(0).id);
    }

    @Test
    public void editingOneOfTwoIdenticalMeetings_changesOnlySelectedId() {
        CourseMeeting first = meeting();
        CourseMeeting second = meeting();
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(
                course(Arrays.asList(first, second))));
        Course selectedSecond = new CourseStructureMapper().toLegacyCourses(courses).get(1);
        Course edited = new Course(
                selectedSecond.day, selectedSecond.startSection, selectedSecond.endSection,
                selectedSecond.name, selectedSecond.weeks, "新地点", "新教师", selectedSecond.raw,
                selectedSecond.credit, selectedSecond.color, selectedSecond.courseType,
                selectedSecond.structuredCourseId, selectedSecond.meetingId);

        assertTrue(CourseEditManager.applyStructuredEdit(courses, selectedSecond, edited));

        assertEquals("教师", courses.get(0).meetings.get(0).teacher);
        assertEquals("教室", courses.get(0).meetings.get(0).location);
        assertEquals(first.id, courses.get(0).meetings.get(0).id);
        assertEquals("新教师", courses.get(0).meetings.get(1).teacher);
        assertEquals("新地点", courses.get(0).meetings.get(1).location);
        assertEquals(second.id, courses.get(0).meetings.get(1).id);
    }

    @Test
    public void editingMeetingToExactClockTime_preservesIdentityAndMinutes() {
        CourseMeeting meeting = meeting();
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(
                course(Collections.singletonList(meeting))));
        Course original = new CourseStructureMapper().toLegacyCourses(courses).get(0);
        Course edited = new Course(
                0, 0, 0, original.name, original.weeks, original.location, original.teacher,
                original.raw, original.credit, original.color, original.courseType,
                original.structuredCourseId, original.meetingId, CourseTimeMode.CLOCK,
                9 * 60 + 10, 10 * 60 + 35);

        assertTrue(CourseEditManager.applyStructuredEdit(courses, original, edited));

        CourseMeeting restored = courses.get(0).meetings.get(0);
        assertEquals(meeting.id, restored.id);
        assertEquals(CourseTimeMode.CLOCK, restored.timeMode);
        assertEquals(9 * 60 + 10, restored.startMinuteOfDay);
        assertEquals(10 * 60 + 35, restored.endMinuteOfDay);
    }

    @Test
    public void expandedCoursesCarryParentAndMeetingIds() {
        StructuredCourse source = threeMeetingCourse();

        List<Course> expanded = new CourseStructureMapper().toLegacyCourses(
                Collections.singletonList(source));

        assertEquals(3, expanded.size());
        for (int index = 0; index < expanded.size(); index++) {
            assertEquals(COURSE_ID, expanded.get(index).structuredCourseId);
            assertEquals(source.meetings.get(index).id, expanded.get(index).meetingId);
        }
    }

    private void assertEditKeepsId(CourseMeeting original, CourseMeeting edited) {
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(
                course(Collections.singletonList(original))));

        assertTrue(CourseEditManager.updateMeeting(
                courses, COURSE_ID, original.id, edited));
        assertEquals(original.id, courses.get(0).meetings.get(0).id);
    }

    private StructuredCourse threeMeetingCourse() {
        return course(Arrays.asList(
                meeting(),
                new CourseMeeting(
                        2, 3, 4, weeks(), "B202", "李老师", "raw-2"),
                new CourseMeeting(
                        4, 5, 6, weeks(), "C303", "王老师", "raw-3")));
    }

    private StructuredCourse course(List<CourseMeeting> meetings) {
        return new StructuredCourse(
                COURSE_ID, "课程", "默认教师", "默认地点", meetings,
                "raw-course", "3.0", "#4FA4F3", CourseType.EXPERIMENT);
    }

    private CourseMeeting meeting() {
        return new CourseMeeting(0, 1, 2, weeks(), "教室", "教师", "raw");
    }

    private WeekRule weeks() {
        return new WeekRule(
                WeekRule.Type.RANGE, 1, 16, Collections.emptyList(), "1-16周");
    }

    private CourseMeeting copy(
            CourseMeeting source, int day, int start, int end, WeekRule weekRule,
            String teacher, String location) {
        return new CourseMeeting(
                day, start, end, weekRule, location, teacher, source.rawText);
    }

    private List<String> meetingIds(StructuredCourse course) {
        List<String> ids = new ArrayList<>();
        for (CourseMeeting meeting : course.meetings) {
            ids.add(meeting.id);
        }
        return ids;
    }
}
