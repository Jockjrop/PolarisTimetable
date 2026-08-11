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
    public final CourseTimeMode timeMode;
    public final int startMinuteOfDay;
    public final int endMinuteOfDay;

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
        this(id, day, startSection, endSection, weekRule, location, teacher, rawText,
                CourseTimeMode.normalize(null, day, startSection, endSection, -1, -1),
                -1, -1);
    }

    public CourseMeeting(String id, int day, int startSection, int endSection, WeekRule weekRule,
                         String location, String teacher, String rawText,
                         CourseTimeMode timeMode, int startMinuteOfDay, int endMinuteOfDay) {
        this.id = id == null ? "" : id;
        this.day = day;
        this.startSection = startSection;
        this.endSection = endSection;
        this.weekRule = weekRule;
        this.location = location == null ? "" : location;
        this.teacher = teacher == null ? "" : teacher;
        this.rawText = rawText == null ? "" : rawText;
        this.timeMode = CourseTimeMode.normalize(
                timeMode, day, startSection, endSection, startMinuteOfDay, endMinuteOfDay);
        this.startMinuteOfDay = this.timeMode == CourseTimeMode.CLOCK
                ? startMinuteOfDay : -1;
        this.endMinuteOfDay = this.timeMode == CourseTimeMode.CLOCK
                ? endMinuteOfDay : -1;
    }

    public CourseMeeting(int day, int startSection, int endSection, WeekRule weekRule,
                         String location, String teacher, String rawText,
                         CourseTimeMode timeMode, int startMinuteOfDay, int endMinuteOfDay) {
        this(StableMeetingId.create(), day, startSection, endSection, weekRule,
                location, teacher, rawText, timeMode, startMinuteOfDay, endMinuteOfDay);
    }

    public boolean isActiveInWeek(int week) {
        return weekRule == null || weekRule.containsWeek(week);
    }

    public boolean hasExactTime() {
        return timeMode == CourseTimeMode.CLOCK;
    }
}
