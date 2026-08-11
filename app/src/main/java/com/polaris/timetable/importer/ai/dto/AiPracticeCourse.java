package com.polaris.timetable.importer.ai.dto;

/** Practice-course candidate from AI output. */
public final class AiPracticeCourse {
    public final String name;
    public final String teacher;
    public final String weekRule;

    public AiPracticeCourse(String name, String teacher, String weekRule) {
        this.name = name;
        this.teacher = teacher;
        this.weekRule = weekRule;
    }
}
