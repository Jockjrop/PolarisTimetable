package com.polaris.timetable.importer.ai;

import com.polaris.timetable.importer.ai.dto.AiCourse;
import com.polaris.timetable.importer.ai.dto.AiMeeting;
import com.polaris.timetable.importer.ai.dto.AiPracticeCourse;
import com.polaris.timetable.importer.ai.dto.AiScheduleImport;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.parser.WeekRuleParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Maps an already validated AI DTO to unsaved StructuredCourse candidates. */
public final class AiScheduleImportMapper {
    private static final int UNSCHEDULED_DAY = -1;
    private static final int UNSCHEDULED_SECTION = 0;

    private final WeekRuleParser weekRuleParser;

    public AiScheduleImportMapper() {
        this(new WeekRuleParser());
    }

    AiScheduleImportMapper(WeekRuleParser weekRuleParser) {
        this.weekRuleParser = weekRuleParser;
    }

    public AiScheduleImportPlan map(AiScheduleImport source) {
        if (source == null) {
            throw new IllegalArgumentException(
                    "AiScheduleImportMapper requires a validated import candidate");
        }

        List<StructuredCourse> candidates = new ArrayList<>();
        List<AiImportIssue> warnings = new ArrayList<>();
        List<AiPracticeCourse> unresolvedPracticeCourses = new ArrayList<>();

        for (AiCourse course : source.courses) {
            candidates.add(mapCourse(course));
        }
        for (int index = 0; index < source.practiceCourses.size(); index++) {
            AiPracticeCourse practiceCourse = source.practiceCourses.get(index);
            if (CourseType.PRACTICE.supportsBannerOnly()) {
                candidates.add(mapPracticeCourse(practiceCourse));
            } else {
                unresolvedPracticeCourses.add(practiceCourse);
                warnings.add(AiImportIssue.mappingWarning(
                        AiImportIssue.Code.PRACTICE_COURSE_NOT_MAPPABLE,
                        safe(practiceCourse.name),
                        index,
                        "该实践课程没有固定上课时间，当前领域模型无法安全表示"));
            }
        }

        return new AiScheduleImportPlan(source.semester, candidates, warnings,
                unresolvedPracticeCourses);
    }

    private StructuredCourse mapCourse(AiCourse source) {
        List<CourseMeeting> meetings = new ArrayList<>();
        for (AiMeeting meeting : source.meetings) {
            meetings.add(new CourseMeeting(
                    meeting.dayOfWeek,
                    meeting.startSection,
                    meeting.endSection,
                    weekRuleParser.parse(meeting.weekRule),
                    meetingLocation(meeting),
                    "",
                    ""));
        }
        return new StructuredCourse(
                StableCourseId.create(),
                safe(source.name),
                safe(source.teacher),
                "",
                meetings,
                "",
                creditText(source.credit),
                "",
                CourseType.LECTURE);
    }

    private StructuredCourse mapPracticeCourse(AiPracticeCourse source) {
        CourseMeeting unscheduledMeeting = new CourseMeeting(
                UNSCHEDULED_DAY,
                UNSCHEDULED_SECTION,
                UNSCHEDULED_SECTION,
                weekRuleParser.parse(source.weekRule),
                "",
                "",
                "",
                CourseTimeMode.NONE,
                -1,
                -1);
        return new StructuredCourse(
                StableCourseId.create(),
                safe(source.name),
                safe(source.teacher),
                "",
                Collections.singletonList(unscheduledMeeting),
                "",
                "",
                "",
                CourseType.PRACTICE);
    }

    private String meetingLocation(AiMeeting meeting) {
        String campus = safe(meeting.campus);
        String location = safe(meeting.location);
        if (campus.trim().isEmpty()) {
            return location;
        }
        if (location.trim().isEmpty()) {
            return campus;
        }
        if (campus.trim().equals(location.trim())) {
            return location;
        }
        return campus + " " + location;
    }

    private String creditText(Double credit) {
        return credit == null ? "" : Double.toString(credit);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
