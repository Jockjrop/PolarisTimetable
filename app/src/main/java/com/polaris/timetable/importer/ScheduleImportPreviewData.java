package com.polaris.timetable.importer;

import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source-neutral StructuredCourse candidates shown before an import is confirmed. */
public final class ScheduleImportPreviewData {
    public final String sourceLabel;
    public final String semester;
    public final List<StructuredCourse> courses;
    public final List<String> warnings;
    private final Map<String, List<Detail>> detailsByCourseId;

    public ScheduleImportPreviewData(String sourceLabel,
                                     String semester,
                                     List<StructuredCourse> courses,
                                     List<String> warnings,
                                     Map<String, List<Detail>> detailsByCourseId) {
        this.sourceLabel = safe(sourceLabel);
        this.semester = semester;
        this.courses = immutableCopy(courses);
        this.warnings = immutableCopy(warnings);
        this.detailsByCourseId = immutableDetails(detailsByCourseId);
    }

    public static ScheduleImportPreviewData basic(String sourceLabel,
                                                  String semester,
                                                  List<StructuredCourse> courses,
                                                  List<String> warnings) {
        return new ScheduleImportPreviewData(sourceLabel, semester, courses, warnings,
                Collections.emptyMap());
    }

    public List<Detail> detailsFor(StructuredCourse course) {
        if (course == null) {
            return Collections.emptyList();
        }
        List<Detail> details = detailsByCourseId.get(course.id);
        return details == null ? Collections.emptyList() : details;
    }

    public int regularCourseCount() {
        int count = 0;
        for (StructuredCourse course : courses) {
            if (course != null && course.courseType != CourseType.PRACTICE) {
                count++;
            }
        }
        return count;
    }

    public int practiceCourseCount() {
        int count = 0;
        for (StructuredCourse course : courses) {
            if (course != null && course.courseType == CourseType.PRACTICE) {
                count++;
            }
        }
        return count;
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

    public boolean isEmpty() {
        return courses.isEmpty();
    }

    public static final class Detail {
        public final String label;
        public final String value;
        public final String note;

        public Detail(String label, String value, String note) {
            this.label = safe(label);
            this.value = safe(value);
            this.note = safe(note);
        }
    }

    private static Map<String, List<Detail>> immutableDetails(
            Map<String, List<Detail>> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<Detail>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Detail>> entry : source.entrySet()) {
            copy.put(safe(entry.getKey()), immutableCopy(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
