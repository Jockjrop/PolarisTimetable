package com.polaris.timetable.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemesterTextExtractor {
    private static final Pattern NUMBERED_TERM = Pattern.compile(
            "(?<!\\d)(20\\d{2})[-—–至~～/](20\\d{2})学年第?([一二三123])学期");
    private static final Pattern SEASON_TERM = Pattern.compile(
            "(?<!\\d)(20\\d{2})[-—–至~～/](20\\d{2})学年(春季|秋季)学期");

    private SemesterTextExtractor() {
    }

    public static String extract(String sourceText) {
        String text = sourceText == null ? "" : sourceText.replaceAll("\\s+", "");
        Matcher numbered = NUMBERED_TERM.matcher(text);
        if (numbered.find()) {
            return numbered.group(1) + "-" + numbered.group(2) + "学年第"
                    + normalizedTermNumber(numbered.group(3)) + "学期";
        }
        Matcher season = SEASON_TERM.matcher(text);
        if (season.find()) {
            return season.group(1) + "-" + season.group(2) + "学年"
                    + season.group(3) + "学期";
        }
        return "";
    }

    private static String normalizedTermNumber(String value) {
        if ("一".equals(value)) {
            return "1";
        }
        if ("二".equals(value)) {
            return "2";
        }
        if ("三".equals(value)) {
            return "3";
        }
        return value;
    }
}
