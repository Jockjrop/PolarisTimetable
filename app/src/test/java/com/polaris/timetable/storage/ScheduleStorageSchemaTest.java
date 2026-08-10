package com.polaris.timetable.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import org.json.JSONException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScheduleStorageSchemaTest {
    private static final String FIRST_UUID = "11111111-1111-4111-8111-111111111111";
    private static final String SECOND_UUID = "22222222-2222-4222-8222-222222222222";

    @Test
    public void oldStructuredCourse_firstReadCreatesUuid_secondReadKeepsIt() throws Exception {
        String oldJson = ScheduleRepository.structuredCoursesToJson(Collections.singletonList(
                course("高等数学|教师|5|#4FA4F3|LECTURE", "高等数学"))).toString();

        List<StructuredCourse> firstRead = ScheduleRepository.structuredCoursesFromJson(oldJson);
        ScheduleStorageSchema.MigrationResult firstMigration = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.LEGACY_VERSION, firstRead, sequence(FIRST_UUID));
        String persisted = ScheduleRepository.structuredCoursesToJson(firstMigration.courses).toString();
        List<StructuredCourse> secondRead = ScheduleRepository.structuredCoursesFromJson(persisted);
        ScheduleStorageSchema.MigrationResult secondMigration = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.CURRENT_VERSION, secondRead, sequence(SECOND_UUID));

        assertEquals(FIRST_UUID, firstMigration.courses.get(0).id);
        assertEquals(FIRST_UUID, secondMigration.courses.get(0).id);
        assertTrue(firstMigration.changed);
        assertFalse(secondMigration.changed);
    }

    @Test
    public void migrationIsIdempotentAndDoesNotDuplicateCourses() {
        List<StructuredCourse> oldCourses = Arrays.asList(
                course("", "高等数学"),
                course("legacy-name-derived-id", "大学物理"));

        ScheduleStorageSchema.MigrationResult first = ScheduleStorageSchema.migrate(
                0, oldCourses, sequence(FIRST_UUID, SECOND_UUID));
        ScheduleStorageSchema.MigrationResult second = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.CURRENT_VERSION, first.courses, sequence(
                        "33333333-3333-4333-8333-333333333333"));

        assertEquals(2, first.courses.size());
        assertEquals(2, second.courses.size());
        assertEquals(first.courses.get(0).id, second.courses.get(0).id);
        assertEquals(first.courses.get(1).id, second.courses.get(1).id);
        assertNotEquals(first.courses.get(0).id, first.courses.get(1).id);
        assertFalse(second.changed);
    }

    @Test
    public void schemaUpgradePreservesAllCourseAndMeetingData() {
        StructuredCourse old = course("old-derived-id", "操作系统");

        ScheduleStorageSchema.MigrationResult migration = ScheduleStorageSchema.migrate(
                0, Collections.singletonList(old), sequence(FIRST_UUID));
        StructuredCourse migrated = migration.courses.get(0);

        assertEquals(ScheduleStorageSchema.CURRENT_VERSION, migration.version);
        assertEquals("操作系统", migrated.name);
        assertEquals("梁琛", migrated.teacher);
        assertEquals("长安校区西区B122", migrated.defaultLocation);
        assertEquals("3.0", migrated.credit);
        assertEquals("#4FA4F3", migrated.color);
        assertEquals(CourseType.LECTURE, migrated.courseType);
        assertEquals(1, migrated.meetings.size());
        assertEquals(0, migrated.meetings.get(0).day);
        assertEquals(1, migrated.meetings.get(0).startSection);
        assertEquals(2, migrated.meetings.get(0).endSection);
        assertTrue(migrated.meetings.get(0).weekRule.containsWeek(8));
    }

    @Test
    public void duplicateValidIdsAreSeparatedWithoutUsingCourseName() {
        StructuredCourse first = course(FIRST_UUID, "同名课程");
        StructuredCourse second = course(FIRST_UUID, "同名课程");

        ScheduleStorageSchema.MigrationResult migration = ScheduleStorageSchema.migrate(
                1, Arrays.asList(first, second), sequence(SECOND_UUID));

        assertEquals(FIRST_UUID, migration.courses.get(0).id);
        assertEquals(SECOND_UUID, migration.courses.get(1).id);
        assertNotEquals(migration.courses.get(0).id, migration.courses.get(1).id);
    }

    @Test
    public void oldLegacyCourseJsonStillLoadsWithoutStableId() throws Exception {
        String oldJson = "[{\"day\":0,\"startSection\":1,\"endSection\":2,"
                + "\"name\":\"高等数学\",\"weeks\":\"1-16周\","
                + "\"location\":\"A101\",\"teacher\":\"李老师\","
                + "\"raw\":\"raw\",\"credit\":\"5\",\"color\":\"#4FA4F3\","
                + "\"courseType\":\"LECTURE\"}]";

        List<Course> courses = ScheduleRepository.legacyCoursesFromJson(oldJson);

        assertEquals(1, courses.size());
        assertEquals("高等数学", courses.get(0).name);
        assertEquals("1-16周", courses.get(0).weeks);
        assertEquals("", courses.get(0).structuredCourseId);
    }

    @Test
    public void legacyAndStructuredPayloadsKeepTheSameSemanticsAndStableId() throws Exception {
        StructuredCourse structured = course(FIRST_UUID, "操作系统");
        CourseStructureMapper mapper = new CourseStructureMapper();
        Course legacy = mapper.toLegacyCourses(Collections.singletonList(structured)).get(0);
        String legacyJson = "[" + ScheduleRepository.toJson(legacy).toString() + "]";

        Course restoredLegacy = ScheduleRepository.legacyCoursesFromJson(legacyJson).get(0);
        StructuredCourse restoredStructured = mapper.fromLegacyCourses(
                Collections.singletonList(restoredLegacy)).get(0);

        assertEquals(FIRST_UUID, restoredLegacy.structuredCourseId);
        assertEquals(FIRST_UUID, restoredStructured.id);
        assertEquals(structured.name, restoredStructured.name);
        assertEquals(structured.teacher, restoredStructured.teacher);
        assertEquals(structured.defaultLocation, restoredStructured.defaultLocation);
        assertEquals(structured.meetings.get(0).day, restoredStructured.meetings.get(0).day);
        assertEquals(structured.meetings.get(0).startSection,
                restoredStructured.meetings.get(0).startSection);
        assertTrue(restoredStructured.meetings.get(0).weekRule.containsWeek(16));
    }

    @Test
    public void schemaVersionKeyIsScopedPerSchedule() {
        assertEquals("schema_version_default", ScheduleStorageSchema.versionKey("default"));
        assertEquals("schema_version_schedule_123", ScheduleStorageSchema.versionKey("schedule_123"));
        assertNotEquals(
                ScheduleStorageSchema.versionKey("schedule_123"),
                ScheduleStorageSchema.versionKey("schedule_456"));
    }

    @Test(expected = JSONException.class)
    public void malformedStructuredJsonIsRejectedBeforeMigrationCanPersist() throws Exception {
        ScheduleRepository.structuredCoursesFromJson("[{\"name\":\"有效课程\"}, 7]");
    }

    @Test
    public void futureSchemaIsReadableButNeverDowngradedOrPersisted() {
        StructuredCourse course = course(FIRST_UUID, "未来数据");

        ScheduleStorageSchema.MigrationResult result = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.CURRENT_VERSION + 1,
                Collections.singletonList(course),
                sequence(SECOND_UUID));

        assertFalse(result.canPersist);
        assertFalse(result.changed);
        assertEquals(FIRST_UUID, result.courses.get(0).id);
        assertEquals(ScheduleStorageSchema.CURRENT_VERSION + 1, result.version);
    }

    private StructuredCourse course(String id, String name) {
        WeekRule weeks = new WeekRule(
                WeekRule.Type.RANGE, 1, 16, Collections.emptyList(), "1-16周");
        CourseMeeting meeting = new CourseMeeting(
                0, 1, 2, weeks, "长安校区西区B122", "梁琛", "raw-meeting");
        return new StructuredCourse(
                id,
                name,
                "梁琛",
                "长安校区西区B122",
                Collections.singletonList(meeting),
                "raw-course",
                "3.0",
                "#4FA4F3",
                CourseType.LECTURE);
    }

    private ScheduleStorageSchema.IdGenerator sequence(String... values) {
        return new ScheduleStorageSchema.IdGenerator() {
            int index;

            @Override
            public String create() {
                String value = values[Math.min(index, values.length - 1)];
                index++;
                assertTrue(StableCourseId.isValid(value));
                return value;
            }
        };
    }
}
