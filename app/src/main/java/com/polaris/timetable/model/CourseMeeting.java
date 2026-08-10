package com.polaris.timetable.model;

public class CourseMeeting {
    public final String id;
    public final int day;
    public final int startSection;
    public final int endSection;
    public final WeekRule weekRule;
    public final String location;
    public final String teacher;
    public final String rawText;

    public CourseMeeting(int day, int startSection, int endSection, WeekRule weekRule,
                         String location, String teacher, String rawText) {
        this(StableMeetingId.create(), day, startSection, endSection, weekRule,
                location, teacher, rawText);
    }

    /**
     * Restores a persisted identity verbatim. Missing or invalid IDs are intentionally retained
     * here so ScheduleStorageSchema is the single place that performs version migration.
     */
    public CourseMeeting(String id, int day, int startSection, int endSection, WeekRule weekRule,
                         String location, String teacher, String rawText) {
        this.id = id == null ? "" : id;
        this.day = day;
        this.startSection = startSection;
        this.endSection = endSection;
        this.weekRule = weekRule;
        this.location = location == null ? "" : location;
        this.teacher = teacher == null ? "" : teacher;
        this.rawText = rawText == null ? "" : rawText;
    }

    public boolean isActiveInWeek(int week) {
        return weekRule == null || weekRule.containsWeek(week);
    }
}
