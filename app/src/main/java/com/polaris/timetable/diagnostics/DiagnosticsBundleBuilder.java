package com.polaris.timetable.diagnostics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断包文本构建器（P6，2026-09）：把宿主收集的结构化信息拼成
 * 固定格式的纯文本（分节 + 键值行 + 可选原文块），供导出与反馈。
 * 纯 Java 无 Android 依赖，保证可单测；输出顺序与输入一致（LinkedHashMap）。
 */
public final class DiagnosticsBundleBuilder {
    private DiagnosticsBundleBuilder() {
    }

    /** 诊断包中的一个分节：标题 + 有序键值行 + 可选原文块（保留换行整段追加）。 */
    public static final class Section {
        public final String title;
        public final LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        public String rawBody = "";

        public Section(String title) {
            this.title = title == null ? "" : title.trim();
        }

        public Section put(String key, String value) {
            entries.put(key == null ? "" : key, value == null ? "" : value);
            return this;
        }

        public Section putRawBody(String body) {
            rawBody = body == null ? "" : body;
            return this;
        }
    }

    /**
     * 构建诊断包全文。generatedAtText 由宿主传入（保持本类无时钟依赖，便于断言）。
     * 空值统一显示为「未设置」，避免键值行出现悬空冒号。
     */
    public static String build(String generatedAtText, List<Section> sections) {
        StringBuilder report = new StringBuilder();
        report.append("Polaris 诊断包\n");
        report.append("生成时间：").append(orUnset(generatedAtText)).append('\n');
        if (sections == null) {
            return report.toString();
        }
        for (Section section : sections) {
            if (section == null) {
                continue;
            }
            report.append('\n').append('[').append(orUnset(section.title)).append(']').append('\n');
            for (Map.Entry<String, String> entry : section.entries.entrySet()) {
                report.append(entry.getKey()).append("：")
                        .append(orUnset(entry.getValue())).append('\n');
            }
            if (section.rawBody.trim().length() > 0) {
                report.append(section.rawBody.trim()).append('\n');
            }
        }
        return report.toString();
    }

    private static String orUnset(String value) {
        return value == null || value.trim().length() == 0 ? "未设置" : value.trim();
    }
}
