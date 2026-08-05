package com.polaris.timetable.parser;

import com.polaris.timetable.model.ParseError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParseDiagnostics {
    private final StringBuilder log = new StringBuilder();
    private final List<ParseError> errors = new ArrayList<>();

    public void info(String message) {
        append("info", message);
    }

    public void warning(ParseError.Code code, String message, int page, String rawText) {
        errors.add(new ParseError(ParseError.Severity.WARNING, code, message, page, rawText));
        append("warning", code + ": " + message);
    }

    public void error(ParseError.Code code, String message, int page, String rawText) {
        errors.add(new ParseError(ParseError.Severity.ERROR, code, message, page, rawText));
        append("error", code + ": " + message);
    }

    public List<ParseError> errors() {
        return Collections.unmodifiableList(errors);
    }

    public String text() {
        return log.toString();
    }

    private void append(String level, String message) {
        log.append('[').append(level).append("] ").append(message == null ? "" : message).append('\n');
    }
}
