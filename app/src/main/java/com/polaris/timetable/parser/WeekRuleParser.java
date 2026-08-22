package com.polaris.timetable.parser;

import com.polaris.timetable.model.WeekRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeekRuleParser {
    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)\\s*周?");
    private static final Pattern WEEK_NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final Pattern ODD_PATTERN = Pattern.compile("(?:单周|[（(]\\s*单\\s*[）)])");
    private static final Pattern EVEN_PATTERN = Pattern.compile("(?:双周|[（(]\\s*双\\s*[）)])");

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

        String normalized = normalizeRangeConnectors(raw);
        boolean oddOnly = ODD_PATTERN.matcher(normalized).find();
        boolean evenOnly = EVEN_PATTERN.matcher(normalized).find();
        if (oddOnly && evenOnly) {
            WeekRule mixedParityRule = parseMixedParitySegments(raw);
            if (mixedParityRule != null) {
                return mixedParityRule;
            }
            oddOnly = false;
            evenOnly = false;
        }

        Set<Integer> referencedWeeks = new LinkedHashSet<>();
        boolean[] rangeCharacters = new boolean[normalized.length()];
        Matcher rangeMatcher = RANGE_PATTERN.matcher(normalized);
        int rangeCount = 0;
        int singleRangeStart = 0;
        int singleRangeEnd = 0;
        while (rangeMatcher.find()) {
            int first = positiveInt(rangeMatcher.group(1));
            int second = positiveInt(rangeMatcher.group(2));
            if (first <= 0 || second <= 0) {
                continue;
            }
            int start = Math.min(first, second);
            int end = Math.max(first, second);
            rangeCount++;
            singleRangeStart = start;
            singleRangeEnd = end;
            addRange(referencedWeeks, start, end);
            for (int index = rangeMatcher.start(); index < rangeMatcher.end(); index++) {
                rangeCharacters[index] = true;
            }
        }

        Set<Integer> discreteWeeks = new LinkedHashSet<>();
        Matcher numberMatcher = WEEK_NUMBER_PATTERN.matcher(normalized);
        while (numberMatcher.find()) {
            if (rangeCharacters[numberMatcher.start()]) {
                continue;
            }
            int week = positiveInt(numberMatcher.group());
            if (week > 0) {
                discreteWeeks.add(week);
                referencedWeeks.add(week);
            }
        }

        if (referencedWeeks.isEmpty()) {
            if (oddOnly) {
                return new WeekRule(WeekRule.Type.ODD, 0, 0, Collections.emptyList(), raw);
            }
            if (evenOnly) {
                return new WeekRule(WeekRule.Type.EVEN, 0, 0, Collections.emptyList(), raw);
            }
            return new WeekRule(WeekRule.Type.UNKNOWN, 0, 0, Collections.emptyList(), raw);
        }

        if (oddOnly || evenOnly) {
            final boolean keepOddWeeks = oddOnly;
            Iterator<Integer> weekIterator = referencedWeeks.iterator();
            while (weekIterator.hasNext()) {
                int week = weekIterator.next();
                if (keepOddWeeks ? week % 2 == 0 : week % 2 != 0) {
                    weekIterator.remove();
                }
            }
            if (referencedWeeks.isEmpty()) {
                return new WeekRule(WeekRule.Type.RANGE, 1, 0,
                        Collections.emptyList(), raw);
            }
        }

        WeekRule.Type type = oddOnly
                ? WeekRule.Type.ODD
                : evenOnly ? WeekRule.Type.EVEN : WeekRule.Type.RANGE;
        boolean simpleRange = rangeCount == 1 && discreteWeeks.isEmpty();
        if (simpleRange) {
            return new WeekRule(type, singleRangeStart, singleRangeEnd,
                    Collections.emptyList(), raw);
        }

        List<Integer> explicitWeeks = new ArrayList<>(referencedWeeks);
        Collections.sort(explicitWeeks);
        int start = explicitWeeks.isEmpty() ? 0 : explicitWeeks.get(0);
        int end = explicitWeeks.isEmpty() ? 0 : explicitWeeks.get(explicitWeeks.size() - 1);
        return new WeekRule(type, start, end, explicitWeeks, raw);
    }

    public boolean isSupportedExpression(String text) {
        String raw = clean(text);
        if (raw.length() == 0 || "周次见PDF".equals(raw)) {
            return false;
        }
        String remaining = normalizeRangeConnectors(raw)
                .replaceAll("\\d+", "")
                .replaceAll("[\\s第周单双全项目,，、;；()（）\\-]", "");
        return remaining.length() == 0 && parse(raw).type != WeekRule.Type.UNKNOWN;
    }

    private WeekRule parseMixedParitySegments(String raw) {
        String[] segments = raw.split("[,，、;；]+");
        if (segments.length < 2) {
            return null;
        }
        Set<Integer> weeks = new LinkedHashSet<>();
        for (String segment : segments) {
            WeekRule segmentRule = parse(segment);
            int lastWeek = segmentRule.lastReferencedWeek();
            if (lastWeek <= 0) {
                continue;
            }
            for (int week = 1; week <= lastWeek; week++) {
                if (segmentRule.containsWeek(week)) {
                    weeks.add(week);
                }
            }
        }
        List<Integer> explicitWeeks = new ArrayList<>(weeks);
        Collections.sort(explicitWeeks);
        if (explicitWeeks.isEmpty()) {
            return new WeekRule(WeekRule.Type.RANGE, 1, 0,
                    Collections.emptyList(), raw);
        }
        return new WeekRule(
                WeekRule.Type.RANGE,
                explicitWeeks.get(0),
                explicitWeeks.get(explicitWeeks.size() - 1),
                explicitWeeks,
                raw);
    }

    private void addRange(Set<Integer> weeks, int start, int end) {
        for (int week = start; week <= end; week++) {
            weeks.add(week);
        }
    }

    private int positiveInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String normalizeRangeConnectors(String text) {
        return text
                .replace('‐', '-')
                .replace('‑', '-')
                .replace('‒', '-')
                .replace('–', '-')
                .replace('—', '-')
                .replace('﹘', '-')
                .replace('﹣', '-')
                .replace('－', '-')
                .replace('~', '-')
                .replace('～', '-')
                .replace('〜', '-')
                .replace('∼', '-')
                .replace("至", "-");
    }

    private String clean(String text) {
        String value = text == null ? "" : text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        return value.startsWith("/") ? value.substring(1).trim() : value;
    }
}
