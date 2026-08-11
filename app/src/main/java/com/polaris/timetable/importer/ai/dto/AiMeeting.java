package com.polaris.timetable.importer.ai.dto;

/** One meeting candidate from AI output. weekRule retains the original text verbatim. */
public final class AiMeeting {
    public final int dayOfWeek;
    public final int startSection;
    public final int endSection;
    public final String weekRule;
    public final String campus;
    public final String location;

    public AiMeeting(int dayOfWeek, int startSection, int endSection, String weekRule,
                     String campus, String location) {
        this.dayOfWeek = dayOfWeek;
        this.startSection = startSection;
        this.endSection = endSection;
        this.weekRule = weekRule;
        this.campus = campus;
        this.location = location;
    }
}
