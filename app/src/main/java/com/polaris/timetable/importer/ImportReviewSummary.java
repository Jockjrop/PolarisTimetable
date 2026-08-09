package com.polaris.timetable.importer;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.ParseError;
import com.polaris.timetable.model.ParseResult;
import com.polaris.timetable.validation.CourseConflictDetector;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable import-check metrics calculated before any schedule data is replaced. */
public final class ImportReviewSummary {
    public final int courseCount;
    public final int meetingCount;
    public final int warningCount;
    public final int errorCount;
    public final int missingLocationCount;
    public final int missingTeacherCount;
    public final int unknownWeekCount;
    public final int conflictCount;
    public final int addedCount;
    public final int modifiedCount;
    public final int removedCount;

    private ImportReviewSummary(int courseCount, int meetingCount,
                                int warningCount, int errorCount,
                                int missingLocationCount, int missingTeacherCount,
                                int unknownWeekCount, int conflictCount,
                                int addedCount, int modifiedCount, int removedCount) {
        this.courseCount = courseCount;
        this.meetingCount = meetingCount;
        this.warningCount = warningCount;
        this.errorCount = errorCount;
        this.missingLocationCount = missingLocationCount;
        this.missingTeacherCount = missingTeacherCount;
        this.unknownWeekCount = unknownWeekCount;
        this.conflictCount = conflictCount;
        this.addedCount = addedCount;
        this.modifiedCount = modifiedCount;
        this.removedCount = removedCount;
    }

    public static ImportReviewSummary analyze(ParseResult result,
                                              List<Course> currentCourses,
                                              int semesterWeeks) {
        List<Course> incoming = result == null
                ? Collections.<Course>emptyList() : result.courses;
        List<Course> current = currentCourses == null
                ? Collections.<Course>emptyList() : currentCourses;
        int warnings = 0;
        int errors = 0;
        if (result != null) {
            for (ParseError error : result.errors) {
                if (error == null) {
                    continue;
                }
                if (error.severity == ParseError.Severity.ERROR) {
                    errors++;
                } else if (error.severity == ParseError.Severity.WARNING) {
                    warnings++;
                }
            }
        }

        int missingLocation = 0;
        int missingTeacher = 0;
        int unknownWeek = 0;
        for (Course course : incoming) {
            if (course == null) {
                continue;
            }
            if (safe(course.location).trim().length() == 0) {
                missingLocation++;
            }
            if (safe(course.teacher).trim().length() == 0) {
                missingTeacher++;
            }
            String weeks = safe(course.weeks).trim();
            if (weeks.length() == 0 || "周次见PDF".equals(weeks)) {
                unknownWeek++;
            }
        }

        Set<String> currentBases = keys(current, false);
        Set<String> currentFull = keys(current, true);
        Set<String> incomingBases = keys(incoming, false);
        int added = 0;
        int modified = 0;
        for (Course course : incoming) {
            if (course == null) {
                continue;
            }
            if (!currentBases.contains(baseKey(course))) {
                added++;
            } else if (!currentFull.contains(fullKey(course))) {
                modified++;
            }
        }
        int removed = 0;
        for (Course course : current) {
            if (course != null && !incomingBases.contains(baseKey(course))) {
                removed++;
            }
        }

        int distinctCourses = result != null && !result.structuredCourses.isEmpty()
                ? result.structuredCourses.size() : distinctNames(incoming).size();
        return new ImportReviewSummary(
                distinctCourses,
                incoming.size(),
                warnings,
                errors,
                missingLocation,
                missingTeacher,
                unknownWeek,
                CourseConflictDetector.findAll(incoming, Math.max(1, semesterWeeks)).size(),
                added,
                modified,
                removed);
    }

    public boolean canImport() {
        return meetingCount > 0;
    }

    public boolean hasIssues() {
        return warningCount > 0 || errorCount > 0 || missingLocationCount > 0
                || missingTeacherCount > 0 || unknownWeekCount > 0 || conflictCount > 0;
    }

    private static Set<String> keys(List<Course> courses, boolean full) {
        Set<String> result = new LinkedHashSet<>();
        for (Course course : courses) {
            if (course != null) {
                result.add(full ? fullKey(course) : baseKey(course));
            }
        }
        return result;
    }

    private static Set<String> distinctNames(List<Course> courses) {
        Set<String> names = new LinkedHashSet<>();
        for (Course course : courses) {
            if (course != null) {
                names.add(normalize(course.name));
            }
        }
        return names;
    }

    private static String baseKey(Course course) {
        return normalize(course.name) + '|' + course.day + '|'
                + course.startSection + '|' + course.endSection;
    }

    private static String fullKey(Course course) {
        return baseKey(course) + '|' + normalize(course.weeks) + '|'
                + normalize(course.location) + '|' + normalize(course.teacher);
    }

    private static String normalize(String value) {
        return safe(value).replaceAll("\\s+", "").trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
