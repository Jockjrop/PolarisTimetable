package com.polaris.timetable.importer.ai;

/** Versioned, model-neutral prompt for external multimodal schedule recognition. */
public final class PolarisAiPromptV1 {
    private static final String PROMPT =
            "你是一个大学课程表识别器。\n\n"
                    + "请识别我接下来发送的课程表图片。\n\n"
                    + "你只能输出 Polaris Schedule JSON v1。\n"
                    + "不要解释。\n"
                    + "不要使用 Markdown。\n"
                    + "不要添加任何 JSON 外的文字。\n\n"
                    + "输出必须首先包含一行：\n\n"
                    + "POLARIS_SCHEDULE_V1\n\n"
                    + "然后紧跟一个合法 JSON object。\n\n"
                    + "JSON 格式：\n\n"
                    + "{\n"
                    + "  \"format\": \"polaris-schedule-v1\",\n"
                    + "  \"semester\": \"学期名称或null\",\n"
                    + "  \"courses\": [\n"
                    + "    {\n"
                    + "      \"name\": \"课程名称\",\n"
                    + "      \"teacher\": \"教师姓名或null\",\n"
                    + "      \"teachingClass\": \"教学班或null\",\n"
                    + "      \"credit\": 3.0,\n"
                    + "      \"meetings\": [\n"
                    + "        {\n"
                    + "          \"dayOfWeek\": 0,\n"
                    + "          \"startSection\": 1,\n"
                    + "          \"endSection\": 2,\n"
                    + "          \"weekRule\": \"1-18周\",\n"
                    + "          \"campus\": \"校区或null\",\n"
                    + "          \"location\": \"教室或null\"\n"
                    + "        }\n"
                    + "      ]\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"practiceCourses\": [\n"
                    + "    {\n"
                    + "      \"name\": \"实践课程名称\",\n"
                    + "      \"teacher\": \"教师姓名或null\",\n"
                    + "      \"weekRule\": \"9-16周\"\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}\n\n"
                    + "严格规则：\n\n"
                    + "1. dayOfWeek 固定：0=周一，1=周二，2=周三，3=周四，"
                    + "4=周五，5=周六，6=周日。\n\n"
                    + "2. 周次必须保留课程表原始语义，例如：1-18周、1-17周(单)、"
                    + "2-18周(双)、9-16周。\n\n"
                    + "3. 不要自己展开为周次数组。\n\n"
                    + "4. 同一门课程如果有多个上课时间，放入同一个 courses item 的 "
                    + "meetings 中。\n\n"
                    + "5. 即使课程名称相同，如果教师或教学班明显不同，不要合并。\n\n"
                    + "6. 没有固定星期和节次的实践课程放入 practiceCourses。\n\n"
                    + "7. 图片中看不清或不存在的信息使用 null。禁止猜测教师、教室、"
                    + "周次、课程名或学分。\n\n"
                    + "8. startSection/endSection 使用课程表明确显示的节次，"
                    + "不要根据表格单元格高度猜测。\n\n"
                    + "9. 不生成 UUID、ID、schemaVersion、explicitWeeks。\n\n"
                    + "10. 输出必须是合法 JSON。不要输出 ```json。不要输出解释。";

    private PolarisAiPromptV1() {
    }

    public static String getPrompt() {
        return PROMPT;
    }
}
