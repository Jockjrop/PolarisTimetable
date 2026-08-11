package com.polaris.timetable.importer.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.Course;
import com.polaris.timetable.importer.ai.dto.AiCourse;
import com.polaris.timetable.importer.ai.dto.AiMeeting;
import com.polaris.timetable.importer.ai.dto.AiPracticeCourse;
import com.polaris.timetable.importer.ai.dto.AiScheduleImport;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StableMeetingId;
import com.polaris.timetable.model.StructuredCourse;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AiScheduleImportMapperTest {
    private final AiScheduleImportMapper mapper = new AiScheduleImportMapper();

    @Test
    public void mapsSingleCourseAndSingleMeeting() {
        AiCourse sourceCourse = course(
                "操作系统", "梁琛", "操作系统-0003", 3.0,
                meeting(2, 1, 2, "1-17周(单)", "长安校区西区", "B122"));

        AiScheduleImportPlan plan = mapper.map(importOf(sourceCourse));

        assertEquals("2026-2027学年第1学期", plan.semester);
        assertEquals(1, plan.courses.size());
        assertEquals(1, plan.meetingCount());
        StructuredCourse course = plan.courses.get(0);
        assertEquals("操作系统", course.name);
        assertEquals("梁琛", course.teacher);
        assertEquals("3.0", course.credit);
        assertEquals(CourseType.LECTURE, course.courseType);
        assertEquals(1, course.meetings.size());
    }

    @Test
    public void oneAiCourseWithMultipleMeetingsStaysOneStructuredCourse() {
        AiCourse sourceCourse = course(
                "操作系统", "梁琛", "操作系统-0003", 3.0,
                meeting(2, 1, 2, "1-17周(单)", null, "B122"),
                meeting(0, 5, 6, "2-18周(双)", null, "A101"));

        AiScheduleImportPlan plan = mapper.map(importOf(sourceCourse));

        assertEquals(1, plan.courses.size());
        assertEquals(2, plan.courses.get(0).meetings.size());
        assertEquals(2, plan.meetingCount());
    }

    @Test
    public void multipleAiCoursesAreNotMerged() {
        AiCourse first = course("课程A", "教师", "A-1", 2.0,
                meeting(0, 1, 2, "1-18周", null, "A101"));
        AiCourse second = course("课程B", "教师", "B-1", 2.0,
                meeting(1, 1, 2, "1-18周", null, "B202"));

        AiScheduleImportPlan plan = mapper.map(importOf(first, second));

        assertEquals(2, plan.courses.size());
        assertEquals("课程A", plan.courses.get(0).name);
        assertEquals("课程B", plan.courses.get(1).name);
    }

    @Test
    public void sameNameWithDifferentTeacherOrTeachingClassIsNotMerged() {
        AiCourse first = course("大学英语", "李老师", "英语-1", 2.0,
                meeting(0, 1, 2, "1-18周", null, "A101"));
        AiCourse differentTeacher = course("大学英语", "王老师", "英语-1", 2.0,
                meeting(1, 1, 2, "1-18周", null, "B202"));
        AiCourse differentTeachingClass = course("大学英语", "李老师", "英语-2", 2.0,
                meeting(2, 1, 2, "1-18周", null, "C303"));

        AiScheduleImportPlan plan = mapper.map(
                importOf(first, differentTeacher, differentTeachingClass));

        assertEquals(3, plan.courses.size());
        assertEquals("李老师", plan.courses.get(0).teacher);
        assertEquals("王老师", plan.courses.get(1).teacher);
        assertNotEquals(plan.courses.get(0).id, plan.courses.get(2).id);
    }

    @Test
    public void keepsZeroBasedDayOfWeek() {
        CourseMeeting meeting = onlyMeeting(
                course("周一课程", null, null, null,
                        meeting(0, 3, 4, "1-18周", null, null)));

        assertEquals(0, meeting.day);
        assertEquals(3, meeting.startSection);
        assertEquals(4, meeting.endSection);
        assertEquals(CourseTimeMode.SECTION, meeting.timeMode);
    }

    @Test
    public void mapsOddWeekRuleThroughExistingParser() {
        CourseMeeting meeting = onlyMeeting(
                course("单周课程", null, null, null,
                        meeting(0, 1, 2, "1-17周(单)", null, null)));

        assertTrue(meeting.weekRule.containsWeek(1));
        assertFalse(meeting.weekRule.containsWeek(2));
        assertTrue(meeting.weekRule.containsWeek(17));
        assertEquals("1-17周(单)", meeting.weekRule.displayText());
    }

    @Test
    public void mapsEvenWeekRuleThroughExistingParser() {
        CourseMeeting meeting = onlyMeeting(
                course("双周课程", null, null, null,
                        meeting(0, 1, 2, "2-18周(双)", null, null)));

        assertTrue(meeting.weekRule.containsWeek(2));
        assertFalse(meeting.weekRule.containsWeek(3));
        assertTrue(meeting.weekRule.containsWeek(18));
        assertEquals("2-18周(双)", meeting.weekRule.displayText());
    }

    @Test
    public void combinesCampusAndLocationIntoMeetingLocation() {
        AiCourse course = course("地点测试", null, null, null,
                meeting(0, 1, 2, "1-18周", "长安校区西区", "B122"),
                meeting(1, 1, 2, "1-18周", null, "C303"),
                meeting(2, 1, 2, "1-18周", "雁塔校区", null));

        List<CourseMeeting> meetings = mapper.map(importOf(course)).courses.get(0).meetings;

        assertEquals("长安校区西区 B122", meetings.get(0).location);
        assertEquals("C303", meetings.get(1).location);
        assertEquals("雁塔校区", meetings.get(2).location);
    }

    @Test
    public void nullOptionalFieldsUseDomainEmptyValuesNotNullText() {
        AiCourse sourceCourse = course("空字段课程", null, null, null,
                meeting(0, 1, 2, "1-18周", null, null));

        StructuredCourse course = mapper.map(importOf(sourceCourse)).courses.get(0);
        CourseMeeting meeting = course.meetings.get(0);

        assertEquals("", course.teacher);
        assertEquals("", course.defaultLocation);
        assertEquals("", course.credit);
        assertEquals("", meeting.location);
        assertEquals("", meeting.teacher);
        assertNotEquals("null", course.teacher);
        assertNotEquals("null", meeting.location);
    }

    @Test
    public void generatesPolarisCourseAndMeetingIds() {
        StructuredCourse course = mapper.map(importOf(
                course("ID测试", "教师", null, null,
                        meeting(0, 1, 2, "1-18周", null, null))))
                .courses.get(0);

        assertTrue(StableCourseId.isValid(course.id));
        assertTrue(StableMeetingId.isValid(course.meetings.get(0).id));
    }

    @Test
    public void independentMappingsGenerateFreshIds() {
        AiScheduleImport source = importOf(
                course("ID测试", "教师", null, null,
                        meeting(0, 1, 2, "1-18周", null, null)));

        StructuredCourse first = mapper.map(source).courses.get(0);
        StructuredCourse second = mapper.map(source).courses.get(0);

        assertNotEquals(first.id, second.id);
        assertNotEquals(first.meetings.get(0).id, second.meetings.get(0).id);
    }

    @Test
    public void practiceCourseUsesExistingBannerOnlyRepresentation() {
        AiPracticeCourse practice = new AiPracticeCourse(
                "物联网智能计算实验", "王宏刚", "9-16周");
        AiScheduleImport source = new AiScheduleImport(
                AiScheduleImport.FORMAT,
                "2026-2027学年第1学期",
                Collections.emptyList(),
                Collections.singletonList(practice));

        AiScheduleImportPlan plan = mapper.map(source);

        assertEquals(1, plan.courses.size());
        assertTrue(plan.unresolvedPracticeCourses.isEmpty());
        assertTrue(plan.warnings.isEmpty());
        StructuredCourse mapped = plan.courses.get(0);
        assertEquals(CourseType.PRACTICE, mapped.courseType);
        assertEquals("物联网智能计算实验", mapped.name);
        assertEquals("王宏刚", mapped.teacher);
        assertEquals(1, mapped.meetings.size());
        CourseMeeting meeting = mapped.meetings.get(0);
        assertEquals(CourseTimeMode.NONE, meeting.timeMode);
        assertEquals(-1, meeting.day);
        assertEquals(0, meeting.startSection);
        assertEquals(0, meeting.endSection);
        assertTrue(meeting.weekRule.containsWeek(9));
        assertTrue(meeting.weekRule.containsWeek(16));
        assertFalse(meeting.weekRule.containsWeek(8));

        Course flattened = new CourseStructureMapper()
                .toLegacyCourses(plan.courses).get(0);
        assertTrue(flattened.isPracticeBannerOnly());
        assertFalse(flattened.hasScheduledTime());
    }

    @Test
    public void candidatesUseExistingFlattenCompatibilityPath() {
        AiScheduleImportPlan plan = mapper.map(importOf(
                course("兼容视图", "张老师", null, 2.0,
                        meeting(4, 5, 6, "1-18周", "长安校区", "D404"))));

        Course flattened = new CourseStructureMapper()
                .toLegacyCourses(plan.courses).get(0);
        StructuredCourse structured = plan.courses.get(0);

        assertEquals(structured.id, flattened.structuredCourseId);
        assertEquals(structured.meetings.get(0).id, flattened.meetingId);
        assertEquals("张老师", flattened.teacher);
        assertEquals("长安校区 D404", flattened.location);
        assertEquals("1-18周", flattened.weeks);
    }

    @Test
    public void mappingStopsAtImmutableCandidatePlanAndLeavesSourceUntouched() {
        AiCourse sourceCourse = course("纯转换", "教师", "班级-1", 1.0,
                meeting(0, 1, 2, "1-18周", null, null));
        AiScheduleImport source = importOf(sourceCourse);

        AiScheduleImportPlan plan = mapper.map(source);

        assertEquals(1, source.courses.size());
        assertEquals(sourceCourse, source.courses.get(0));
        assertEquals(1, plan.courses.size());
        boolean immutable = false;
        try {
            plan.courses.clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        assertTrue(immutable);
    }

    @Test
    public void validatedParserSuccessFeedsMapperEntryPoint() {
        String aiText = AiScheduleImport.OUTPUT_MARKER + "\n{"
                + "\"format\":\"polaris-schedule-v1\","
                + "\"semester\":\"2026-2027学年第1学期\","
                + "\"courses\":[{\"name\":\"编译原理\",\"meetings\":[{"
                + "\"dayOfWeek\":3,\"startSection\":7,\"endSection\":8,"
                + "\"weekRule\":\"1-18周\"}]}],"
                + "\"practiceCourses\":[]}";
        AiScheduleImportResult parsed = new AiScheduleImportParser().parse(aiText);

        assertTrue(parsed instanceof AiScheduleImportResult.Success);
        AiScheduleImportPlan plan = mapper.map(
                ((AiScheduleImportResult.Success) parsed).data);

        assertEquals(1, plan.courses.size());
        assertEquals("编译原理", plan.courses.get(0).name);
        assertEquals(3, plan.courses.get(0).meetings.get(0).day);
    }

    private CourseMeeting onlyMeeting(AiCourse course) {
        return mapper.map(importOf(course)).courses.get(0).meetings.get(0);
    }

    private AiScheduleImport importOf(AiCourse... courses) {
        return new AiScheduleImport(
                AiScheduleImport.FORMAT,
                "2026-2027学年第1学期",
                Arrays.asList(courses),
                Collections.emptyList());
    }

    private AiCourse course(String name, String teacher, String teachingClass,
                            Double credit, AiMeeting... meetings) {
        return new AiCourse(name, teacher, teachingClass, credit, Arrays.asList(meetings));
    }

    private AiMeeting meeting(int day, int startSection, int endSection,
                              String weekRule, String campus, String location) {
        return new AiMeeting(day, startSection, endSection,
                weekRule, campus, location);
    }
}
