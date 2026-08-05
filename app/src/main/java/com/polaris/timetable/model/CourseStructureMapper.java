package com.polaris.timetable.model;

import com.polaris.timetable.Course;
import com.polaris.timetable.parser.WeekRuleParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CourseStructureMapper {
    private final WeekRuleParser weekRuleParser = new WeekRuleParser();

    public List<StructuredCourse> fromLegacyCourses(List<Course> legacyCourses) {
        Map<String, CourseBuilder> builders = new LinkedHashMap<>();
        if (legacyCourses == null) {
            return new ArrayList<>();
        }

        for (Course course : legacyCourses) {
            String key = keyFor(course);
            CourseBuilder builder = builders.get(key);
            if (builder == null) {
                builder = new CourseBuilder(
                        key, course.name, course.teacher, course.location, course.credit, course.color);
                builders.put(key, builder);
            }
            builder.addMeeting(new CourseMeeting(
                    course.day,
                    course.startSection,
                    course.endSection,
                    weekRuleParser.parse(course.weeks),
                    course.location,
                    course.teacher,
                    course.raw));
        }

        List<StructuredCourse> structuredCourses = new ArrayList<>();
        for (CourseBuilder builder : builders.values()) {
            structuredCourses.add(builder.build());
        }
        return structuredCourses;
    }

    public List<Course> toLegacyCourses(List<StructuredCourse> structuredCourses) {
        List<Course> legacyCourses = new ArrayList<>();
        if (structuredCourses == null) {
            return legacyCourses;
        }
        for (StructuredCourse course : structuredCourses) {
            if (course == null) {
                continue;
            }
            for (CourseMeeting meeting : course.meetings) {
                if (meeting == null) {
                    continue;
                }
                String location = meeting.location.isEmpty()
                        ? course.defaultLocation
                        : meeting.location;
                String teacher = meeting.teacher.isEmpty()
                        ? course.teacher
                        : meeting.teacher;
                String rawText = meeting.rawText.isEmpty()
                        ? course.rawText
                        : meeting.rawText;
                legacyCourses.add(new Course(
                        meeting.day,
                        meeting.startSection,
                        meeting.endSection,
                        course.name,
                        meeting.weekRule == null ? "周次见PDF" : meeting.weekRule.displayText(),
                        location,
                        teacher,
                        rawText,
                        course.credit,
                        course.color));
            }
        }
        return legacyCourses;
    }

    private String keyFor(Course course) {
        return normalize(course.name) + "|" + normalize(course.teacher)
                + "|" + normalize(course.credit) + "|" + normalize(course.color);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private static class CourseBuilder {
        final String id;
        final String name;
        final String teacher;
        final String defaultLocation;
        final String credit;
        final String color;
        final List<CourseMeeting> meetings = new ArrayList<>();
        final StringBuilder rawText = new StringBuilder();

        CourseBuilder(String id, String name, String teacher, String defaultLocation,
                      String credit, String color) {
            this.id = id;
            this.name = name;
            this.teacher = teacher;
            this.defaultLocation = defaultLocation;
            this.credit = credit;
            this.color = color;
        }

        void addMeeting(CourseMeeting meeting) {
            meetings.add(meeting);
            if (meeting.rawText.length() > 0) {
                if (rawText.length() > 0) {
                    rawText.append('\n');
                }
                rawText.append(meeting.rawText);
            }
        }

        StructuredCourse build() {
            return new StructuredCourse(
                    id, name, teacher, defaultLocation, meetings, rawText.toString(), credit, color);
        }
    }
}
