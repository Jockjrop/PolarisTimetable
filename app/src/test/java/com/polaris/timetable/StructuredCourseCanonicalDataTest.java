package com.polaris.timetable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class StructuredCourseCanonicalDataTest {
    private static final String FIRST_ID = "11111111-1111-4111-8111-111111111111";
    private static final String SECOND_ID = "22222222-2222-4222-8222-222222222222";

    @Test
    public void threeMeetings_expandToThreeCoursesWithOneStableIdAndAllFields() {
        StructuredCourse source = threeMeetingCourse(FIRST_ID, "信号与系统");

        List<Course> expanded = new CourseStructureMapper().toLegacyCourses(
                Collections.singletonList(source));

        assertEquals(3, expanded.size());
        for (Course course : expanded) {
            assertEquals(FIRST_ID, course.structuredCourseId);
            assertEquals("信号与系统", course.name);
            assertEquals("3.5", course.credit);
            assertEquals("#4FA4F3", course.color);
            assertEquals(CourseType.EXPERIMENT, course.courseType);
        }
        assertMeeting(expanded.get(0), 0, 1, 2, "张老师", "A101", "1-16周");
        assertMeeting(expanded.get(1), 2, 3, 4, "李老师", "B202", "2-16周(双)");
        assertMeeting(expanded.get(2), 4, 5, 6, "王老师", "C303", "5、7、9周");
    }

    @Test
    public void updateCourseFields_keepsUuidAndAllThreeMeetings() {
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(
                threeMeetingCourse(FIRST_ID, "信号与系统")));

        assertTrue(CourseEditManager.updateCourseFields(
                courses, FIRST_ID, "信号与系统（新版）", "默认教师2", "默认地点2",
                "4.0", "#36B889", CourseType.LECTURE));

        StructuredCourse edited = courses.get(0);
        assertEquals(FIRST_ID, edited.id);
        assertEquals("信号与系统（新版）", edited.name);
        assertEquals("默认教师2", edited.teacher);
        assertEquals("默认地点2", edited.defaultLocation);
        assertEquals("4.0", edited.credit);
        assertEquals("#36B889", edited.color);
        assertEquals(CourseType.LECTURE, edited.courseType);
        assertEquals(3, edited.meetings.size());
        for (Course expanded : new CourseStructureMapper().toLegacyCourses(courses)) {
            assertEquals(FIRST_ID, expanded.structuredCourseId);
            assertEquals("信号与系统（新版）", expanded.name);
        }
    }

    @Test
    public void updateOneMeeting_doesNotAffectItsSiblings() {
        StructuredCourse original = threeMeetingCourse(FIRST_ID, "信号与系统");
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(original));
        CourseMeeting replacement = meeting(
                1, 7, 8, WeekRule.Type.ODD, 1, 15, Collections.emptyList(),
                "D404", "赵老师", "1-15周(单)");

        assertTrue(CourseEditManager.updateMeeting(courses, FIRST_ID, 1, replacement));

        StructuredCourse edited = courses.get(0);
        assertEquals(FIRST_ID, edited.id);
        assertEquals(original.meetings.get(0), edited.meetings.get(0));
        assertEquals(replacement, edited.meetings.get(1));
        assertEquals(original.meetings.get(2), edited.meetings.get(2));
    }

    @Test
    public void deleteOneMeeting_keepsCourseAndOtherMeetings() {
        StructuredCourse source = threeMeetingCourse(FIRST_ID, "信号与系统");
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(source));
        Course target = new CourseStructureMapper().toLegacyCourses(courses).get(1);

        int deleted = CourseDeletionManager.deleteStructured(
                courses, target, CourseDeletionScope.CURRENT_MEETING, 2, 20);

        assertEquals(1, deleted);
        assertEquals(1, courses.size());
        assertEquals(FIRST_ID, courses.get(0).id);
        assertEquals(2, courses.get(0).meetings.size());
        assertEquals(0, courses.get(0).meetings.get(0).day);
        assertEquals(4, courses.get(0).meetings.get(1).day);
    }

    @Test
    public void deleteCurrentWeek_changesOnlyTheSelectedMeetingWeekRule() {
        StructuredCourse source = threeMeetingCourse(FIRST_ID, "信号与系统");
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(source));
        Course target = new CourseStructureMapper().toLegacyCourses(courses).get(0);

        assertEquals(1, CourseDeletionManager.deleteStructured(
                courses, target, CourseDeletionScope.CURRENT_WEEK, 3, 20));

        StructuredCourse result = courses.get(0);
        assertEquals(FIRST_ID, result.id);
        assertEquals(3, result.meetings.size());
        assertFalse(result.meetings.get(0).weekRule.containsWeek(3));
        assertTrue(result.meetings.get(0).weekRule.containsWeek(2));
        assertEquals(source.meetings.get(1), result.meetings.get(1));
        assertEquals(source.meetings.get(2), result.meetings.get(2));
    }

    @Test
    public void deleteStructuredCourse_removesEveryExpandedCourse() {
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(
                threeMeetingCourse(FIRST_ID, "信号与系统")));
        Course target = new CourseStructureMapper().toLegacyCourses(courses).get(0);

        assertEquals(1, CourseDeletionManager.deleteStructured(
                courses, target, CourseDeletionScope.ALL_MEETINGS, 1, 20));

        assertTrue(courses.isEmpty());
        assertTrue(new CourseStructureMapper().toLegacyCourses(courses).isEmpty());
    }

    @Test
    public void sameNameDifferentUuids_editAndDeleteNeverCrossCourseBoundary() {
        StructuredCourse first = threeMeetingCourse(FIRST_ID, "同名课程");
        StructuredCourse second = threeMeetingCourse(SECOND_ID, "同名课程");
        List<StructuredCourse> courses = new ArrayList<>(Arrays.asList(first, second));

        assertTrue(CourseEditManager.updateCourseFields(
                courses, FIRST_ID, "仅修改第一门", first.teacher, first.defaultLocation,
                first.credit, "#E86464", first.courseType));
        assertEquals("仅修改第一门", courses.get(0).name);
        assertEquals("同名课程", courses.get(1).name);

        Course secondTarget = new CourseStructureMapper().toLegacyCourses(
                Collections.singletonList(courses.get(1))).get(0);
        assertEquals(1, CourseDeletionManager.deleteStructured(
                courses, secondTarget, CourseDeletionScope.ALL_MEETINGS, 1, 20));
        assertEquals(1, courses.size());
        assertEquals(FIRST_ID, courses.get(0).id);
        assertFalse(SECOND_ID.equals(courses.get(0).id));
    }

    @Test
    public void editorPayload_updatesOneMeetingWithoutChangingStructuredUuid() {
        StructuredCourse source = threeMeetingCourse(FIRST_ID, "信号与系统");
        List<StructuredCourse> courses = new ArrayList<>(Collections.singletonList(source));
        CourseStructureMapper mapper = new CourseStructureMapper();
        Course selected = mapper.toLegacyCourses(courses).get(1);
        Course edited = new Course(
                3, 8, 9, "新课程名", "3-12周", "新地点", "新教师", "",
                "5.0", "#956BD6", CourseType.LECTURE, FIRST_ID);

        assertTrue(CourseEditManager.applyStructuredEdit(courses, selected, edited));

        StructuredCourse result = courses.get(0);
        assertEquals(FIRST_ID, result.id);
        assertEquals("新课程名", result.name);
        assertEquals(3, result.meetings.size());
        assertEquals(source.meetings.get(0), result.meetings.get(0));
        assertEquals(source.meetings.get(2), result.meetings.get(2));
        assertEquals(3, result.meetings.get(1).day);
        assertEquals(8, result.meetings.get(1).startSection);
        assertEquals("新教师", result.meetings.get(1).teacher);
        assertEquals("新地点", result.meetings.get(1).location);
    }

    private StructuredCourse threeMeetingCourse(String id, String name) {
        List<CourseMeeting> meetings = Arrays.asList(
                meeting(0, 1, 2, WeekRule.Type.RANGE, 1, 16,
                        Collections.emptyList(), "A101", "张老师", "1-16周"),
                meeting(2, 3, 4, WeekRule.Type.EVEN, 2, 16,
                        Collections.emptyList(), "B202", "李老师", "2-16周(双)"),
                meeting(4, 5, 6, WeekRule.Type.RANGE, 5, 9,
                        Arrays.asList(5, 7, 9), "C303", "王老师", "5、7、9周"));
        return new StructuredCourse(
                id, name, "默认教师", "默认地点", meetings, "raw-course",
                "3.5", "#4FA4F3", CourseType.EXPERIMENT);
    }

    private CourseMeeting meeting(
            int day, int start, int end, WeekRule.Type type, int startWeek, int endWeek,
            List<Integer> explicitWeeks, String location, String teacher, String rawWeeks) {
        return new CourseMeeting(
                day, start, end,
                new WeekRule(type, startWeek, endWeek, explicitWeeks, rawWeeks),
                location, teacher, "raw-meeting-" + day);
    }

    private void assertMeeting(
            Course course, int day, int start, int end,
            String teacher, String location, String weeks) {
        assertEquals(day, course.day);
        assertEquals(start, course.startSection);
        assertEquals(end, course.endSection);
        assertEquals(teacher, course.teacher);
        assertEquals(location, course.location);
        assertEquals(weeks, course.weeks);
    }
}
