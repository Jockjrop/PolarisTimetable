package com.polaris.timetable.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;
import com.polaris.timetable.time.CourseTimeResolver;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScheduleCalendarExporterTest {
    private static final String MEETING_ONE = "11111111-1111-4111-8111-111111111111";
    private static final String MEETING_TWO = "22222222-2222-4222-8222-222222222222";

    private static CourseTimeResolver.Settings settings() {
        return new CourseTimeResolver.Settings(
                "08:00", 50, 10, 30, "14:30", "16:35", "");
    }

    private static ScheduleCalendarExporter.ExportContext context() {
        return new ScheduleCalendarExporter.ExportContext(
                "2026春课表", "2026春季学期", "2026/3/3", 20, settings());
    }

    private static StructuredCourse course(String name, CourseMeeting... meetings) {
        return new StructuredCourse(
                "course-" + name.hashCode(), name, "默认教师", "默认地点",
                Arrays.asList(meetings), "raw", "3", "#4FA4F3",
                CourseType.LECTURE);
    }

    private static CourseMeeting meeting(int day, int startSection, int endSection,
                                         WeekRule weekRule, String location) {
        return new CourseMeeting(MEETING_ONE, day, startSection, endSection,
                weekRule, location, "张老师", "raw");
    }

    private static CourseMeeting clockMeeting(int day, int startMinute, int endMinute,
                                              WeekRule weekRule) {
        return new CourseMeeting(MEETING_TWO, day, 1, 1, weekRule,
                "机房", "李老师", "raw", CourseTimeMode.CLOCK,
                startMinute, endMinute);
    }

    private static int count(String content, String token) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    @Test
    public void ical_rangeCourse_expandsOneEventPerActiveWeek() {
        StructuredCourse course = course("高等数学",
                meeting(0, 1, 2,
                        new WeekRule(WeekRule.Type.RANGE, 1, 3,
                                Collections.emptyList(), "1-3周"), "A101"));

        String content = ScheduleCalendarExporter.buildICal(context(),
                Collections.singletonList(course));

        assertTrue(content.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(content.contains("X-WR-CALNAME:2026春课表\r\n"));
        assertEquals(3, count(content, "BEGIN:VEVENT"));
        // firstWeekDay 2026/3/3 (Tue) -> week 1 Monday is 2026-03-02.
        assertTrue(content.contains("DTSTART:20260302T080000\r\n"));
        assertTrue(content.contains("DTEND:20260302T095000\r\n"));
        assertTrue(content.contains("DTSTART:20260309T080000\r\n"));
        assertTrue(content.contains("DTSTART:20260316T080000\r\n"));
        assertTrue(content.contains("SUMMARY:高等数学\r\n"));
        assertTrue(content.contains("LOCATION:A101\r\n"));
        assertTrue(content.contains("UID:polaris-course-"));
        assertTrue(content.contains(MEETING_ONE));
        assertTrue(content.endsWith("END:VCALENDAR\r\n"));
    }

    @Test
    public void ical_oddEvenRules_expandOnlyMatchingWeeks() {
        StructuredCourse odd = course("单周课",
                meeting(1, 3, 4,
                        new WeekRule(WeekRule.Type.ODD, 1, 5,
                                Collections.emptyList(), "1-5周(单)"), "B202"));
        StructuredCourse even = course("双周课",
                meeting(2, 3, 4,
                        new WeekRule(WeekRule.Type.EVEN, 1, 5,
                                Collections.emptyList(), "1-5周(双)"), "C303"));

        String content = ScheduleCalendarExporter.buildICal(context(),
                Arrays.asList(odd, even));

        // Odd weeks 1,3,5 -> 3 events; even weeks 2,4 -> 2 events.
        assertEquals(5, count(content, "BEGIN:VEVENT"));
        // Week 1 Monday 03-02 (day 1 -> 03-03), week 3 (day 1 -> 03-17), week 5 (day 1 -> 03-31).
        assertTrue(content.contains("DTSTART:20260303T102000\r\n"));
        assertTrue(content.contains("DTSTART:20260317T102000\r\n"));
        assertTrue(content.contains("DTSTART:20260331T102000\r\n"));
        // Even: week 2 (day 2 -> 03-11), week 4 (day 2 -> 03-25).
        assertTrue(content.contains("DTSTART:20260311T102000\r\n"));
        assertTrue(content.contains("DTSTART:20260325T102000\r\n"));
        // No odd-week Tuesday event on 03-10.
        assertFalse(content.contains("DTSTART:20260310T"));
    }

    @Test
    public void ical_explicitWeeks_expandsExactlyThoseWeeks() {
        StructuredCourse course = course("讲座",
                meeting(0, 7, 8,
                        new WeekRule(WeekRule.Type.RANGE, 0, 0,
                                Arrays.asList(2, 5, 7), "2,5,7周"), "D404"));

        String content = ScheduleCalendarExporter.buildICal(context(),
                Collections.singletonList(course));

        assertEquals(3, count(content, "BEGIN:VEVENT"));
        // Sections 7-8 start at the late-afternoon anchor 16:35.
        // Week 2 Monday 03-09, week 5 Monday 03-30, week 7 Monday 04-13.
        assertTrue(content.contains("DTSTART:20260309T163500\r\n"));
        assertTrue(content.contains("DTSTART:20260330T163500\r\n"));
        assertTrue(content.contains("DTSTART:20260413T163500\r\n"));
    }

    @Test
    public void ical_allRule_expandsEverySemesterWeek() {
        StructuredCourse course = course("体育",
                meeting(3, 5, 6,
                        new WeekRule(WeekRule.Type.ALL, 0, 0,
                                Collections.emptyList(), "全周"), "操场"));

        String content = ScheduleCalendarExporter.buildICal(context(),
                Collections.singletonList(course));

        assertEquals(20, count(content, "BEGIN:VEVENT"));
    }

    @Test
    public void ical_unknownWeekRule_generatesSingleEventWithNotice() {
        StructuredCourse course = course("项目实践",
                meeting(4, 9, 10,
                        new WeekRule(WeekRule.Type.UNKNOWN, 0, 0,
                                Collections.emptyList(), "周次见PDF"), "实验室"));

        String content = ScheduleCalendarExporter.buildICal(context(),
                Collections.singletonList(course));

        assertEquals(1, count(content, "BEGIN:VEVENT"));
        assertTrue(content.contains("周次未能识别"));
        // Week 1 Friday 03-06.
        assertTrue(content.contains("DTSTART:20260306T"));
    }

    @Test
    public void ical_clockTimeMeeting_usesExactTimes() {
        StructuredCourse course = course("英语听力",
                clockMeeting(0, 10 * 60, 11 * 60 + 30,
                        new WeekRule(WeekRule.Type.RANGE, 1, 2,
                                Collections.emptyList(), "1-2周")));

        String content = ScheduleCalendarExporter.buildICal(context(),
                Collections.singletonList(course));

        assertEquals(2, count(content, "BEGIN:VEVENT"));
        assertTrue(content.contains("DTSTART:20260302T100000\r\n"));
        assertTrue(content.contains("DTEND:20260302T113000\r\n"));
        assertTrue(content.contains("DTSTART:20260309T100000\r\n"));
    }

    @Test
    public void ical_escapesSpecialCharacters() {
        StructuredCourse course = course("数学,分析;A\\B",
                meeting(0, 1, 2,
                        new WeekRule(WeekRule.Type.RANGE, 1, 1,
                                Collections.emptyList(), "1周"), "A101"));

        String content = ScheduleCalendarExporter.buildICal(context(),
                Collections.singletonList(course));

        assertTrue(content.contains("SUMMARY:数学\\,分析\\;A\\\\B\r\n"));
    }

    @Test
    public void ical_foldsLongLinesWithCrLfSpace() {
        StringBuilder longDescription = new StringBuilder("这是一门课程名称非常非常长的课程");
        while (longDescription.length() < 60) {
            longDescription.append("啊");
        }
        StructuredCourse course = course(longDescription.toString(),
                meeting(0, 1, 2,
                        new WeekRule(WeekRule.Type.RANGE, 1, 1,
                                Collections.emptyList(), "1周"), "A101"));

        String content = ScheduleCalendarExporter.buildICal(context(),
                Collections.singletonList(course));

        assertTrue(content.contains("\r\n "));
    }

    @Test
    public void csv_hasBomHeaderAndOneRowPerMeeting() {
        StructuredCourse course = course("高等数学",
                meeting(0, 1, 2,
                        new WeekRule(WeekRule.Type.RANGE, 1, 16,
                                Collections.emptyList(), "1-16周"), "A101"),
                clockMeeting(3, 10 * 60, 11 * 60 + 30,
                        new WeekRule(WeekRule.Type.EVEN, 2, 16,
                                Collections.emptyList(), "2-16周(双)")));

        String content = ScheduleCalendarExporter.buildCsv(context(),
                Collections.singletonList(course));

        assertTrue(content.startsWith("\uFEFF"));
        assertTrue(content.contains("课程名称,教师,学分,课程类型,星期,开始节次,结束节次,"
                + "开始时间,结束时间,周次,地点\r\n"));
        assertTrue(content.contains("高等数学,张老师,3,普通课程,周一,1,2,08:00,09:50,1-16周,A101\r\n"));
        assertTrue(content.contains("高等数学,李老师,3,普通课程,周四,,,10:00,11:30,2-16周(双),机房\r\n"));
    }

    @Test
    public void csv_escapesCommasAndQuotes() {
        StructuredCourse course = course("数学,\"分析\"",
                meeting(0, 1, 2,
                        new WeekRule(WeekRule.Type.RANGE, 1, 1,
                                Collections.emptyList(), "1周"), "A101"));

        String content = ScheduleCalendarExporter.buildCsv(context(),
                Collections.singletonList(course));

        assertTrue(content.contains("\"数学,\"\"分析\"\"\",张老师"));
    }

    @Test
    public void csv_emptySchedule_containsOnlyHeader() {
        String content = ScheduleCalendarExporter.buildCsv(context(),
                new ArrayList<StructuredCourse>());

        assertTrue(content.startsWith("\uFEFF课程名称"));
        assertEquals(1, count(content, "\r\n"));
    }

    @Test
    public void safeFileName_replacesUnsafeCharacters() {
        assertEquals("2026春课表", ScheduleCalendarExporter.safeFileName("2026春课表"));
        assertEquals("a_b_c", ScheduleCalendarExporter.safeFileName("a/b\\c"));
        assertEquals("课表", ScheduleCalendarExporter.safeFileName("  "));
        assertEquals("课表", ScheduleCalendarExporter.safeFileName(null));
    }

    @Test
    public void foldLine_keepsEveryPhysicalLineWithinLimitAndPreservesText() {
        StringBuilder longName = new StringBuilder("课程");
        while (longName.length() < 100) {
            longName.append("非常长课程名称");
        }
        String folded = ScheduleCalendarExporter.foldLine("SUMMARY:" + longName);

        String[] lines = folded.split("\r\n");
        assertTrue(lines.length > 1);
        for (String line : lines) {
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            assertTrue("line exceeds 75 octets: " + line, bytes.length <= 75);
            // Must not start with a UTF-8 continuation byte.
            if (line.length() > 0) {
                assertFalse((bytes[0] & 0xC0) == 0x80);
            }
        }
        String unfolded = folded.replace("\r\n ", "");
        assertTrue(unfolded.contains(longName.toString()));
    }
}
