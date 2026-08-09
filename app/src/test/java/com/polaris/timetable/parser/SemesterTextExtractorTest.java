package com.polaris.timetable.parser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SemesterTextExtractorTest {
    @Test
    public void extractsAndNormalizesNumberedSemester() {
        assertEquals("2025-2026学年第2学期",
                SemesterTextExtractor.extract("西安邮电大学 2025 - 2026 学年 第 2 学期 学生课表"));
        assertEquals("2025-2026学年第2学期",
                SemesterTextExtractor.extract("2025至2026学年第二学期"));
    }

    @Test
    public void extractsSeasonSemester() {
        assertEquals("2025-2026学年春季学期",
                SemesterTextExtractor.extract("2025—2026学年春季学期课程安排"));
    }

    @Test
    public void ignoresBareYearsWithoutSemesterLabels() {
        assertEquals("", SemesterTextExtractor.extract("课表生成时间 2025-2026"));
    }
}
