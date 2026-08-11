package com.polaris.timetable.importer.ai;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PolarisAiPromptV1Test {
    @Test
    public void containsProtocolMarker() {
        assertTrue(PolarisAiPromptV1.getPrompt().contains("POLARIS_SCHEDULE_V1"));
    }

    @Test
    public void containsV1Format() {
        String prompt = PolarisAiPromptV1.getPrompt();
        assertTrue(prompt.contains("\"format\": \"polaris-schedule-v1\""));
    }

    @Test
    public void definesZeroBasedDayOfWeek() {
        String prompt = PolarisAiPromptV1.getPrompt();
        assertTrue(prompt.contains("0=周一"));
        assertTrue(prompt.contains("6=周日"));
    }

    @Test
    public void forbidsInternalIdsAndSchemaFields() {
        String prompt = PolarisAiPromptV1.getPrompt();
        assertTrue(prompt.contains("不生成 UUID、ID、schemaVersion、explicitWeeks"));
    }

    @Test
    public void definesMultipleMeetingGroupingRule() {
        String prompt = PolarisAiPromptV1.getPrompt();
        assertTrue(prompt.contains("同一门课程如果有多个上课时间"));
        assertTrue(prompt.contains("同一个 courses item 的 meetings"));
    }
}
