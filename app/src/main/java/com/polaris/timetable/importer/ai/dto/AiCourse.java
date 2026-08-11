package com.polaris.timetable.importer.ai.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Course candidate from AI output. It is deliberately separate from persisted models. */
public final class AiCourse {
    public final String name;
    public final String teacher;
    public final String teachingClass;
    public final Double credit;
    public final List<AiMeeting> meetings;

    public AiCourse(String name, String teacher, String teachingClass, Double credit,
                    List<AiMeeting> meetings) {
        this.name = name;
        this.teacher = teacher;
        this.teachingClass = teachingClass;
        this.credit = credit;
        this.meetings = meetings == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(meetings));
    }
}
