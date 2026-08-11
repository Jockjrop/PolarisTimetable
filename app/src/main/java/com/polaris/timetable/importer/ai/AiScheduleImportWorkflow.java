package com.polaris.timetable.importer.ai;

import com.polaris.timetable.importer.ScheduleImportPreviewData;
import com.polaris.timetable.importer.ai.dto.AiCourse;
import com.polaris.timetable.importer.ai.dto.AiScheduleImport;
import com.polaris.timetable.model.StructuredCourse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Untrusted AI text -> validated, mapped, unsaved preview candidates. */
public final class AiScheduleImportWorkflow {
    private final AiScheduleImportParser parser;
    private final AiScheduleImportMapper mapper;

    public AiScheduleImportWorkflow() {
        this(new AiScheduleImportParser(), new AiScheduleImportMapper());
    }

    AiScheduleImportWorkflow(AiScheduleImportParser parser,
                             AiScheduleImportMapper mapper) {
        this.parser = parser;
        this.mapper = mapper;
    }

    public PrepareResult prepare(String untrustedText) {
        AiScheduleImportResult parsed = parser.parse(untrustedText);
        if (parsed instanceof AiScheduleImportResult.Failure) {
            return new PrepareFailure(
                    ((AiScheduleImportResult.Failure) parsed).errors);
        }

        AiScheduleImport source = ((AiScheduleImportResult.Success) parsed).data;
        final AiScheduleImportPlan plan;
        try {
            plan = mapper.map(source);
        } catch (RuntimeException exception) {
            return new PrepareFailure(Collections.singletonList(
                    AiImportIssue.mappingError(
                            AiImportIssue.Code.MAPPING_ERROR,
                            "课程候选转换失败")));
        }
        if (plan.courses.isEmpty()) {
            return new PrepareFailure(Collections.singletonList(
                    AiImportIssue.mappingError(
                            AiImportIssue.Code.NO_IMPORTABLE_COURSES,
                            "没有识别到可导入课程")));
        }

        ScheduleImportPreviewData preview = new ScheduleImportPreviewData(
                "AI 识别",
                plan.semester,
                plan.courses,
                AiImportIssueFormatter.formatAll(plan.warnings),
                previewDetails(source, plan));
        return new PrepareSuccess(source, plan, preview);
    }

    private Map<String, List<ScheduleImportPreviewData.Detail>> previewDetails(
            AiScheduleImport source, AiScheduleImportPlan plan) {
        Map<String, List<ScheduleImportPreviewData.Detail>> details =
                new LinkedHashMap<>();
        int normalCount = Math.min(source.courses.size(), plan.courses.size());
        for (int index = 0; index < normalCount; index++) {
            AiCourse sourceCourse = source.courses.get(index);
            StructuredCourse mappedCourse = plan.courses.get(index);
            if (sourceCourse.teachingClass == null
                    || sourceCourse.teachingClass.trim().isEmpty()) {
                continue;
            }
            List<ScheduleImportPreviewData.Detail> courseDetails = new ArrayList<>();
            courseDetails.add(new ScheduleImportPreviewData.Detail(
                    "教学班", sourceCourse.teachingClass, "仅本次识别信息"));
            details.put(mappedCourse.id, courseDetails);
        }
        return details;
    }

    public abstract static class PrepareResult {
        private PrepareResult() {
        }

        public abstract boolean isSuccess();
    }

    public static final class PrepareSuccess extends PrepareResult {
        public final AiScheduleImport source;
        public final AiScheduleImportPlan plan;
        public final ScheduleImportPreviewData preview;

        PrepareSuccess(AiScheduleImport source, AiScheduleImportPlan plan,
                       ScheduleImportPreviewData preview) {
            this.source = source;
            this.plan = plan;
            this.preview = preview;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    public static final class PrepareFailure extends PrepareResult {
        public final List<AiImportIssue> errors;

        PrepareFailure(List<AiImportIssue> errors) {
            this.errors = errors == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(errors));
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }
}
