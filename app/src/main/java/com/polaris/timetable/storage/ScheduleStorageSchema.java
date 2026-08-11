package com.polaris.timetable.storage;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StableMeetingId;
import com.polaris.timetable.model.StructuredCourse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScheduleStorageSchema {
    public static final int LEGACY_VERSION = 0;
    public static final int STRUCTURED_COURSE_ID_VERSION = 1;
    public static final int COURSE_MEETING_ID_VERSION = 2;
    public static final int COURSE_CLOCK_TIME_VERSION = 3;
    public static final int CURRENT_VERSION = COURSE_CLOCK_TIME_VERSION;
    private static final String VERSION_KEY_PREFIX = "schema_version_";

    interface IdGenerator {
        String create();
    }

    private ScheduleStorageSchema() {
    }

    public static String versionKey(String scheduleId) {
        return VERSION_KEY_PREFIX
                + (scheduleId == null || scheduleId.length() == 0 ? "default" : scheduleId);
    }

    public static MigrationResult migrate(int storedVersion, List<StructuredCourse> source) {
        return migrate(storedVersion, source, StableCourseId::create, StableMeetingId::create);
    }

    static MigrationResult migrate(int storedVersion, List<StructuredCourse> source,
                                   IdGenerator idGenerator) {
        return migrate(storedVersion, source, idGenerator, idGenerator);
    }

    static MigrationResult migrate(int storedVersion, List<StructuredCourse> source,
                                   IdGenerator courseIdGenerator,
                                   IdGenerator meetingIdGenerator) {
        List<StructuredCourse> original = source == null
                ? Collections.emptyList() : source;
        if (storedVersion > CURRENT_VERSION) {
            return new MigrationResult(original, storedVersion, false, false);
        }

        int normalizedVersion = Math.max(LEGACY_VERSION, storedVersion);
        List<StructuredCourse> migrated = new ArrayList<>(original.size());
        Set<String> usedCourseIds = new HashSet<>();
        boolean dataChanged = false;
        for (StructuredCourse course : original) {
            if (course == null) {
                return new MigrationResult(original, normalizedVersion, false, false);
            }
            String courseId = course.id;
            if (!StableCourseId.isValid(courseId) || usedCourseIds.contains(courseId)) {
                courseId = nextUniqueCourseId(courseIdGenerator, usedCourseIds);
                dataChanged = true;
            }
            usedCourseIds.add(courseId);

            List<CourseMeeting> meetings = new ArrayList<>(course.meetings.size());
            Set<String> usedMeetingIds = new HashSet<>();
            boolean meetingsChanged = false;
            for (CourseMeeting meeting : course.meetings) {
                if (meeting == null) {
                    return new MigrationResult(original, normalizedVersion, false, false);
                }
                String meetingId = meeting.id;
                if (!StableMeetingId.isValid(meetingId)
                        || usedMeetingIds.contains(meetingId)) {
                    meetingId = nextUniqueMeetingId(meetingIdGenerator, usedMeetingIds);
                    meetingsChanged = true;
                    dataChanged = true;
                }
                usedMeetingIds.add(meetingId);
                meetings.add(meetingId.equals(meeting.id)
                        ? meeting : copyWithId(meeting, meetingId));
            }

            boolean courseIdChanged = !courseId.equals(course.id);
            migrated.add(courseIdChanged || meetingsChanged
                    ? copyWithIds(course, courseId, meetings)
                    : course);
        }

        boolean versionChanged = normalizedVersion < CURRENT_VERSION;
        return new MigrationResult(
                migrated,
                CURRENT_VERSION,
                dataChanged || versionChanged,
                true);
    }

    private static String nextUniqueCourseId(IdGenerator generator, Set<String> usedIds) {
        String id;
        do {
            id = generator.create();
        } while (!StableCourseId.isValid(id) || usedIds.contains(id));
        return id;
    }

    private static String nextUniqueMeetingId(IdGenerator generator, Set<String> usedIds) {
        String id;
        do {
            id = generator.create();
        } while (!StableMeetingId.isValid(id) || usedIds.contains(id));
        return id;
    }

    private static StructuredCourse copyWithIds(
            StructuredCourse course, String id, List<CourseMeeting> meetings) {
        return new StructuredCourse(
                id,
                course.name,
                course.teacher,
                course.defaultLocation,
                meetings,
                course.rawText,
                course.credit,
                course.color,
                course.courseType);
    }

    private static CourseMeeting copyWithId(CourseMeeting meeting, String id) {
        return new CourseMeeting(
                id,
                meeting.day,
                meeting.startSection,
                meeting.endSection,
                meeting.weekRule,
                meeting.location,
                meeting.teacher,
                meeting.rawText,
                meeting.timeMode,
                meeting.startMinuteOfDay,
                meeting.endMinuteOfDay);
    }

    public static final class MigrationResult {
        public final List<StructuredCourse> courses;
        public final int version;
        public final boolean changed;
        public final boolean canPersist;

        MigrationResult(List<StructuredCourse> courses, int version,
                        boolean changed, boolean canPersist) {
            this.courses = Collections.unmodifiableList(new ArrayList<>(courses));
            this.version = version;
            this.changed = changed;
            this.canPersist = canPersist;
        }
    }
}
