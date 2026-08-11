package com.polaris.timetable.importer.ai;

import com.polaris.timetable.importer.ai.dto.AiPracticeCourse;
import com.polaris.timetable.model.StructuredCourse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure, immutable candidate plan awaiting user preview and confirmation. */
public final class AiScheduleImportPlan {
    public final String semester;
    public final List<StructuredCourse> courses;
    public final List<AiImportIssue> warnings;
    public final List<AiPracticeCourse> unresolvedPracticeCourses;

    public AiScheduleImportPlan(String semester,
                                List<StructuredCourse> courses,
                                List<AiImportIssue> warnings,
                                List<AiPracticeCourse> unresolvedPracticeCourses) {
        this.semester = semester;
        this.courses = immutableCopy(courses);
        this.warnings = immutableCopy(warnings);
        this.unresolvedPracticeCourses = immutableCopy(unresolvedPracticeCourses);
    }

    public int meetingCount() {
        int count = 0;
        for (StructuredCourse course : courses) {
            if (course != null) {
                count += course.meetings.size();
            }
        }
        return count;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
