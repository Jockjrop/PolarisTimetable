package com.polaris.timetable.importer.ai.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Candidate schedule data decoded from the external AI interchange protocol. */
public final class AiScheduleImport {
    public static final String OUTPUT_MARKER = "POLARIS_SCHEDULE_V1";
    public static final String FORMAT = "polaris-schedule-v1";

    public final String format;
    public final String semester;
    public final List<AiCourse> courses;
    public final List<AiPracticeCourse> practiceCourses;

    public AiScheduleImport(String format, String semester, List<AiCourse> courses,
                            List<AiPracticeCourse> practiceCourses) {
        this.format = format;
        this.semester = semester;
        this.courses = immutableCopy(courses);
        this.practiceCourses = immutableCopy(practiceCourses);
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
