package com.polaris.timetable.model;

public class ParseError {
    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public enum Code {
        PDF_OPEN_FAILED,
        NO_TEXT_FOUND,
        DAY_COLUMNS_NOT_FOUND,
        SECTION_NOT_FOUND,
        COURSE_NAME_LOW_CONFIDENCE,
        WEEK_RULE_UNKNOWN,
        LOCATION_NOT_FOUND,
        TEACHER_NOT_FOUND
    }

    public final Severity severity;
    public final Code code;
    public final String message;
    public final int page;
    public final String rawText;

    public ParseError(Severity severity, Code code, String message, int page, String rawText) {
        this.severity = severity;
        this.code = code;
        this.message = message == null ? "" : message;
        this.page = page;
        this.rawText = rawText == null ? "" : rawText;
    }
}
