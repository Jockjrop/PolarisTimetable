package com.polaris.timetable.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StableMeetingId;
import com.polaris.timetable.model.StructuredCourse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScheduleImageParserTest {
    private final ScheduleImageParser parser = new ScheduleImageParser();

    @Test
    public void recognizesCommonWeekdayTitles() {
        assertEquals(0, ScheduleImageParser.weekdayIndex("周一"));
        assertEquals(2, ScheduleImageParser.weekdayIndex("星期三"));
        assertEquals(6, ScheduleImageParser.weekdayIndex("星期日"));
        assertEquals(6, ScheduleImageParser.weekdayIndex("周天"));
        assertEquals(-1, ScheduleImageParser.weekdayIndex("课程表"));
    }

    @Test
    public void mapsMondayThroughFridayColumns() {
        ScheduleImageParser.Layout layout = parser.analyzeLayout(gridBlocks(), 700, 800);

        assertTrue(layout.valid);
        assertEquals(5, layout.columns.size());
        assertEquals(0, layout.dayAt(150));
        assertEquals(1, layout.dayAt(250));
        assertEquals(4, layout.dayAt(550));
        assertEquals(-1, layout.dayAt(50));
    }

    @Test
    public void mapsVerticalSectionAxis() {
        ScheduleImageParser.Layout layout = parser.analyzeLayout(gridBlocks(), 700, 800);

        assertTrue(layout.valid);
        assertEquals(6, layout.sections.size());
        assertEquals(1, (int) layout.sectionsOverlapping(120, 180).get(0));
        assertEquals(3, (int) layout.sectionsOverlapping(320, 480).get(0));
        assertEquals(4, (int) layout.sectionsOverlapping(320, 480).get(1));
    }

    @Test
    public void mapsPairedSectionLabelsUsedByRenderedTimetables() {
        List<OcrTextBlock> blocks = pairedSectionGridBlocks();
        blocks.add(courseBlock(110, 110, 190, 190,
                "移动应用开发◆\n(1-2节)1-4周\n场地:A101\n教师:陈晨"));

        ImageScheduleRecognitionResult result = parser.parse(blocks, 700, 800);

        assertTrue(result.isImportable());
        StructuredCourse course = result.structuredCourses.get(0);
        assertEquals("移动应用开发", course.name);
        assertEquals("陈晨", course.teacher);
        assertEquals("A101", course.defaultLocation);
        assertEquals(1, course.meetings.get(0).startSection);
        assertEquals(2, course.meetings.get(0).endSection);
    }

    @Test
    public void recognizesCourseSpanningTwoSections() {
        ImageScheduleRecognitionResult result = parseCourse(
                courseBlock(110, 110, 190, 290,
                        "高等数学\n张老师\nA101\n1-16周"));

        assertTrue(result.isImportable());
        CourseMeeting meeting = result.structuredCourses.get(0).meetings.get(0);
        assertEquals(1, meeting.startSection);
        assertEquals(2, meeting.endSection);
        assertTrue(StableCourseId.isValid(result.structuredCourses.get(0).id));
        assertTrue(StableMeetingId.isValid(meeting.id));
    }

    @Test
    public void delegatesContinuousWeeksToWeekRuleParser() {
        CourseMeeting meeting = onlyMeeting("1-16周");

        assertTrue(meeting.weekRule.containsWeek(1));
        assertTrue(meeting.weekRule.containsWeek(16));
        assertFalse(meeting.weekRule.containsWeek(17));
    }

    @Test
    public void delegatesOddWeeksToWeekRuleParser() {
        CourseMeeting meeting = onlyMeeting("1-8周 单周");

        assertTrue(meeting.weekRule.containsWeek(1));
        assertFalse(meeting.weekRule.containsWeek(2));
        assertTrue(meeting.weekRule.containsWeek(7));
    }

    @Test
    public void delegatesMixedWeekListToWeekRuleParser() {
        CourseMeeting meeting = onlyMeeting("2、5-6周");

        assertTrue(meeting.weekRule.containsWeek(2));
        assertTrue(meeting.weekRule.containsWeek(5));
        assertTrue(meeting.weekRule.containsWeek(6));
        assertFalse(meeting.weekRule.containsWeek(4));
    }

    @Test
    public void sameCourseCellsBecomeMultipleMeetings() {
        List<OcrTextBlock> blocks = gridBlocks();
        blocks.add(courseBlock(110, 110, 190, 290,
                "高等数学\n张老师\nA101\n1-16周"));
        blocks.add(courseBlock(310, 310, 390, 490,
                "高等数学\n张老师\nA101\n1-16周"));

        ImageScheduleRecognitionResult result = parser.parse(blocks, 700, 800);

        assertEquals(1, result.structuredCourses.size());
        assertEquals(2, result.structuredCourses.get(0).meetings.size());
        assertNotEquals(result.structuredCourses.get(0).meetings.get(0).id,
                result.structuredCourses.get(0).meetings.get(1).id);
    }

    @Test
    public void sameNameDifferentTeachersAreNotMerged() {
        List<OcrTextBlock> blocks = gridBlocks();
        blocks.add(courseBlock(110, 110, 190, 290,
                "大学英语\n张老师\nA101\n1-16周"));
        blocks.add(courseBlock(210, 110, 290, 290,
                "大学英语\n李老师\nB202\n1-16周"));

        ImageScheduleRecognitionResult result = parser.parse(blocks, 700, 800);

        assertEquals(2, result.structuredCourses.size());
        assertEquals("张老师", result.structuredCourses.get(0).teacher);
        assertEquals("李老师", result.structuredCourses.get(1).teacher);
        assertNotEquals(result.structuredCourses.get(0).id,
                result.structuredCourses.get(1).id);
    }

    @Test
    public void mergesNearbyOcrLinesFromTheSameSourceParagraph() {
        List<OcrTextBlock> blocks = gridBlocks();
        blocks.add(ocrLine(110, 115, 190, 145, "高等数学", 42));
        blocks.add(ocrLine(120, 155, 185, 180, "张老师", 42));
        blocks.add(ocrLine(120, 190, 180, 215, "A101", 42));
        blocks.add(ocrLine(115, 225, 185, 250, "1-16周", 42));

        ImageScheduleRecognitionResult result = parser.parse(blocks, 700, 800);

        assertTrue(result.isImportable());
        assertEquals(1, result.structuredCourses.size());
        StructuredCourse course = result.structuredCourses.get(0);
        assertEquals("高等数学", course.name);
        assertEquals("张老师", course.teacher);
        assertEquals("A101", course.defaultLocation);
        assertEquals(1, course.meetings.get(0).startSection);
        assertEquals(2, course.meetings.get(0).endSection);
    }

    @Test
    public void splitsOneOcrParagraphAcrossWeekdayColumns() {
        List<OcrTextBlock> blocks = gridBlocks();
        blocks.add(ocrLine(110, 115, 190, 145, "高等数学", 73));
        blocks.add(ocrLine(110, 155, 190, 180, "张老师 A101 1-16周", 73));
        blocks.add(ocrLine(210, 115, 290, 145, "大学英语", 73));
        blocks.add(ocrLine(210, 155, 290, 180, "李老师 B202 1-16周", 73));

        ImageScheduleRecognitionResult result = parser.parse(blocks, 700, 800);

        assertEquals(2, result.structuredCourses.size());
        assertEquals(0, result.structuredCourses.get(0).meetings.get(0).day);
        assertEquals(1, result.structuredCourses.get(1).meetings.get(0).day);
    }

    @Test
    public void splitsDistantCoursesFromTheSameOcrParagraph() {
        List<OcrTextBlock> blocks = gridBlocks();
        blocks.add(ocrLine(110, 115, 190, 145, "高等数学", 91));
        blocks.add(ocrLine(110, 155, 190, 180, "张老师 A101 1-16周", 91));
        blocks.add(ocrLine(110, 415, 190, 445, "大学英语", 91));
        blocks.add(ocrLine(110, 455, 190, 480, "李老师 B202 1-16周", 91));

        ImageScheduleRecognitionResult result = parser.parse(blocks, 700, 800);

        assertEquals(2, result.structuredCourses.size());
        assertEquals(1, result.structuredCourses.get(0).meetings.get(0).startSection);
        assertEquals(4, result.structuredCourses.get(1).meetings.get(0).startSection);
    }

    @Test
    public void emptyOcrResultCannotBeCommitted() {
        ImageScheduleRecognitionResult result = parser.parse(
                Collections.emptyList(), 700, 800);

        assertEquals(ImageScheduleRecognitionResult.Status.FAILURE, result.status);
        assertFalse(result.isImportable());
        assertFalse(ImageImportCommitPolicy.canCommit(true, result));
    }

    @Test
    public void unrecognizedGridCannotBeCommitted() {
        ImageScheduleRecognitionResult result = parser.parse(
                Collections.singletonList(new OcrTextBlock(
                        "高等数学 张老师", 100, 100, 300, 160, 0.95f)),
                700, 800);

        assertEquals(ImageScheduleRecognitionResult.Status.FAILURE, result.status);
        assertFalse(ImageImportCommitPolicy.canCommit(true, result));
    }

    @Test
    public void cancelledImportCannotReplaceCanonicalCourses() {
        ImageScheduleRecognitionResult result = parseCourse(
                courseBlock(110, 110, 190, 290,
                        "高等数学\n张老师\nA101\n1-16周"));

        assertTrue(result.isImportable());
        assertFalse(ImageImportCommitPolicy.canCommit(false, result));
        assertTrue(ImageImportCommitPolicy.canCommit(true, result));
    }

    private CourseMeeting onlyMeeting(String weeks) {
        ImageScheduleRecognitionResult result = parseCourse(
                courseBlock(110, 110, 190, 290,
                        "高等数学\n张老师\nA101\n" + weeks));
        assertTrue(result.isImportable());
        return result.structuredCourses.get(0).meetings.get(0);
    }

    private ImageScheduleRecognitionResult parseCourse(OcrTextBlock course) {
        List<OcrTextBlock> blocks = gridBlocks();
        blocks.add(course);
        return parser.parse(blocks, 700, 800);
    }

    private List<OcrTextBlock> gridBlocks() {
        List<OcrTextBlock> blocks = new ArrayList<>();
        String[] weekdays = {"周一", "周二", "周三", "周四", "周五"};
        for (int day = 0; day < weekdays.length; day++) {
            int centerX = 150 + day * 100;
            blocks.add(new OcrTextBlock(
                    weekdays[day], centerX - 35, 20, centerX + 35, 60, 0.98f));
        }
        for (int section = 1; section <= 6; section++) {
            int centerY = 150 + (section - 1) * 100;
            blocks.add(new OcrTextBlock(
                    String.valueOf(section), 20, centerY - 20, 60, centerY + 20, 0.98f));
        }
        return blocks;
    }

    private List<OcrTextBlock> pairedSectionGridBlocks() {
        List<OcrTextBlock> blocks = new ArrayList<>();
        String[] weekdays = {"周一", "周二", "周三", "周四", "周五"};
        for (int day = 0; day < weekdays.length; day++) {
            int centerX = 150 + day * 100;
            blocks.add(new OcrTextBlock(
                    weekdays[day], centerX - 35, 20, centerX + 35, 60, 0.98f));
        }
        for (int band = 0; band < 6; band++) {
            int startSection = band * 2 + 1;
            int centerY = 150 + band * 100;
            blocks.add(new OcrTextBlock(
                    startSection + "-" + (startSection + 1) + "节",
                    10, centerY - 20, 70, centerY + 20, 0.98f));
        }
        return blocks;
    }

    private OcrTextBlock courseBlock(int left, int top, int right, int bottom, String text) {
        return new OcrTextBlock(text, left, top, right, bottom, 0.95f);
    }

    private OcrTextBlock ocrLine(int left, int top, int right, int bottom,
                                 String text, int sourceGroupId) {
        return new OcrTextBlock(text, left, top, right, bottom, 0.95f, sourceGroupId);
    }
}
