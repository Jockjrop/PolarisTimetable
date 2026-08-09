package com.polaris.timetable.parser;

import com.polaris.timetable.model.ParseError;
import com.polaris.timetable.model.ParseResult;

/** Formats one parser result for an in-app, copyable diagnostics report. */
public final class ParseDiagnosticsReport {
    private ParseDiagnosticsReport() {
    }

    public static String summary(ParseResult result) {
        if (result == null) {
            return "暂无导入记录";
        }
        int warnings = count(result, ParseError.Severity.WARNING);
        int errors = count(result, ParseError.Severity.ERROR);
        if (!result.success || errors > 0) {
            return errors > 0 ? "解析失败 · " + errors + " 条错误" : "解析失败";
        }
        if (warnings > 0) {
            return result.courses.size() + " 门课程 · " + warnings + " 条提示";
        }
        return result.courses.size() + " 门课程 · 解析正常";
    }

    public static String build(ParseResult result) {
        if (result == null) {
            return "";
        }
        StringBuilder report = new StringBuilder();
        report.append("解析状态：").append(result.success ? "完成" : "失败").append('\n');
        report.append("PDF 页数：").append(Math.max(0, result.pageCount)).append('\n');
        report.append("识别课程：").append(result.courses.size()).append(" 门").append('\n');
        report.append("警告：").append(count(result, ParseError.Severity.WARNING)).append(" 条").append('\n');
        report.append("错误：").append(count(result, ParseError.Severity.ERROR)).append(" 条");

        if (!result.errors.isEmpty()) {
            report.append("\n\n识别提示");
            for (int index = 0; index < result.errors.size(); index++) {
                ParseError error = result.errors.get(index);
                if (error == null) {
                    continue;
                }
                report.append('\n').append(index + 1).append(". [")
                        .append(severityText(error.severity)).append("] ")
                        .append(error.message.length() == 0
                                ? error.code.name() : error.message);
                if (error.page > 0) {
                    report.append("（第 ").append(error.page).append(" 页）");
                }
                if (error.rawText.trim().length() > 0) {
                    report.append("\n   原文：").append(normalize(error.rawText));
                }
            }
        }
        if (result.diagnosticsText.trim().length() > 0) {
            report.append("\n\n详细日志\n").append(result.diagnosticsText.trim());
        }
        return report.toString();
    }

    private static int count(ParseResult result, ParseError.Severity severity) {
        int count = 0;
        for (ParseError error : result.errors) {
            if (error != null && error.severity == severity) {
                count++;
            }
        }
        return count;
    }

    private static String severityText(ParseError.Severity severity) {
        if (severity == ParseError.Severity.ERROR) {
            return "错误";
        }
        if (severity == ParseError.Severity.WARNING) {
            return "提示";
        }
        return "信息";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
