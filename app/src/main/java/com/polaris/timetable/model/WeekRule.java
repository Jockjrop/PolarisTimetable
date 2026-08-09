package com.polaris.timetable.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WeekRule {
    public enum Type {
        RANGE,
        ODD,
        EVEN,
        PROJECT,
        ALL,
        UNKNOWN
    }

    public final Type type;
    public final int startWeek;
    public final int endWeek;
    public final List<Integer> explicitWeeks;
    public final String rawText;

    public WeekRule(Type type, int startWeek, int endWeek, List<Integer> explicitWeeks, String rawText) {
        this.type = type;
        this.startWeek = startWeek;
        this.endWeek = endWeek;
        this.explicitWeeks = explicitWeeks == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(explicitWeeks));
        this.rawText = rawText == null ? "" : rawText;
    }

    public boolean containsWeek(int week) {
        if (week <= 0) {
            return false;
        }
        if (!explicitWeeks.isEmpty()) {
            return explicitWeeks.contains(week);
        }
        switch (type) {
            case ALL:
                return true;
            case RANGE:
                return week >= startWeek && week <= endWeek;
            case ODD:
                return isWithinBounds(week) && week % 2 == 1;
            case EVEN:
                return isWithinBounds(week) && week % 2 == 0;
            case PROJECT:
                return true;
            case UNKNOWN:
                return true;
            default:
                return false;
        }
    }

    public int lastReferencedWeek() {
        int lastWeek = 0;
        for (Integer week : explicitWeeks) {
            if (week != null) {
                lastWeek = Math.max(lastWeek, week);
            }
        }
        if (lastWeek > 0) {
            return lastWeek;
        }
        switch (type) {
            case RANGE:
            case ODD:
            case EVEN:
                return Math.max(0, endWeek);
            case PROJECT:
            case ALL:
            case UNKNOWN:
            default:
                return 0;
        }
    }

    private boolean isWithinBounds(int week) {
        return (startWeek <= 0 || week >= startWeek)
                && (endWeek <= 0 || week <= endWeek);
    }

    public String displayText() {
        if (rawText.length() > 0) {
            return rawText;
        }
        switch (type) {
            case ALL:
                return "全周";
            case RANGE:
                return startWeek + "-" + endWeek + "周";
            case ODD:
                return startWeek + "-" + endWeek + "周(单)";
            case EVEN:
                return startWeek + "-" + endWeek + "周(双)";
            case PROJECT:
                return "项目周";
            case UNKNOWN:
            default:
                return "周次见PDF";
        }
    }
}
