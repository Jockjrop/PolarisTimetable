package com.polaris.timetable.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.Course;
import com.polaris.timetable.time.CourseTimeResolver;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CourseStructureMapperTest {
    @Test
    public void roundTrip_preservesMultipleMeetingsAndDisplayFields() {
        Course first = new Course(
                0, 1, 2, "数字信号处理", "1-16周(单)", "A101", "张老师", "raw-1",
                "3.0", "#4FA4F3", CourseType.EXPERIMENT);
        Course second = new Course(
                2, 3, 4, "数字信号处理", "2-16周(双)", "B202", "张老师", "raw-2",
                "3.0", "#4FA4F3", CourseType.EXPERIMENT);
        CourseStructureMapper mapper = new CourseStructureMapper();

        List<StructuredCourse> structured = mapper.fromLegacyCourses(Arrays.asList(first, second));

        assertEquals(1, structured.size());
        assertEquals(2, structured.get(0).meetings.size());
        assertEquals("3.0", structured.get(0).credit);
        assertEquals("#4FA4F3", structured.get(0).color);
        assertEquals(CourseType.EXPERIMENT, structured.get(0).courseType);

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

    @Test
    public void fromLegacy_doesNotMergeDifferentCourseTypes() {
        Course lecture = new Course(0, 1, 2, "工程训练", "1-2周", "", "", "",
                "", "", CourseType.LECTURE);
        Course practice = new Course(2, 1, 2, "工程训练", "1-2周", "", "", "",
                "", "", CourseType.PRACTICE);

        List<StructuredCourse> structured = new CourseStructureMapper()
                .fromLegacyCourses(Arrays.asList(lecture, practice));

        assertEquals(2, structured.size());
    }

    @Test
    public void roundTrip_preservesBannerOnlyPracticeCourse() {
        Course practice = new Course(-1, 0, 0, "生产实习", "17周", "校外", "张老师", "",
                "2.0", "#36B889", CourseType.PRACTICE);
        CourseStructureMapper mapper = new CourseStructureMapper();

        List<Course> restored = mapper.toLegacyCourses(
                mapper.fromLegacyCourses(Arrays.asList(practice)));

        assertEquals(1, restored.size());
        assertCourse(practice, restored.get(0));
    }

    @Test
    public void roundTrip_preservesWeekSemanticsForSupportedExpressions() {
        List<String> expressions = Arrays.asList(
                "1-8周",
                "1-8周 单周",
                "1-8周（双）",
                "2、5-6周",
                "1,3,5周",
                "1，3，5周",
                "1-2周，5–6周",
                "5周",
                "全周",
                "项目周");
        CourseStructureMapper mapper = new CourseStructureMapper();

        for (String expression : expressions) {
            Course original = new Course(
                    0, 1, 2, "课程-" + expression, expression, "A101", "教师", "");
            Course restored = mapper.toLegacyCourses(
                    mapper.fromLegacyCourses(Arrays.asList(original))).get(0);

            assertEquals(expression, restored.weeks);
            for (int week = 1; week <= 20; week++) {
                assertEquals(expression + " week " + week,
                        CourseTimeResolver.isActiveInWeek(original, week),
                        CourseTimeResolver.isActiveInWeek(restored, week));
            }
        }
    }

    @Test
    public void courseStructuredCourseRoundTripGeneratesIdOnlyOnce() {
        Course original = new Course(
                0, 1, 2, "高等数学", "1-16周", "A101", "李老师", "");
        CourseStructureMapper mapper = new CourseStructureMapper();

        StructuredCourse firstStructured = mapper.fromLegacyCourses(
                Arrays.asList(original)).get(0);
        Course legacyWithId = mapper.toLegacyCourses(
                Arrays.asList(firstStructured)).get(0);
        StructuredCourse secondStructured = mapper.fromLegacyCourses(
                Arrays.asList(legacyWithId)).get(0);

        assertTrue(StableCourseId.isValid(firstStructured.id));
        assertEquals(firstStructured.id, legacyWithId.structuredCourseId);
        assertEquals(firstStructured.id, secondStructured.id);
    }

    @Test
    public void mutableCourseFieldsAndMeetingChangesKeepStableId() {
        String id = "11111111-1111-4111-8111-111111111111";
        Course original = courseWithId(
                id, 0, 1, 2, "高等数学", "李老师", "A101", "#4FA4F3");
        List<Course> editedVariants = Arrays.asList(
                courseWithId(id, 0, 1, 2, "高等数学A", "李老师", "A101", "#4FA4F3"),
                courseWithId(id, 0, 1, 2, "高等数学", "王老师", "A101", "#4FA4F3"),
                courseWithId(id, 0, 1, 2, "高等数学", "李老师", "B202", "#4FA4F3"),
                courseWithId(id, 0, 1, 2, "高等数学", "李老师", "A101", "#36B889"),
                courseWithId(id, 2, 5, 6, "高等数学", "李老师", "A101", "#4FA4F3"),
                new Course(0, 1, 2, "高等数学", "1-16周", "A101", "李老师", "raw",
                        "4.0", "#4FA4F3", CourseType.LECTURE, id),
                new Course(0, 1, 2, "高等数学", "1-16周", "A101", "李老师", "raw",
                        "3.0", "#4FA4F3", CourseType.EXPERIMENT, id));
        CourseStructureMapper mapper = new CourseStructureMapper();

        assertEquals(id, mapper.fromLegacyCourses(Arrays.asList(original)).get(0).id);
        for (Course edited : editedVariants) {
            assertEquals(id, mapper.fromLegacyCourses(Arrays.asList(edited)).get(0).id);
        }
    }

    @Test
    public void sameNameCoursesDoNotShareGeneratedIds() {
        Course first = new Course(
                0, 1, 2, "大学英语", "1-16周", "A101", "李老师", "");
        Course second = new Course(
                1, 1, 2, "大学英语", "1-16周", "B202", "王老师", "");

        List<StructuredCourse> structured = new CourseStructureMapper()
                .fromLegacyCourses(Arrays.asList(first, second));

        assertEquals(2, structured.size());
        assertTrue(StableCourseId.isValid(structured.get(0).id));
        assertTrue(StableCourseId.isValid(structured.get(1).id));
        assertNotEquals(structured.get(0).id, structured.get(1).id);
    }

    @Test
    public void distinctStableIdsKeepOtherwiseIdenticalCoursesSeparate() {
        String firstId = "11111111-1111-4111-8111-111111111111";
        String secondId = "22222222-2222-4222-8222-222222222222";
        Course first = courseWithId(
                firstId, 0, 1, 2, "同名课程", "同一教师", "A101", "#4FA4F3");
        Course second = courseWithId(
                secondId, 0, 1, 2, "同名课程", "同一教师", "A101", "#4FA4F3");

        List<StructuredCourse> structured = new CourseStructureMapper()
                .fromLegacyCourses(Arrays.asList(first, second));

        assertEquals(2, structured.size());
        assertEquals(firstId, structured.get(0).id);
        assertEquals(secondId, structured.get(1).id);
    }

    private Course courseWithId(String id, int day, int start, int end,
                                String name, String teacher, String location, String color) {
        return new Course(
                day, start, end, name, "1-16周", location, teacher, "raw",
                "3.0", color, CourseType.LECTURE, id);
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
        assertEquals(expected.courseType, actual.courseType);
    }
}
