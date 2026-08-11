package com.polaris.timetable.importer.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts structured import issues into concise user-facing Chinese messages. */
public final class AiImportIssueFormatter {
    private AiImportIssueFormatter() {
    }

    public static List<String> formatAll(List<AiImportIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> messages = new ArrayList<>();
        for (AiImportIssue issue : issues) {
            if (issue != null) {
                messages.add(format(issue));
            }
        }
        return Collections.unmodifiableList(messages);
    }

    public static String format(AiImportIssue issue) {
        if (issue == null) {
            return "AI 识别结果包含未知错误";
        }
        String problem = problemText(issue);
        String context = contextText(issue);
        return context.isEmpty() ? problem : context + "\n" + problem;
    }

    private static String contextText(AiImportIssue issue) {
        StringBuilder context = new StringBuilder();
        if (issue.practiceCourseIndex >= 0) {
            context.append("实践课程");
        } else if (!issue.courseName.trim().isEmpty()) {
            context.append("课程“").append(issue.courseName).append('”');
        } else if (issue.courseIndex >= 0) {
            context.append("第 ").append(issue.courseIndex + 1).append(" 门课程");
        }
        if (issue.meetingIndex >= 0) {
            if (context.length() > 0) {
                context.append(" · ");
            }
            context.append("第 ").append(issue.meetingIndex + 1).append(" 个上课时间");
        }
        return context.toString();
    }

    private static String problemText(AiImportIssue issue) {
        switch (issue.code) {
            case JSON_NOT_FOUND:
                return "无法找到 POLARIS_SCHEDULE_V1 数据或完整 JSON";
            case PARSE_ERROR:
                return "JSON 格式错误，请确认 AI 返回内容完整且没有额外改写";
            case INVALID_FORMAT:
                return "识别结果不是 Polaris Schedule JSON v1";
            case INVALID_WEEK_RULE:
                return "周次格式无法识别";
            case NO_IMPORTABLE_COURSES:
                return "没有识别到可导入课程";
            case MAPPING_ERROR:
                return "课程候选转换失败，请检查识别结果后重试";
            case PRACTICE_COURSE_NOT_MAPPABLE:
                return "该课程没有固定上课时间，当前不会作为普通课表块导入";
            default:
                return fieldProblem(issue);
        }
    }

    private static String fieldProblem(AiImportIssue issue) {
        if ("dayOfWeek".equals(issue.field)) {
            return "星期字段非法，必须是 0 到 6";
        }
        if ("startSection".equals(issue.field)) {
            return "开始节次必须大于 0";
        }
        if ("endSection".equals(issue.field)) {
            return "结束节次不能早于开始节次";
        }
        if ("weekRule".equals(issue.field)) {
            return "周次不能为空或格式无法识别";
        }
        if ("name".equals(issue.field)) {
            return "课程名称不能为空";
        }
        if ("credit".equals(issue.field)) {
            return "学分必须大于 0";
        }
        if (issue.message != null && !issue.message.trim().isEmpty()) {
            return issue.message;
        }
        return "识别结果字段不符合 Polaris 协议";
    }
}
