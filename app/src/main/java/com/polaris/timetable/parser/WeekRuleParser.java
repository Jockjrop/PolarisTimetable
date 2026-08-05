package com.polaris.timetable.parser;

import com.polaris.timetable.model.WeekRule;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeekRuleParser {
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*周?(?:\\s*[（(](单|双)[）)])?");
    private static final Pattern SINGLE_WEEK_PATTERN = Pattern.compile("(\\d+)\\s*周");

    public WeekRule parse(String text) {
        String raw = clean(text);
        if (raw.length() == 0 || "周次见PDF".equals(raw)) {
            return new WeekRule(WeekRule.Type.UNKNOWN, 0, 0, Collections.emptyList(), "周次见PDF");
        }
        if (raw.contains("项目周")) {
            return new WeekRule(WeekRule.Type.PROJECT, 0, 0, Collections.emptyList(), raw);
        }
        if (raw.contains("全周")) {
            return new WeekRule(WeekRule.Type.ALL, 0, 0, Collections.emptyList(), raw);
        }

        Matcher matcher = RANGE_PATTERN.matcher(raw);
        if (!matcher.find()) {
            Matcher singleMatcher = SINGLE_WEEK_PATTERN.matcher(raw);
            if (singleMatcher.find()) {
                int week = Integer.parseInt(singleMatcher.group(1));
                return new WeekRule(WeekRule.Type.RANGE, week, week, Collections.emptyList(), raw);
            }
            return new WeekRule(WeekRule.Type.UNKNOWN, 0, 0, Collections.emptyList(), raw);
        }

        int start = Integer.parseInt(matcher.group(1));
        int end = Integer.parseInt(matcher.group(2));
        String parity = matcher.group(3);
        WeekRule.Type type = WeekRule.Type.RANGE;
        if ("单".equals(parity)) {
            type = WeekRule.Type.ODD;
        } else if ("双".equals(parity)) {
            type = WeekRule.Type.EVEN;
        }
        return new WeekRule(type, start, end, Collections.emptyList(), raw);
    }

    private String clean(String text) {
        String value = text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        return value.startsWith("/") ? value.substring(1).trim() : value;
    }
}
