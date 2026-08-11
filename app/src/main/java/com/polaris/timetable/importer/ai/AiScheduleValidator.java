package com.polaris.timetable.importer.ai;

import com.polaris.timetable.importer.ai.dto.AiCourse;
import com.polaris.timetable.importer.ai.dto.AiMeeting;
import com.polaris.timetable.importer.ai.dto.AiPracticeCourse;
import com.polaris.timetable.importer.ai.dto.AiScheduleImport;
import com.polaris.timetable.parser.WeekRuleParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Semantic validation for decoded v1 candidates. No value is corrected or normalized. */
public final class AiScheduleValidator {
    private final WeekRuleParser weekRuleParser;

    public AiScheduleValidator() {
        this(new WeekRuleParser());
    }

    AiScheduleValidator(WeekRuleParser weekRuleParser) {
        this.weekRuleParser = weekRuleParser;
    }

    public Result validate(AiScheduleImport data) {
        List<AiImportIssue> errors = new ArrayList<>();
        if (data == null) {
            errors.add(issue(AiImportIssue.Code.INVALID_VALUE,
                    -1, "", -1, -1, "", "导入数据不得为 null"));
            return new Result(errors, Collections.emptyList());
        }
        if (!AiScheduleImport.FORMAT.equals(data.format)) {
            errors.add(issue(AiImportIssue.Code.INVALID_FORMAT,
                    -1, "", -1, -1, "format",
                    "format 必须严格为 " + AiScheduleImport.FORMAT));
        }

        for (int courseIndex = 0; courseIndex < data.courses.size(); courseIndex++) {
            validateCourse(data.courses.get(courseIndex), courseIndex, errors);
        }
        for (int practiceIndex = 0;
             practiceIndex < data.practiceCourses.size(); practiceIndex++) {
            validatePracticeCourse(data.practiceCourses.get(practiceIndex),
                    practiceIndex, errors);
        }
        return new Result(errors, Collections.emptyList());
    }

    private void validateCourse(AiCourse course, int courseIndex,
                                List<AiImportIssue> errors) {
        if (course == null) {
            errors.add(issue(AiImportIssue.Code.INVALID_VALUE,
                    courseIndex, "", -1, -1, "course",
                    "course 不得为 null"));
            return;
        }
        String courseName = course.name == null ? "" : course.name;
        if (isBlank(course.name)) {
            errors.add(issue(AiImportIssue.Code.REQUIRED_FIELD,
                    courseIndex, courseName, -1, -1, "name",
                    "course.name trim 后不得为空"));
        }
        if (course.credit != null
                && (course.credit.isNaN() || course.credit.isInfinite()
                || course.credit <= 0d)) {
            errors.add(issue(AiImportIssue.Code.INVALID_VALUE,
                    courseIndex, courseName, -1, -1, "credit",
                    "credit 若存在必须大于 0"));
        }
        for (int meetingIndex = 0; meetingIndex < course.meetings.size(); meetingIndex++) {
            validateMeeting(course.meetings.get(meetingIndex), courseIndex,
                    courseName, meetingIndex, errors);
        }
    }

    private void validateMeeting(AiMeeting meeting, int courseIndex, String courseName,
                                 int meetingIndex, List<AiImportIssue> errors) {
        if (meeting == null) {
            errors.add(issue(AiImportIssue.Code.INVALID_VALUE,
                    courseIndex, courseName, meetingIndex, -1, "meeting",
                    "meeting 不得为 null"));
            return;
        }
        if (meeting.dayOfWeek < 0 || meeting.dayOfWeek > 6) {
            errors.add(issue(AiImportIssue.Code.OUT_OF_RANGE,
                    courseIndex, courseName, meetingIndex, -1, "dayOfWeek",
                    "dayOfWeek 必须在 0..6 范围内"));
        }
        if (meeting.startSection <= 0) {
            errors.add(issue(AiImportIssue.Code.OUT_OF_RANGE,
                    courseIndex, courseName, meetingIndex, -1, "startSection",
                    "startSection 必须大于 0"));
        }
        if (meeting.endSection < meeting.startSection) {
            errors.add(issue(AiImportIssue.Code.OUT_OF_RANGE,
                    courseIndex, courseName, meetingIndex, -1, "endSection",
                    "endSection 必须大于或等于 startSection"));
        }
        validateWeekRule(meeting.weekRule, courseIndex, courseName,
                meetingIndex, -1, errors);
    }

    private void validatePracticeCourse(AiPracticeCourse course, int practiceIndex,
                                        List<AiImportIssue> errors) {
        if (course == null) {
            errors.add(issue(AiImportIssue.Code.INVALID_VALUE,
                    -1, "", -1, practiceIndex, "practiceCourse",
                    "practiceCourse 不得为 null"));
            return;
        }
        String courseName = course.name == null ? "" : course.name;
        if (isBlank(course.name)) {
            errors.add(issue(AiImportIssue.Code.REQUIRED_FIELD,
                    -1, courseName, -1, practiceIndex, "name",
                    "practiceCourse.name trim 后不得为空"));
        }
        validateWeekRule(course.weekRule, -1, courseName,
                -1, practiceIndex, errors);
    }

    private void validateWeekRule(String weekRule, int courseIndex, String courseName,
                                  int meetingIndex, int practiceIndex,
                                  List<AiImportIssue> errors) {
        if (isBlank(weekRule)) {
            errors.add(issue(AiImportIssue.Code.REQUIRED_FIELD,
                    courseIndex, courseName, meetingIndex, practiceIndex,
                    "weekRule", "weekRule 不得为空"));
        } else if (!weekRuleParser.isSupportedExpression(weekRule)) {
            errors.add(issue(AiImportIssue.Code.INVALID_WEEK_RULE,
                    courseIndex, courseName, meetingIndex, practiceIndex,
                    "weekRule", "weekRule 无法通过现有 WeekRuleParser 验证"));
        }
    }

    private AiImportIssue issue(AiImportIssue.Code code, int courseIndex,
                                String courseName, int meetingIndex,
                                int practiceIndex, String field, String message) {
        return AiImportIssue.validation(code, courseIndex, courseName,
                meetingIndex, practiceIndex, field, message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Result {
        public final List<AiImportIssue> errors;
        public final List<AiImportIssue> warnings;

        Result(List<AiImportIssue> errors, List<AiImportIssue> warnings) {
            this.errors = immutableCopy(errors);
            this.warnings = immutableCopy(warnings);
        }

        public boolean isSuccess() {
            return errors.isEmpty();
        }

        private static <T> List<T> immutableCopy(List<T> values) {
            return values == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(values));
        }
    }
}
