package com.polaris.timetable.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** DiagnosticsBundleBuilder 单测：纯逻辑，不依赖 Android。 */
public class DiagnosticsBundleBuilderTest {

    @Test
    public void build_twoSections_preservesOrderAndFormat() {
        DiagnosticsBundleBuilder.Section app =
                new DiagnosticsBundleBuilder.Section("应用")
                        .put("版本", "1.23.0")
                        .put("设备", "Pixel 7");
        DiagnosticsBundleBuilder.Section schedule =
                new DiagnosticsBundleBuilder.Section("当前课表")
                        .put("课程数", "12");
        String report = DiagnosticsBundleBuilder.build("2026/9/3 11:30",
                Arrays.asList(app, schedule));

        String expected = "Polaris 诊断包\n"
                + "生成时间：2026/9/3 11:30\n"
                + "\n[应用]\n"
                + "版本：1.23.0\n"
                + "设备：Pixel 7\n"
                + "\n[当前课表]\n"
                + "课程数：12\n";
        assertEquals(expected, report);
    }

    @Test
    public void build_blankValues_shownAsUnset() {
        DiagnosticsBundleBuilder.Section section =
                new DiagnosticsBundleBuilder.Section("应用")
                        .put("学校", "")
                        .put("学期", null);
        String report = DiagnosticsBundleBuilder.build("t",
                Collections.singletonList(section));
        assertTrue(report.contains("学校：未设置\n"));
        assertTrue(report.contains("学期：未设置\n"));
    }

    @Test
    public void build_rawBody_appendedVerbatimAfterEntries() {
        DiagnosticsBundleBuilder.Section parse =
                new DiagnosticsBundleBuilder.Section("最近解析诊断")
                        .putRawBody("解析状态：完成\n识别课程：3 门");
        String report = DiagnosticsBundleBuilder.build("t",
                Collections.singletonList(parse));
        assertTrue(report.contains("[最近解析诊断]\n解析状态：完成\n识别课程：3 门\n"));
    }

    @Test
    public void build_nullAndEmptySections_tolerated() {
        List<DiagnosticsBundleBuilder.Section> sections = new ArrayList<>();
        sections.add(null);
        sections.add(new DiagnosticsBundleBuilder.Section("空节"));
        String report = DiagnosticsBundleBuilder.build(null, sections);
        assertTrue(report.startsWith("Polaris 诊断包\n生成时间：未设置\n"));
        assertTrue(report.contains("\n[空节]\n"));
    }

    @Test
    public void build_nullSections_onlyHeader() {
        String report = DiagnosticsBundleBuilder.build("t", null);
        assertEquals("Polaris 诊断包\n生成时间：t\n", report);
    }
}
