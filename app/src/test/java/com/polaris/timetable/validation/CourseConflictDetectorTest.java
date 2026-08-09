package com.polaris.timetable.validation;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseType;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class CourseConflictDetectorTest {
    @Test
    public void overlappingSectionsAndWeeks_createConflict() {
        Course first = course(0, 1, 3, "高等数学", "1-4周");
        Course second = course(0, 3, 4, "大学物理", "2-6周");

        List<CourseConflictDetector.Conflict> conflicts = CourseConflictDetector.findAll(
                Arrays.asList(first, second), 20);

        assertEquals(1, conflicts.size());
        CourseConflictDetector.Conflict conflict = conflicts.get(0);
        assertSame(first, conflict.first);
        assertSame(second, conflict.second);
        assertEquals(3, conflict.overlapStartSection);
        assertEquals(3, conflict.overlapEndSection);
        assertEquals(Arrays.asList(2, 3, 4), conflict.commonWeeks);
    }

    @Test
    public void adjacentSections_areNotConflict() {
        Course first = course(0, 1, 2, "高等数学", "1-8周");
        Course second = course(0, 3, 4, "大学物理", "1-8周");

        assertTrue(CourseConflictDetector.findAll(
                Arrays.asList(first, second), 20).isEmpty());
    }

    @Test
    public void sameSlotButDifferentWeeks_areNotConflict() {
        Course first = course(0, 1, 2, "高等数学", "1-4周");
        Course second = course(0, 1, 2, "大学物理", "5-8周");

        assertTrue(CourseConflictDetector.findAll(
                Arrays.asList(first, second), 20).isEmpty());
    }

    @Test
    public void oddAndEvenCourses_areNotConflict() {
        Course odd = course(0, 1, 2, "高等数学", "1-8周 单周");
        Course even = course(0, 1, 2, "大学物理", "1-8周 双周");

        assertTrue(CourseConflictDetector.findAll(
                Arrays.asList(odd, even), 20).isEmpty());
    }

    @Test
    public void explicitAndMultipleWeekRanges_findOnlyCommonWeeks() {
        Course first = course(0, 1, 2, "高等数学", "1-2、5、7周");
        Course second = course(0, 1, 2, "大学物理", "2、5-6周");

        CourseConflictDetector.Conflict conflict = CourseConflictDetector.findAll(
                Arrays.asList(first, second), 20).get(0);

        assertEquals(Arrays.asList(2, 5), conflict.commonWeeks);
        assertEquals("第2、5周", conflict.commonWeeksText());
    }

    @Test
    public void forWeek_returnsOnlyConflictsActiveThatWeek() {
        Course first = course(0, 1, 2, "高等数学", "1-4周");
        Course second = course(0, 1, 2, "大学物理", "3-6周");

        assertEquals(0, CourseConflictDetector.forWeek(
                Arrays.asList(first, second), 20, 2).size());
        assertEquals(1, CourseConflictDetector.forWeek(
                Arrays.asList(first, second), 20, 3).size());
    }

    @Test
    public void bannerOnlyPractice_isIgnored() {
        Course practice = new Course(-1, 0, 0, "工程训练", "1-20周",
                "", "", "", "", "", CourseType.PRACTICE);
        Course lesson = course(0, 1, 2, "高等数学", "1-20周");

        assertEquals(Collections.emptyList(), CourseConflictDetector.findAll(
                Arrays.asList(practice, lesson), 20));
    }

    @Test
    public void formatWeeks_compactsConsecutiveRanges() {
        assertEquals("第1–3、5、8–9周", CourseConflictDetector.formatWeeks(
                Arrays.asList(1, 2, 3, 5, 8, 9)));
    }

    private Course course(int day, int start, int end, String name, String weeks) {
        return new Course(day, start, end, name, weeks, "A101", "教师", "");
    }
}
