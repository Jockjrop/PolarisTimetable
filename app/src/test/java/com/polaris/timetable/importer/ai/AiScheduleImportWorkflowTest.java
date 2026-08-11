package com.polaris.timetable.importer.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.importer.ScheduleImportPreviewData;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.StructuredCourse;

import org.junit.Test;

import java.util.List;

public class AiScheduleImportWorkflowTest {
    private final AiScheduleImportWorkflow workflow = new AiScheduleImportWorkflow();

    @Test
    public void clipboardTextCanEnterFullPreparePipeline() {
        String clipboardText = AiScheduleImportWorkflowTest.validText();

        AiScheduleImportWorkflow.PrepareResult result = workflow.prepare(clipboardText);

        assertTrue(result instanceof AiScheduleImportWorkflow.PrepareSuccess);
        assertEquals("操作系统", success(result).preview.courses.get(0).name);
    }

    @Test
    public void parserFailureDoesNotProducePreviewCandidates() {
        AiScheduleImportWorkflow.PrepareResult result = workflow.prepare(
                "POLARIS_SCHEDULE_V1\n{broken json}");

        assertTrue(result instanceof AiScheduleImportWorkflow.PrepareFailure);
        AiScheduleImportWorkflow.PrepareFailure failure =
                (AiScheduleImportWorkflow.PrepareFailure) result;
        assertFalse(failure.errors.isEmpty());
        assertEquals(AiImportIssue.Code.PARSE_ERROR, failure.errors.get(0).code);
    }

    @Test
    public void successProducesStructuredPreviewCandidates() {
        AiScheduleImportWorkflow.PrepareSuccess success = success(
                workflow.prepare(validText()));

        assertEquals("AI 识别", success.preview.sourceLabel);
        assertEquals(1, success.preview.regularCourseCount());
        assertEquals(0, success.preview.practiceCourseCount());
        assertEquals(1, success.preview.courses.size());
        assertEquals(1, success.plan.courses.size());
    }

    @Test
    public void multipleMeetingsStayUnderOnePreviewCourse() {
        String text = validText().replace(
                "}] }],\"practiceCourses\":[]}",
                "},{\"dayOfWeek\":0,\"startSection\":5,\"endSection\":6,"
                        + "\"weekRule\":\"2-18周(双)\",\"campus\":null,"
                        + "\"location\":\"A101\"}] }],\"practiceCourses\":[]}");

        AiScheduleImportWorkflow.PrepareSuccess success = success(workflow.prepare(text));

        assertEquals(1, success.preview.courses.size());
        assertEquals(2, success.preview.courses.get(0).meetings.size());
    }

    @Test
    public void practiceCourseIsPreviewedAsUnscheduled() {
        String text = "POLARIS_SCHEDULE_V1\n{"
                + "\"format\":\"polaris-schedule-v1\","
                + "\"semester\":null,\"courses\":[],"
                + "\"practiceCourses\":[{\"name\":\"物联网智能计算实验\","
                + "\"teacher\":\"王宏刚\",\"weekRule\":\"9-16周\"}]}";

        ScheduleImportPreviewData preview = success(workflow.prepare(text)).preview;

        assertEquals(0, preview.regularCourseCount());
        assertEquals(1, preview.practiceCourseCount());
        assertEquals(CourseTimeMode.NONE,
                preview.courses.get(0).meetings.get(0).timeMode);
    }

    @Test
    public void semesterIsPreviewMetadataOnly() {
        AiScheduleImportWorkflow.PrepareSuccess success = success(
                workflow.prepare(validText()));

        assertEquals("2026-2027学年第1学期", success.preview.semester);
        assertEquals("2026-2027学年第1学期", success.source.semester);
        assertEquals("操作系统", success.preview.courses.get(0).name);
    }

    @Test
    public void teachingClassIsPreviewOnlyAndNotWrittenIntoDomainFields() {
        AiScheduleImportWorkflow.PrepareSuccess success = success(
                workflow.prepare(validText()));
        StructuredCourse course = success.preview.courses.get(0);
        List<ScheduleImportPreviewData.Detail> details =
                success.preview.detailsFor(course);

        assertEquals(1, details.size());
        assertEquals("教学班", details.get(0).label);
        assertEquals("操作系统-0003", details.get(0).value);
        assertEquals("仅本次识别信息", details.get(0).note);
        assertFalse(course.rawText.contains("操作系统-0003"));
        assertFalse(course.teacher.contains("操作系统-0003"));
        assertFalse(course.defaultLocation.contains("操作系统-0003"));
        assertFalse(course.meetings.get(0).location.contains("操作系统-0003"));
    }

    @Test
    public void emptyValidatedResultDoesNotOpenSuccessfulPreview() {
        String text = "{\"format\":\"polaris-schedule-v1\",\"semester\":null,"
                + "\"courses\":[],\"practiceCourses\":[]}";

        AiScheduleImportWorkflow.PrepareResult result = workflow.prepare(text);

        assertTrue(result instanceof AiScheduleImportWorkflow.PrepareFailure);
        AiScheduleImportWorkflow.PrepareFailure failure =
                (AiScheduleImportWorkflow.PrepareFailure) result;
        assertEquals(AiImportIssue.Code.NO_IMPORTABLE_COURSES,
                failure.errors.get(0).code);
    }

    @Test
    public void issueFormatterHidesParserImplementationDetails() {
        AiScheduleImportWorkflow.PrepareFailure failure =
                (AiScheduleImportWorkflow.PrepareFailure) workflow.prepare("{broken}");

        String message = AiImportIssueFormatter.format(failure.errors.get(0));

        assertTrue(message.contains("JSON 格式错误"));
        assertFalse(message.contains("JSONException"));
        assertFalse(message.contains("NullPointerException"));
    }

    private AiScheduleImportWorkflow.PrepareSuccess success(
            AiScheduleImportWorkflow.PrepareResult result) {
        assertTrue(result instanceof AiScheduleImportWorkflow.PrepareSuccess);
        return (AiScheduleImportWorkflow.PrepareSuccess) result;
    }

    private static String validText() {
        return "POLARIS_SCHEDULE_V1\n{"
                + "\"format\":\"polaris-schedule-v1\","
                + "\"semester\":\"2026-2027学年第1学期\","
                + "\"courses\":[{\"name\":\"操作系统\",\"teacher\":\"梁琛\","
                + "\"teachingClass\":\"操作系统-0003\",\"credit\":3.0,"
                + "\"meetings\":[{\"dayOfWeek\":2,\"startSection\":1,"
                + "\"endSection\":2,\"weekRule\":\"1-17周(单)\","
                + "\"campus\":\"长安校区西区\",\"location\":\"B122\"}] }],"
                + "\"practiceCourses\":[]}";
    }
}
