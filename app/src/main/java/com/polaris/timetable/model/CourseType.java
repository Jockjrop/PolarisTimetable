package com.polaris.timetable.model;

public enum CourseType {
    LECTURE("普通课程", ""),
    EXPERIMENT("实验课程", "实验"),
    PRACTICE("实践课程", "实践"),
    ONLINE("网络课程", "网络");

    public final String displayName;
    public final String badgeText;

    CourseType(String displayName, String badgeText) {
        this.displayName = displayName;
        this.badgeText = badgeText;
    }

    public static CourseType fromStorage(String value) {
        if (value != null) {
            for (CourseType type : values()) {
                if (type.name().equalsIgnoreCase(value)
                        || type.displayName.equals(value)
                        || type.badgeText.equals(value)) {
                    return type;
                }
            }
        }
        return LECTURE;
    }

    public static CourseType fromMarker(char marker) {
        if (marker == '◇') {
            return EXPERIMENT;
        }
        if (marker == '●') {
            return PRACTICE;
        }
        if (marker == '○') {
            return ONLINE;
        }
        return LECTURE;
    }

    public boolean supportsBannerOnly() {
        return this == EXPERIMENT || this == PRACTICE;
    }
}
