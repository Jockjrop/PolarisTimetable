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
        switch (type) {
            case ALL:
                return true;
            case RANGE:
                return week >= startWeek && week <= endWeek;
            case ODD:
                return week >= startWeek && week <= endWeek && week % 2 == 1;
            case EVEN:
                return week >= startWeek && week <= endWeek && week % 2 == 0;
            case PROJECT:
                return false;
            case UNKNOWN:
                return true;
            default:
                return false;
        }
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
