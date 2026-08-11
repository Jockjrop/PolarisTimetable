package com.polaris.timetable.importer.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.importer.ai.dto.AiScheduleImport;

import org.junit.Test;

public class AiScheduleImportParserTest {
    private final AiScheduleImportParser parser = new AiScheduleImportParser();

    @Test
    public void parsesStandardPolarisScheduleV1Output() {
        AiScheduleImportResult.Success success = assertSuccess(
                AiScheduleImport.OUTPUT_MARKER + "\n" + standardJson());

        assertEquals(AiScheduleImport.FORMAT, success.data.format);
        assertEquals("2026-2027学年第1学期", success.data.semester);
        assertEquals(1, success.data.courses.size());
        assertEquals("操作系统", success.data.courses.get(0).name);
        assertEquals("梁琛", success.data.courses.get(0).teacher);
        assertEquals("操作系统-0003", success.data.courses.get(0).teachingClass);
        assertEquals(Double.valueOf(3.0d), success.data.courses.get(0).credit);
        assertEquals(1, success.data.courses.get(0).meetings.size());
        assertEquals(2, success.data.courses.get(0).meetings.get(0).dayOfWeek);
        assertEquals("1-17周(单)", success.data.courses.get(0).meetings.get(0).weekRule);
        assertEquals("长安校区西区", success.data.courses.get(0).meetings.get(0).campus);
        assertEquals("B122", success.data.courses.get(0).meetings.get(0).location);
        assertTrue(success.warnings.isEmpty());
    }

    @Test
    public void parsesPureJson() {
        assertSuccess(emptyScheduleJson());
    }

    @Test
    public void parsesMarkdownJsonCodeFence() {
        assertSuccess("```json\n" + emptyScheduleJson() + "\n```");
    }

    @Test
    public void extractsJsonSurroundedByAiExplanation() {
        assertSuccess("下面是识别结果，请核对：\n" + emptyScheduleJson()
                + "\n以上结果可能需要人工确认。");
    }

    @Test
    public void markerTakesPriorityOverEarlierJsonObject() {
        AiScheduleImportResult.Success success = assertSuccess(
                "示例对象 {}，正式结果如下：\n"
                        + AiScheduleImport.OUTPUT_MARKER + "\n" + standardJson());
        assertEquals(1, success.data.courses.size());
    }

    @Test
    public void rejectsWrongFormat() {
        AiImportIssue error = firstError(emptyScheduleJson()
                .replace(AiScheduleImport.FORMAT, "polaris-schedule-v2"));
        assertEquals(AiImportIssue.Code.INVALID_FORMAT, error.code);
        assertEquals("format", error.field);
    }

    @Test
    public void rejectsNegativeDayOfWeekWithMeetingContext() {
        AiImportIssue error = firstError(singleCourseJson("课程A", -1, 1, 2, "1-18周", 3.0));
        assertEquals(AiImportIssue.Code.OUT_OF_RANGE, error.code);
        assertEquals(0, error.courseIndex);
        assertEquals("课程A", error.courseName);
        assertEquals(0, error.meetingIndex);
        assertEquals("dayOfWeek", error.field);
    }

    @Test
    public void rejectsDayOfWeekSeven() {
        AiImportIssue error = firstError(singleCourseJson("课程A", 7, 1, 2, "1-18周", 3.0));
        assertEquals("dayOfWeek", error.field);
    }

    @Test
    public void rejectsEndSectionBeforeStartSection() {
        AiImportIssue error = firstError(singleCourseJson("课程A", 0, 4, 3, "1-18周", 3.0));
        assertEquals("endSection", error.field);
    }

    @Test
    public void rejectsNonPositiveStartSection() {
        AiImportIssue error = firstError(singleCourseJson("课程A", 0, 0, 2, "1-18周", 3.0));
        assertEquals("startSection", error.field);
    }

    @Test
    public void rejectsBlankCourseName() {
        AiImportIssue error = firstError(singleCourseJson("   ", 0, 1, 2, "1-18周", 3.0));
        assertEquals(AiImportIssue.Code.REQUIRED_FIELD, error.code);
        assertEquals("name", error.field);
    }

    @Test
    public void existingWeekRuleParserAcceptsOddWeekRule() {
        AiScheduleImportResult.Success success = assertSuccess(
                singleCourseJson("课程A", 0, 1, 2, "1-17周(单)", 3.0));
        assertEquals("1-17周(单)", success.data.courses.get(0).meetings.get(0).weekRule);
    }

    @Test
    public void existingWeekRuleParserAcceptsEvenWeekRule() {
        AiScheduleImportResult.Success success = assertSuccess(
                singleCourseJson("课程A", 0, 1, 2, "2-18周(双)", 3.0));
        assertEquals("2-18周(双)", success.data.courses.get(0).meetings.get(0).weekRule);
    }

    @Test
    public void rejectsUnsupportedWeekRule() {
        AiImportIssue error = firstError(
                singleCourseJson("课程A", 0, 1, 2, "大概前半学期", 3.0));
        assertEquals(AiImportIssue.Code.INVALID_WEEK_RULE, error.code);
        assertEquals("weekRule", error.field);
    }

    @Test
    public void parsesPracticeCourses() {
        String json = "{"
                + "\"format\":\"polaris-schedule-v1\","
                + "\"semester\":null,"
                + "\"courses\":[],"
                + "\"practiceCourses\":[{"
                + "\"name\":\"物联网智能计算实验\","
                + "\"teacher\":\"王宏刚\","
                + "\"weekRule\":\"9-16周\""
                + "}]}";

        AiScheduleImportResult.Success success = assertSuccess(json);
        assertEquals(1, success.data.practiceCourses.size());
        assertEquals("物联网智能计算实验", success.data.practiceCourses.get(0).name);
        assertEquals("王宏刚", success.data.practiceCourses.get(0).teacher);
        assertEquals("9-16周", success.data.practiceCourses.get(0).weekRule);
    }

    @Test
    public void damagedJsonReturnsParseErrorInsteadOfThrowing() {
        AiImportIssue error = firstError(
                AiScheduleImport.OUTPUT_MARKER
                        + "\n{\"format\":\"polaris-schedule-v1\",\"courses\":[");
        assertEquals(AiImportIssue.Code.PARSE_ERROR, error.code);
        assertFalse(error.message.isEmpty());
    }

    @Test
    public void syntacticallyInvalidBalancedJsonReturnsParseError() {
        AiImportIssue error = firstError(
                "{\"format\":\"polaris-schedule-v1\",,\"courses\":[],"
                        + "\"practiceCourses\":[]}");
        assertEquals(AiImportIssue.Code.PARSE_ERROR, error.code);
        assertEquals(AiImportIssue.Stage.PARSING, error.stage);
    }

    @Test
    public void preservesMultipleCoursesAndMultipleMeetingsWithoutMerging() {
        String json = "{"
                + "\"format\":\"polaris-schedule-v1\","
                + "\"courses\":["
                + courseObject("课程A", meetingObject(0, 1, 2, "1-18周") + ","
                + meetingObject(2, 3, 4, "1-17周(单)")) + ","
                + courseObject("课程B", meetingObject(4, 5, 6, "2-18周(双)"))
                + "],\"practiceCourses\":[]}";

        AiScheduleImportResult.Success success = assertSuccess(json);
        assertEquals(2, success.data.courses.size());
        assertEquals("课程A", success.data.courses.get(0).name);
        assertEquals(2, success.data.courses.get(0).meetings.size());
        assertEquals("课程B", success.data.courses.get(1).name);
        assertEquals(1, success.data.courses.get(1).meetings.size());
    }

    @Test
    public void rejectsNonPositiveCredit() {
        AiImportIssue error = firstError(singleCourseJson("课程A", 0, 1, 2, "1-18周", 0.0));
        assertEquals(AiImportIssue.Code.INVALID_VALUE, error.code);
        assertEquals("credit", error.field);
    }

    @Test
    public void rejectsInvalidPracticeCourseFieldsWithPracticeIndex() {
        String json = "{"
                + "\"format\":\"polaris-schedule-v1\","
                + "\"courses\":[],"
                + "\"practiceCourses\":[{\"name\":\" \",\"weekRule\":\"未知\"}]}";

        AiScheduleImportResult.Failure failure = assertFailure(json);
        assertEquals(2, failure.errors.size());
        assertEquals(0, failure.errors.get(0).practiceCourseIndex);
        assertEquals("name", failure.errors.get(0).field);
        assertEquals("weekRule", failure.errors.get(1).field);
    }

    @Test
    public void rejectsWrongJsonFieldTypeWithoutCoercion() {
        String json = singleCourseJson("课程A", 0, 1, 2, "1-18周", 3.0)
                .replace("\"dayOfWeek\":0", "\"dayOfWeek\":\"0\"");
        AiImportIssue error = firstError(json);
        assertEquals(AiImportIssue.Code.FIELD_TYPE, error.code);
        assertEquals(AiImportIssue.Stage.PARSING, error.stage);
        assertEquals("dayOfWeek", error.field);
    }

    @Test
    public void extractorIgnoresBracesAndEscapesInsideJsonStrings() {
        String json = standardJson().replace("\"B122\"", "\"B{122} \\\"东\\\"\"");
        AiScheduleImportResult.Success success = assertSuccess(
                "识别结果：" + json + " 后置说明 {不属于 JSON}");
        assertEquals("B{122} \"东\"", success.data.courses.get(0).meetings.get(0).location);
    }

    private AiScheduleImportResult.Success assertSuccess(String input) {
        AiScheduleImportResult result = parser.parse(input);
        assertTrue(result instanceof AiScheduleImportResult.Success);
        return (AiScheduleImportResult.Success) result;
    }

    private AiScheduleImportResult.Failure assertFailure(String input) {
        AiScheduleImportResult result = parser.parse(input);
        assertTrue(result instanceof AiScheduleImportResult.Failure);
        return (AiScheduleImportResult.Failure) result;
    }

    private AiImportIssue firstError(String input) {
        AiScheduleImportResult.Failure failure = assertFailure(input);
        assertFalse(failure.errors.isEmpty());
        return failure.errors.get(0);
    }

    private String emptyScheduleJson() {
        return "{\"format\":\"polaris-schedule-v1\","
                + "\"semester\":null,\"courses\":[],\"practiceCourses\":[]}";
    }

    private String standardJson() {
        return "{"
                + "\"format\":\"polaris-schedule-v1\","
                + "\"semester\":\"2026-2027学年第1学期\","
                + "\"courses\":[{"
                + "\"name\":\"操作系统\","
                + "\"teacher\":\"梁琛\","
                + "\"teachingClass\":\"操作系统-0003\","
                + "\"credit\":3.0,"
                + "\"meetings\":[{"
                + "\"dayOfWeek\":2,"
                + "\"startSection\":1,"
                + "\"endSection\":2,"
                + "\"weekRule\":\"1-17周(单)\","
                + "\"campus\":\"长安校区西区\","
                + "\"location\":\"B122\""
                + "}]}],"
                + "\"practiceCourses\":[{"
                + "\"name\":\"物联网智能计算实验\","
                + "\"teacher\":\"王宏刚\","
                + "\"weekRule\":\"9-16周\""
                + "}]}";
    }

    private String singleCourseJson(String name, int day, int start, int end,
                                    String weekRule, double credit) {
        return "{"
                + "\"format\":\"polaris-schedule-v1\","
                + "\"courses\":[{"
                + "\"name\":\"" + name + "\","
                + "\"credit\":" + credit + ","
                + "\"meetings\":[" + meetingObject(day, start, end, weekRule) + "]"
                + "}],\"practiceCourses\":[]}";
    }

    private String courseObject(String name, String meetings) {
        return "{\"name\":\"" + name + "\",\"meetings\":[" + meetings + "]}";
    }

    private String meetingObject(int day, int start, int end, String weekRule) {
        return "{\"dayOfWeek\":" + day
                + ",\"startSection\":" + start
                + ",\"endSection\":" + end
                + ",\"weekRule\":\"" + weekRule + "\"}";
    }
}
