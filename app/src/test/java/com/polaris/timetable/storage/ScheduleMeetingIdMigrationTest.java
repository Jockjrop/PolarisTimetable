package com.polaris.timetable.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StableMeetingId;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScheduleMeetingIdMigrationTest {
    private static final String COURSE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String MEETING_ID_1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String MEETING_ID_2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String MEETING_ID_3 = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String UNUSED_ID = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";

    @Test
    public void versionOneWithoutMeetingId_migratesToVersionTwo() {
        StructuredCourse v1 = course(COURSE_ID, Collections.singletonList(meeting("")));

        ScheduleStorageSchema.MigrationResult migration = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.STRUCTURED_COURSE_ID_VERSION,
                Collections.singletonList(v1),
                sequence(UNUSED_ID),
                sequence(MEETING_ID_1));

        assertEquals(ScheduleStorageSchema.COURSE_MEETING_ID_VERSION, migration.version);
        assertTrue(migration.changed);
        assertEquals(COURSE_ID, migration.courses.get(0).id);
        assertEquals(MEETING_ID_1, migration.courses.get(0).meetings.get(0).id);
        assertMeetingDataEquals(v1.meetings.get(0), migration.courses.get(0).meetings.get(0));
    }

    @Test
    public void secondMigration_doesNotGenerateMeetingIdAgain() {
        StructuredCourse v1 = course(COURSE_ID, Collections.singletonList(meeting("")));
        ScheduleStorageSchema.MigrationResult first = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.STRUCTURED_COURSE_ID_VERSION,
                Collections.singletonList(v1),
                sequence(UNUSED_ID),
                sequence(MEETING_ID_1));

        ScheduleStorageSchema.MigrationResult second = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.CURRENT_VERSION,
                first.courses,
                sequence(UNUSED_ID),
                sequence(MEETING_ID_2));

        assertFalse(second.changed);
        assertEquals(MEETING_ID_1, second.courses.get(0).meetings.get(0).id);
    }

    @Test
    public void existingValidMeetingId_isPreserved() {
        StructuredCourse v1 = course(
                COURSE_ID, Collections.singletonList(meeting(MEETING_ID_1)));

        ScheduleStorageSchema.MigrationResult migration = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.STRUCTURED_COURSE_ID_VERSION,
                Collections.singletonList(v1),
                sequence(UNUSED_ID),
                sequence(MEETING_ID_2));

        assertEquals(MEETING_ID_1, migration.courses.get(0).meetings.get(0).id);
    }

    @Test
    public void versionZero_withoutCourseOrMeetingIds_upgradesDirectlyToVersionTwo() {
        StructuredCourse v0 = course("legacy-derived-id", Collections.singletonList(meeting("")));

        ScheduleStorageSchema.MigrationResult migration = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.LEGACY_VERSION,
                Collections.singletonList(v0),
                sequence(COURSE_ID),
                sequence(MEETING_ID_1));

        assertEquals(ScheduleStorageSchema.CURRENT_VERSION, migration.version);
        assertEquals(COURSE_ID, migration.courses.get(0).id);
        assertEquals(MEETING_ID_1, migration.courses.get(0).meetings.get(0).id);
        assertTrue(StableCourseId.isValid(migration.courses.get(0).id));
        assertTrue(StableMeetingId.isValid(migration.courses.get(0).meetings.get(0).id));
    }

    @Test
    public void threeMissingMeetingIds_receiveThreeDifferentIds() {
        StructuredCourse v1 = course(COURSE_ID, Arrays.asList(
                meeting(""), meeting(""), meeting("")));

        ScheduleStorageSchema.MigrationResult migration = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.STRUCTURED_COURSE_ID_VERSION,
                Collections.singletonList(v1),
                sequence(UNUSED_ID),
                sequence(MEETING_ID_1, MEETING_ID_2, MEETING_ID_3));
        List<CourseMeeting> meetings = migration.courses.get(0).meetings;

        assertEquals(MEETING_ID_1, meetings.get(0).id);
        assertEquals(MEETING_ID_2, meetings.get(1).id);
        assertEquals(MEETING_ID_3, meetings.get(2).id);
        assertNotEquals(meetings.get(0).id, meetings.get(1).id);
        assertNotEquals(meetings.get(1).id, meetings.get(2).id);
    }

    @Test
    public void duplicateValidMeetingIds_areSeparatedWithinOneCourse() {
        StructuredCourse v1 = course(COURSE_ID, Arrays.asList(
                meeting(MEETING_ID_1), meeting(MEETING_ID_1)));

        ScheduleStorageSchema.MigrationResult migration = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.STRUCTURED_COURSE_ID_VERSION,
                Collections.singletonList(v1),
                sequence(UNUSED_ID),
                sequence(MEETING_ID_2));

        assertEquals(MEETING_ID_1, migration.courses.get(0).meetings.get(0).id);
        assertEquals(MEETING_ID_2, migration.courses.get(0).meetings.get(1).id);
    }

    private StructuredCourse course(String id, List<CourseMeeting> meetings) {
        return new StructuredCourse(
                id, "操作系统", "默认教师", "默认地点", meetings,
                "raw-course", "3.0", "#4FA4F3", CourseType.LECTURE);
    }

    private CourseMeeting meeting(String id) {
        WeekRule weeks = new WeekRule(
                WeekRule.Type.RANGE, 1, 16, Collections.emptyList(), "1-16周");
        return new CourseMeeting(id, 0, 1, 2, weeks, "A101", "张老师", "raw-meeting");
    }

    private void assertMeetingDataEquals(CourseMeeting expected, CourseMeeting actual) {
        assertEquals(expected.day, actual.day);
        assertEquals(expected.startSection, actual.startSection);
        assertEquals(expected.endSection, actual.endSection);
        assertEquals(expected.weekRule.type, actual.weekRule.type);
        assertEquals(expected.weekRule.rawText, actual.weekRule.rawText);
        assertEquals(expected.location, actual.location);
        assertEquals(expected.teacher, actual.teacher);
        assertEquals(expected.rawText, actual.rawText);
    }

    private ScheduleStorageSchema.IdGenerator sequence(String... values) {
        return new ScheduleStorageSchema.IdGenerator() {
            int index;

            @Override
            public String create() {
                String value = values[Math.min(index, values.length - 1)];
                index++;
                return value;
            }
        };
    }
}
