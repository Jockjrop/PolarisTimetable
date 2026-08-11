package com.polaris.timetable.importer.ai;

/** A structured extraction, parsing, or validation issue for a future preview UI. */
public final class AiImportIssue {
    public enum Severity {
        WARNING,
        ERROR
    }

    public enum Stage {
        EXTRACTION,
        PARSING,
        VALIDATION,
        MAPPING
    }

    public enum Code {
        JSON_NOT_FOUND,
        PARSE_ERROR,
        REQUIRED_FIELD,
        FIELD_TYPE,
        INVALID_FORMAT,
        OUT_OF_RANGE,
        INVALID_VALUE,
        INVALID_WEEK_RULE,
        PRACTICE_COURSE_NOT_MAPPABLE,
        NO_IMPORTABLE_COURSES,
        MAPPING_ERROR
    }

    public final Severity severity;
    public final Stage stage;
    public final Code code;
    public final int courseIndex;
    public final String courseName;
    public final int meetingIndex;
    public final int practiceCourseIndex;
    public final String field;
    public final String message;

    public AiImportIssue(Severity severity, Stage stage, Code code,
                         int courseIndex, String courseName, int meetingIndex,
                         int practiceCourseIndex, String field, String message) {
        this.severity = severity;
        this.stage = stage;
        this.code = code;
        this.courseIndex = courseIndex;
        this.courseName = courseName == null ? "" : courseName;
        this.meetingIndex = meetingIndex;
        this.practiceCourseIndex = practiceCourseIndex;
        this.field = field == null ? "" : field;
        this.message = message == null ? "" : message;
    }

    static AiImportIssue extraction(Code code, String message) {
        return new AiImportIssue(Severity.ERROR, Stage.EXTRACTION, code,
                -1, "", -1, -1, "", message);
    }

    static AiImportIssue parsing(Code code, int courseIndex, String courseName,
                                 int meetingIndex, int practiceCourseIndex,
                                 String field, String message) {
        return new AiImportIssue(Severity.ERROR, Stage.PARSING, code,
                courseIndex, courseName, meetingIndex, practiceCourseIndex,
                field, message);
    }

    static AiImportIssue validation(Code code, int courseIndex, String courseName,
                                    int meetingIndex, int practiceCourseIndex,
                                    String field, String message) {
        return new AiImportIssue(Severity.ERROR, Stage.VALIDATION, code,
                courseIndex, courseName, meetingIndex, practiceCourseIndex,
                field, message);
    }

    static AiImportIssue mappingWarning(Code code, String courseName,
                                        int practiceCourseIndex, String message) {
        return new AiImportIssue(Severity.WARNING, Stage.MAPPING, code,
                -1, courseName, -1, practiceCourseIndex, "practiceCourse", message);
    }

    static AiImportIssue mappingError(Code code, String message) {
        return new AiImportIssue(Severity.ERROR, Stage.MAPPING, code,
                -1, "", -1, -1, "", message);
    }
}
