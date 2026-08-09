package com.polaris.timetable.importer;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.ParseError;
import com.polaris.timetable.model.ParseResult;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImportReviewSummaryTest {
    @Test
    public void analyze_countsAddedModifiedAndRemovedMeetings() {
        Course unchanged = course(0, 1, "高等数学", "A101", "教师");
        Course changedCurrent = course(1, 3, "大学物理", "B201", "教师");
        Course changedIncoming = course(1, 3, "大学物理", "B202", "教师");
        Course removed = course(2, 5, "大学英语", "C301", "教师");
        Course added = course(3, 7, "体育", "操场", "教师");
        ParseResult result = new ParseResult(true,
                Arrays.asList(unchanged, changedIncoming, added),
                Collections.emptyList(), "", 1);

        ImportReviewSummary summary = ImportReviewSummary.analyze(result,
                Arrays.asList(unchanged, changedCurrent, removed), 20);

        assertEquals(1, summary.addedCount);
        assertEquals(1, summary.modifiedCount);
        assertEquals(1, summary.removedCount);
    }

    @Test
    public void analyze_surfacesMissingFieldsWarningsAndConflicts() {
        Course first = new Course(0, 1, 2, "课程一", "周次见PDF", "", "", "");
        Course second = course(0, 1, "课程二", "A101", "教师");
        ParseError warning = new ParseError(ParseError.Severity.WARNING,
                ParseError.Code.LOCATION_NOT_FOUND, "地点缺失", 1, "");
        ParseResult result = new ParseResult(true, Arrays.asList(first, second),
                Collections.singletonList(warning), "", 1);

        ImportReviewSummary summary = ImportReviewSummary.analyze(
                result, Collections.emptyList(), 20);

        assertEquals(1, summary.warningCount);
        assertEquals(1, summary.missingLocationCount);
        assertEquals(1, summary.missingTeacherCount);
        assertEquals(1, summary.unknownWeekCount);
        assertEquals(1, summary.conflictCount);
        assertTrue(summary.hasIssues());
        assertTrue(summary.canImport());
    }

    @Test
    public void analyze_blocksEmptyImport() {
        ImportReviewSummary summary = ImportReviewSummary.analyze(
                new ParseResult(false, Collections.emptyList(), Collections.emptyList(), "", 1),
                Collections.emptyList(), 20);

        assertFalse(summary.canImport());
    }

    private Course course(int day, int start, String name, String location, String teacher) {
        return new Course(day, start, start + 1, name, "1-20周", location, teacher, "");
    }
}
