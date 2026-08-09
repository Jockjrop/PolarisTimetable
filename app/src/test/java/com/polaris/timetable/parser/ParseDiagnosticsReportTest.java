package com.polaris.timetable.parser;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.ParseError;
import com.polaris.timetable.model.ParseResult;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParseDiagnosticsReportTest {
    @Test
    public void summary_reportsSuccessfulImportWithoutWarnings() {
        ParseResult result = new ParseResult(true,
                Collections.singletonList(course()), Collections.emptyList(), "", 2);

        assertEquals("1 门课程 · 解析正常", ParseDiagnosticsReport.summary(result));
    }

    @Test
    public void build_includesWarningPageRawTextAndDetailedLog() {
        ParseError warning = new ParseError(ParseError.Severity.WARNING,
                ParseError.Code.TEACHER_NOT_FOUND, "未识别到教师", 2,
                "课程原始  文本");
        ParseResult result = new ParseResult(true,
                Collections.singletonList(course()), Collections.singletonList(warning),
                "course seeds=1", 2);

        String report = ParseDiagnosticsReport.build(result);

        assertEquals("1 门课程 · 1 条提示", ParseDiagnosticsReport.summary(result));
        assertTrue(report.contains("未识别到教师（第 2 页）"));
        assertTrue(report.contains("原文：课程原始 文本"));
        assertTrue(report.contains("详细日志\ncourse seeds=1"));
    }

    @Test
    public void summary_handlesMissingResult() {
        assertEquals("暂无导入记录", ParseDiagnosticsReport.summary(null));
        assertEquals("", ParseDiagnosticsReport.build(null));
    }

    private Course course() {
        return new Course(0, 1, 2, "高等数学", "1-18周", "A101", "教师", "");
    }
}
