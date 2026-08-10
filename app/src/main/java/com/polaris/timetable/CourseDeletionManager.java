package com.polaris.timetable;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StableMeetingId;
import com.polaris.timetable.parser.WeekRuleParser;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CourseDeletionManager {
    private static final WeekRuleParser WEEK_RULE_PARSER = new WeekRuleParser();

    private CourseDeletionManager() {
    }

    /** Deletes from canonical StructuredCourse data using only the stable UUID and meeting. */
    public static int deleteStructured(
            List<StructuredCourse> structuredCourses,
            Course target,
            CourseDeletionScope scope,
            int currentWeek,
            int semesterWeeks) {
        if (structuredCourses == null || target == null || scope == null
                || !StableCourseId.isValid(target.structuredCourseId)) {
            return 0;
        }
        int courseIndex = findStructuredCourseIndex(
                structuredCourses, target.structuredCourseId);
        if (courseIndex < 0) {
            return 0;
        }
        if (scope == CourseDeletionScope.ALL_MEETINGS) {
            structuredCourses.remove(courseIndex);
            return 1;
        }

        StructuredCourse course = structuredCourses.get(courseIndex);
        int meetingIndex = findMeetingIndex(course, target);
        if (meetingIndex < 0) {
            return 0;
        }
        List<CourseMeeting> meetings = new ArrayList<>(course.meetings);
        if (scope == CourseDeletionScope.CURRENT_MEETING) {
            meetings.remove(meetingIndex);
        } else if (scope == CourseDeletionScope.CURRENT_WEEK) {
            CourseMeeting meeting = meetings.get(meetingIndex);
            if (currentWeek <= 0 || !meeting.isActiveInWeek(currentWeek)) {
                return 0;
            }
            List<Integer> remainingWeeks = remainingWeeks(
                    meeting.weekRule, currentWeek, semesterWeeks);
            if (remainingWeeks.isEmpty()) {
                meetings.remove(meetingIndex);
            } else {
                WeekRule remainingRule = new WeekRule(
                        WeekRule.Type.RANGE,
                        remainingWeeks.get(0),
                        remainingWeeks.get(remainingWeeks.size() - 1),
                        remainingWeeks,
                        compactWeekExpression(remainingWeeks));
                meetings.set(meetingIndex, new CourseMeeting(
                        meeting.id,
                        meeting.day,
                        meeting.startSection,
                        meeting.endSection,
                        remainingRule,
                        meeting.location,
                        meeting.teacher,
                        meeting.rawText));
            }
        } else {
            return 0;
        }
        structuredCourses.set(courseIndex, copyWithMeetings(course, meetings));
        return 1;
    }

    private static int findStructuredCourseIndex(
            List<StructuredCourse> structuredCourses, String stableId) {
        for (int index = 0; index < structuredCourses.size(); index++) {
            StructuredCourse course = structuredCourses.get(index);
            if (course != null && stableId.equalsIgnoreCase(course.id)) {
                return index;
            }
        }
        return -1;
    }

    private static int findMeetingIndex(StructuredCourse course, Course target) {
        if (StableMeetingId.isValid(target.meetingId)) {
            for (int index = 0; index < course.meetings.size(); index++) {
                CourseMeeting meeting = course.meetings.get(index);
                if (meeting != null && target.meetingId.equalsIgnoreCase(meeting.id)) {
                    return index;
                }
            }
            return -1;
        }
        return findMeetingIndexByLegacySignature(course, target);
    }

    /** Compatibility fallback for pre-v2 views and old share/recovery payloads. */
    private static int findMeetingIndexByLegacySignature(
            StructuredCourse course, Course target) {
        int slotFallback = -1;
        for (int index = 0; index < course.meetings.size(); index++) {
            CourseMeeting meeting = course.meetings.get(index);
            if (meeting.day != target.day
                    || meeting.startSection != target.startSection
                    || meeting.endSection != target.endSection) {
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

    private static List<Integer> remainingWeeks(
            WeekRule rule, int deletedWeek, int semesterWeeks) {
        int boundedWeeks = Math.max(deletedWeek, Math.max(1, semesterWeeks));
        List<Integer> remaining = new ArrayList<>();
        for (int week = 1; week <= boundedWeeks; week++) {
            if (week != deletedWeek && (rule == null || rule.containsWeek(week))) {
                remaining.add(week);
            }
        }
        return remaining;
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
        return normalizedName(first).equals(normalizedName(second));
    }

    /** Legacy-only flat-list helper retained for compatibility tests and old integrations. */
    @Deprecated
    public static int delete(
            List<Course> courses,
            Course target,
            CourseDeletionScope scope,
            int currentWeek,
            int semesterWeeks) {
        if (courses == null || target == null || scope == null) {
            return 0;
        }
        switch (scope) {
            case CURRENT_WEEK:
                return deleteCurrentWeek(courses, target, currentWeek, semesterWeeks);
            case CURRENT_MEETING:
                return removeMatching(courses, target, true);
            case ALL_MEETINGS:
                return removeMatching(courses, target, false);
            default:
                return 0;
        }
    }

    static boolean isActiveInWeek(Course course, int week) {
        return CourseTimeResolver.isActiveInWeek(course, week);
    }

    static boolean compactSplitWeekEntries(List<Course> courses) {
        if (courses == null || courses.size() < 2) {
            return false;
        }
        Map<String, List<Course>> groups = new LinkedHashMap<>();
        for (Course course : courses) {
            if (!isSingleWeekEntry(course)) {
                continue;
            }
            String key = matchingFieldsKey(course);
            List<Course> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(course);
        }

        boolean changed = false;
        for (List<Course> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            Course first = group.get(0);
            int firstIndex = courses.indexOf(first);
            List<Integer> weeks = new ArrayList<>();
            for (Course course : group) {
                int week = WEEK_RULE_PARSER.parse(course.weeks).lastReferencedWeek();
                if (!weeks.contains(week)) {
                    weeks.add(week);
                }
            }
            java.util.Collections.sort(weeks);
            courses.set(firstIndex, copyWithWeeks(first, compactWeekExpression(weeks)));
            for (int index = group.size() - 1; index >= 1; index--) {
                courses.remove(group.get(index));
            }
            changed = true;
        }
        return changed;
    }

    private static int deleteCurrentWeek(
            List<Course> courses, Course target, int currentWeek, int semesterWeeks) {
        int index = courses.indexOf(target);
        if (index < 0 || currentWeek <= 0 || !isActiveInWeek(target, currentWeek)) {
            return 0;
        }
        courses.remove(index);
        int boundedWeeks = Math.max(currentWeek, Math.max(1, semesterWeeks));
        List<Integer> remainingWeeks = new ArrayList<>();
        for (int week = 1; week <= boundedWeeks; week++) {
            if (week != currentWeek && isActiveInWeek(target, week)) {
                remainingWeeks.add(week);
            }
        }
        if (!remainingWeeks.isEmpty()) {
            courses.add(index, copyWithWeeks(target, compactWeekExpression(remainingWeeks)));
        }
        return 1;
    }

    private static int removeMatching(List<Course> courses, Course target, boolean sameMeetingOnly) {
        int removed = 0;
        for (int index = courses.size() - 1; index >= 0; index--) {
            Course candidate = courses.get(index);
            if (sameCourse(candidate, target)
                    && (!sameMeetingOnly || sameMeeting(candidate, target))) {
                courses.remove(index);
                removed++;
            }
        }
        return removed;
    }

    private static boolean sameCourse(Course first, Course second) {
        if (first == null || second == null) {
            return false;
        }
        boolean firstHasStableId = StableCourseId.isValid(first.structuredCourseId);
        boolean secondHasStableId = StableCourseId.isValid(second.structuredCourseId);
        if (firstHasStableId || secondHasStableId) {
            return firstHasStableId
                    && secondHasStableId
                    && first.structuredCourseId.equalsIgnoreCase(second.structuredCourseId)
                    && normalizedName(first.teacher).equals(normalizedName(second.teacher));
        }
        String firstName = normalizedName(first.name);
        String secondName = normalizedName(second.name);
        if (firstName.length() == 0 || secondName.length() == 0) {
            return first == second;
        }
        return firstName.equals(secondName)
                && normalizedName(first.teacher).equals(normalizedName(second.teacher));
    }

    private static boolean sameMeeting(Course first, Course second) {
        return first.day == second.day
                && first.startSection == second.startSection
                && first.endSection == second.endSection;
    }

    private static String normalizedName(String value) {
        return value == null ? "" : value.trim();
    }

    private static Course copyWithWeeks(Course source, String weeks) {
        return new Course(
                source.day,
                source.startSection,
                source.endSection,
                source.name,
                weeks,
                source.location,
                source.teacher,
                source.raw,
                source.credit,
                source.color,
                source.courseType,
                source.structuredCourseId,
                source.meetingId);
    }

    private static String compactWeekExpression(List<Integer> weeks) {
        StringBuilder expression = new StringBuilder();
        int index = 0;
        while (index < weeks.size()) {
            int start = weeks.get(index);
            int end = start;
            while (index + 1 < weeks.size() && weeks.get(index + 1) == end + 1) {
                index++;
                end = weeks.get(index);
            }
            if (expression.length() > 0) {
                expression.append('、');
            }
            expression.append(start);
            if (end > start) {
                expression.append('-').append(end);
            }
            index++;
        }
        return expression.append('周').toString();
    }

    private static boolean isSingleWeekEntry(Course course) {
        if (course == null) {
            return false;
        }
        WeekRule rule = WEEK_RULE_PARSER.parse(course.weeks);
        return rule.lastReferencedWeek() > 0
                && (rule.explicitWeeks.size() == 1
                || (rule.explicitWeeks.isEmpty()
                && rule.startWeek > 0
                && rule.startWeek == rule.endWeek));
    }

    private static String matchingFieldsKey(Course course) {
        return course.day + "|" + course.startSection + "|" + course.endSection + "|"
                + safe(course.name) + "|" + safe(course.location) + "|" + safe(course.teacher) + "|"
                + safe(course.raw) + "|" + safe(course.credit) + "|" + safe(course.color) + "|"
                + course.courseType.name() + "|" + safe(course.structuredCourseId);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
