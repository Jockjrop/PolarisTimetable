package com.polaris.timetable;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StableMeetingId;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.parser.WeekRuleParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Applies an edit to the flat legacy course list without losing the course-level semantics
 * represented by a shared StructuredCourse ID.
 */
public final class CourseEditManager {
    private static final WeekRuleParser WEEK_RULE_PARSER = new WeekRuleParser();

    private CourseEditManager() {
    }

    /**
     * Applies the current editor payload to the canonical model. Course-level fields are replaced
     * on StructuredCourse while time, week, teacher and location are replaced only on the selected
     * CourseMeeting. The StructuredCourse UUID never changes.
     */
    public static boolean applyStructuredEdit(
            List<StructuredCourse> structuredCourses, Course original, Course edited) {
        if (structuredCourses == null || original == null || edited == null) {
            return false;
        }
        if (!StableCourseId.isValid(original.structuredCourseId)) {
            return addStructuredCourse(structuredCourses, edited);
        }

        int courseIndex = findCourseIndex(structuredCourses, original.structuredCourseId);
        if (courseIndex < 0) {
            return false;
        }
        StructuredCourse source = structuredCourses.get(courseIndex);
        int meetingIndex = findMeetingIndex(source, original);
        if (meetingIndex < 0) {
            return false;
        }

        List<CourseMeeting> meetings = new ArrayList<>(source.meetings);
        CourseMeeting previousMeeting = meetings.get(meetingIndex);
        meetings.set(meetingIndex, new CourseMeeting(
                previousMeeting.id,
                edited.day,
                edited.startSection,
                edited.endSection,
                WEEK_RULE_PARSER.parse(edited.weeks),
                edited.location,
                edited.teacher,
                previousMeeting.rawText,
                edited.timeMode,
                edited.startMinuteOfDay,
                edited.endMinuteOfDay));
        structuredCourses.set(courseIndex, new StructuredCourse(
                source.id,
                edited.name,
                source.teacher,
                source.defaultLocation,
                meetings,
                source.rawText,
                edited.credit,
                edited.color,
                edited.courseType));
        return true;
    }

    /** Updates only course-level fields and retains the stable UUID and every meeting. */
    public static boolean updateCourseFields(
            List<StructuredCourse> structuredCourses,
            String structuredCourseId,
            String name,
            String defaultTeacher,
            String defaultLocation,
            String credit,
            String color,
            CourseType courseType) {
        int courseIndex = findCourseIndex(structuredCourses, structuredCourseId);
        if (courseIndex < 0) {
            return false;
        }
        StructuredCourse source = structuredCourses.get(courseIndex);
        structuredCourses.set(courseIndex, new StructuredCourse(
                source.id,
                name,
                defaultTeacher,
                defaultLocation,
                source.meetings,
                source.rawText,
                credit,
                color,
                courseType));
        return true;
    }

    /** Replaces one meeting by stable ID without changing its identity or sibling meetings. */
    public static boolean updateMeeting(
            List<StructuredCourse> structuredCourses,
            String structuredCourseId,
            String meetingId,
            CourseMeeting editedMeeting) {
        int courseIndex = findCourseIndex(structuredCourses, structuredCourseId);
        if (courseIndex < 0 || editedMeeting == null || !StableMeetingId.isValid(meetingId)) {
            return false;
        }
        StructuredCourse source = structuredCourses.get(courseIndex);
        int meetingIndex = findMeetingIndex(source, meetingId);
        if (meetingIndex < 0) {
            return false;
        }
        List<CourseMeeting> meetings = new ArrayList<>(source.meetings);
        meetings.set(meetingIndex, copyWithId(editedMeeting, meetingId));
        structuredCourses.set(courseIndex, copyWithMeetings(source, meetings));
        return true;
    }

    /** Legacy index-based compatibility helper. Normal production edits use meeting UUID. */
    @Deprecated
    public static boolean updateMeeting(
            List<StructuredCourse> structuredCourses,
            String structuredCourseId,
            int meetingIndex,
            CourseMeeting editedMeeting) {
        int courseIndex = findCourseIndex(structuredCourses, structuredCourseId);
        if (courseIndex < 0 || editedMeeting == null) {
            return false;
        }
        StructuredCourse source = structuredCourses.get(courseIndex);
        if (meetingIndex < 0 || meetingIndex >= source.meetings.size()) {
            return false;
        }
        return updateMeeting(
                structuredCourses, structuredCourseId,
                source.meetings.get(meetingIndex).id, editedMeeting);
    }

    private static boolean addStructuredCourse(
            List<StructuredCourse> structuredCourses, Course edited) {
        String stableId = StableCourseId.isValid(edited.structuredCourseId)
                ? edited.structuredCourseId : StableCourseId.create();
        CourseMeeting meeting = new CourseMeeting(
                edited.day,
                edited.startSection,
                edited.endSection,
                WEEK_RULE_PARSER.parse(edited.weeks),
                edited.location,
                edited.teacher,
                edited.raw,
                edited.timeMode,
                edited.startMinuteOfDay,
                edited.endMinuteOfDay);
        structuredCourses.add(new StructuredCourse(
                stableId,
                edited.name,
                edited.teacher,
                edited.location,
                Collections.singletonList(meeting),
                edited.raw,
                edited.credit,
                edited.color,
                edited.courseType));
        return true;
    }

    private static int findCourseIndex(
            List<StructuredCourse> structuredCourses, String structuredCourseId) {
        if (structuredCourses == null || !StableCourseId.isValid(structuredCourseId)) {
            return -1;
        }
        for (int index = 0; index < structuredCourses.size(); index++) {
            StructuredCourse course = structuredCourses.get(index);
            if (course != null && structuredCourseId.equalsIgnoreCase(course.id)) {
                return index;
            }
        }
        return -1;
    }

    private static int findMeetingIndex(StructuredCourse course, Course target) {
        if (StableMeetingId.isValid(target.meetingId)) {
            return findMeetingIndex(course, target.meetingId);
        }
        return findMeetingIndexByLegacySignature(course, target);
    }

    private static int findMeetingIndex(StructuredCourse course, String meetingId) {
        for (int index = 0; index < course.meetings.size(); index++) {
            CourseMeeting meeting = course.meetings.get(index);
            if (meeting != null && meetingId.equalsIgnoreCase(meeting.id)) {
                return index;
            }
        }
        return -1;
    }

    /** Compatibility fallback for pre-v2 views and old share/recovery payloads. */
    private static int findMeetingIndexByLegacySignature(
            StructuredCourse course, Course target) {
        int slotFallback = -1;
        for (int index = 0; index < course.meetings.size(); index++) {
            CourseMeeting meeting = course.meetings.get(index);
            if (meeting.day != target.day
                    || meeting.startSection != target.startSection
                    || meeting.endSection != target.endSection
                    || meeting.timeMode != target.timeMode
                    || meeting.startMinuteOfDay != target.startMinuteOfDay
                    || meeting.endMinuteOfDay != target.endMinuteOfDay) {
                continue;
            }
            if (slotFallback < 0) {
                slotFallback = index;
            }
            String weeks = meeting.weekRule == null
                    ? "周次见PDF" : meeting.weekRule.displayText();
            String teacher = meeting.teacher.isEmpty() ? course.teacher : meeting.teacher;
            String location = meeting.location.isEmpty()
                    ? course.defaultLocation : meeting.location;
            if (same(weeks, target.weeks)
                    && same(teacher, target.teacher)
                    && same(location, target.location)) {
                return index;
            }
        }
        return slotFallback;
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

    private static StructuredCourse copyWithMeetings(
            StructuredCourse source, List<CourseMeeting> meetings) {
        return new StructuredCourse(
                source.id,
                source.name,
                source.teacher,
                source.defaultLocation,
                meetings,
                source.rawText,
                source.credit,
                source.color,
                source.courseType);
    }

    private static boolean same(String first, String second) {
        return normalize(first).equals(normalize(second));
    }

    /** Legacy-only flat-list helper retained for compatibility tests and old integrations. */
    @Deprecated
    public static boolean applyEdit(List<Course> courses, Course original, Course edited) {
        if (courses == null || original == null || edited == null) {
            return false;
        }

        int originalIndex = courses.indexOf(original);
        if (originalIndex < 0) {
            courses.add(edited);
            return true;
        }

        String stableId = original.structuredCourseId;
        if (!StableCourseId.isValid(stableId)) {
            courses.set(originalIndex, edited);
            return true;
        }

        String originalTeacher = normalize(original.teacher);
        String editedTeacher = normalize(edited.teacher);
        if (!originalTeacher.equals(editedTeacher)) {
            courses.set(originalIndex, copyWithStableId(edited, StableCourseId.create()));
            return true;
        }

        boolean sharedByDifferentTeacher = false;
        for (Course candidate : courses) {
            if (stableId.equalsIgnoreCase(candidate.structuredCourseId)
                    && !originalTeacher.equals(normalize(candidate.teacher))) {
                sharedByDifferentTeacher = true;
                break;
            }
        }
        String targetStableId = sharedByDifferentTeacher ? StableCourseId.create() : stableId;

        for (int index = 0; index < courses.size(); index++) {
            Course candidate = courses.get(index);
            if (!stableId.equalsIgnoreCase(candidate.structuredCourseId)
                    || !originalTeacher.equals(normalize(candidate.teacher))) {
                continue;
            }
            if (index == originalIndex) {
                courses.set(index, copyWithStableId(edited, targetStableId));
            } else {
                courses.set(index, copyCourseLevelFields(candidate, edited, targetStableId));
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static Course copyWithStableId(Course course, String stableId) {
        return new Course(
                course.day,
                course.startSection,
                course.endSection,
                course.name,
                course.weeks,
                course.location,
                course.teacher,
                course.raw,
                course.credit,
                course.color,
                course.courseType,
                stableId,
                course.meetingId,
                course.timeMode,
                course.startMinuteOfDay,
                course.endMinuteOfDay);
    }

    private static Course copyCourseLevelFields(
            Course meeting, Course edited, String stableId) {
        return new Course(
                meeting.day,
                meeting.startSection,
                meeting.endSection,
                edited.name,
                meeting.weeks,
                meeting.location,
                meeting.teacher,
                meeting.raw,
                edited.credit,
                edited.color,
                edited.courseType,
                stableId,
                meeting.meetingId,
                meeting.timeMode,
                meeting.startMinuteOfDay,
                meeting.endMinuteOfDay);
    }
}
