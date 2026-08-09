package com.polaris.timetable.model;

import com.polaris.timetable.Course;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParseResult {
    public final boolean success;
    public final List<Course> courses;
    public final List<StructuredCourse> structuredCourses;
    public final List<ParseError> errors;
    public final String diagnosticsText;
    public final int pageCount;
    public final String classTimeConfig;
    public final String semesterName;

    public ParseResult(boolean success, List<Course> courses, List<ParseError> errors,
                       String diagnosticsText, int pageCount) {
        this(success, courses, new CourseStructureMapper().fromLegacyCourses(courses), errors, diagnosticsText, pageCount, "");
    }

    public ParseResult(boolean success, List<Course> courses, List<StructuredCourse> structuredCourses,
                       List<ParseError> errors, String diagnosticsText, int pageCount) {
        this(success, courses, structuredCourses, errors, diagnosticsText, pageCount, "");
    }

    public ParseResult(boolean success, List<Course> courses, List<StructuredCourse> structuredCourses,
                       List<ParseError> errors, String diagnosticsText, int pageCount, String classTimeConfig) {
        this(success, courses, structuredCourses, errors, diagnosticsText, pageCount, classTimeConfig, "");
    }

    public ParseResult(boolean success, List<Course> courses, List<StructuredCourse> structuredCourses,
                       List<ParseError> errors, String diagnosticsText, int pageCount,
                       String classTimeConfig, String semesterName) {
        this.success = success;
        this.courses = courses == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(courses));
        this.structuredCourses = structuredCourses == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(structuredCourses));
        this.errors = errors == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(errors));
        this.diagnosticsText = diagnosticsText == null ? "" : diagnosticsText;
        this.pageCount = pageCount;
        this.classTimeConfig = classTimeConfig == null ? "" : classTimeConfig;
        this.semesterName = semesterName == null ? "" : semesterName;
    }
}
