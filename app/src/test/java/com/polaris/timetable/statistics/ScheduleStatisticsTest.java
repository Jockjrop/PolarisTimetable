package com.polaris.timetable.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ScheduleStatisticsTest {
    private static final String COURSE_ONE = "11111111-1111-4111-8111-111111111111";
    private static final String COURSE_TWO = "22222222-2222-4222-8222-222222222222";

    private static CourseMeeting meeting(int day, int startSection, int endSection,
                                         WeekRule weekRule, String teacher) {
        return new CourseMeeting("meeting-" + day + "-" + startSection,
                day, startSection, endSection, weekRule, "A101", teacher, "raw");
    }

    private static StructuredCourse course(String id, String name, String teacher,
                                           String credit, CourseType type,
                                           CourseMeeting... meetings) {
        return new StructuredCourse(id, name, teacher, "A101",
                Arrays.asList(meetings), "raw", credit, "#4FA4F3", type);
    }

    @Test
    public void compute_countsCoursesCreditsAndWeeklySections() {
        StructuredCourse math = course(COURSE_ONE, "高等数学", "张老师", "5",
                CourseType.LECTURE,
                meeting(0, 1, 2, new WeekRule(WeekRule.Type.RANGE, 1, 16,
                        Collections.emptyList(), "1-16周"), "张老师"),
                meeting(3, 3, 4, new WeekRule(WeekRule.Type.EVEN, 2, 16,
                        Collections.emptyList(), "2-16周(双)"), "张老师"));
        StructuredCourse lab = course(COURSE_TWO, "物理实验", "李老师", "1.5",
                CourseType.EXPERIMENT,
                meeting(1, 5, 6, new WeekRule(WeekRule.Type.RANGE, 1, 8,
                        Collections.emptyList(), "1-8周"), "李老师"));

        ScheduleStatistics.Statistics stats =
                ScheduleStatistics.compute(Arrays.asList(math, lab), 20);

        assertEquals(2, stats.courseCount);
        assertEquals(1, stats.experimentCount);
        assertEquals(0, stats.practiceCount);
        assertEquals(6.5, stats.totalCredits, 0.001);
        // 2 + 2 + 2 = 6 periods per week.
        assertEquals(6, stats.weeklySections);
        // 2*16 + 2*8(even 2-16 -> 8 weeks) + 2*8 = 32 + 16 + 16.
        assertEquals(64, stats.semesterSections);
        assertEquals(2, stats.teacherCount);
        assertEquals("张老师", stats.topTeacher);
    }

    @Test
    public void compute_sectionsByDay_tracksWeeklyPeriodsPerDay() {
        StructuredCourse course = course(COURSE_ONE, "英语", "王老师", "2",
                CourseType.LECTURE,
                meeting(0, 1, 2, new WeekRule(WeekRule.Type.RANGE, 1, 4,
                        Collections.emptyList(), "1-4周"), "王老师"),
                meeting(4, 7, 8, new WeekRule(WeekRule.Type.RANGE, 1, 4,
                        Collections.emptyList(), "1-4周"), "王老师"));

        ScheduleStatistics.Statistics stats =
                ScheduleStatistics.compute(Collections.singletonList(course), 10);

        assertEquals(2, stats.sectionsByDay[0]);
        assertEquals(2, stats.sectionsByDay[4]);
        assertEquals(0, stats.sectionsByDay[1]);
    }

    @Test
    public void compute_clockModeMeeting_countsCeilOfDuration() {
        CourseMeeting clock = new CourseMeeting("clock-1", 2, 1, 1,
                new WeekRule(WeekRule.Type.RANGE, 1, 2,
                        Collections.emptyList(), "1-2周"), "机房", "赵老师", "raw",
                CourseTimeMode.CLOCK, 10 * 60, 11 * 60 + 50);
        StructuredCourse course = course(COURSE_ONE, "听力", "赵老师", "1",
                CourseType.LECTURE, clock);

        ScheduleStatistics.Statistics stats =
                ScheduleStatistics.compute(Collections.singletonList(course), 10);

        // 110 minutes / 45 -> ceil = 3 periods.
        assertEquals(3, stats.weeklySections);
        assertEquals(6, stats.semesterSections);
    }

    @Test
    public void compute_unknownWeekRule_countsSingleWeek() {
        StructuredCourse course = course(COURSE_ONE, "项目", "孙老师", "2",
                CourseType.PRACTICE,
                meeting(5, 9, 10, new WeekRule(WeekRule.Type.UNKNOWN, 0, 0,
                        Collections.emptyList(), "周次见PDF"), "孙老师"));

        ScheduleStatistics.Statistics stats =
                ScheduleStatistics.compute(Collections.singletonList(course), 20);

        assertEquals(1, stats.practiceCount);
        assertEquals(2, stats.semesterSections);
    }

    @Test
    public void compute_emptyAndNullInputs_yieldZeroStatistics() {
        ScheduleStatistics.Statistics empty =
                ScheduleStatistics.compute(Collections.emptyList(), 20);
        assertEquals(0, empty.courseCount);
        assertEquals(0, empty.semesterSections);

        ScheduleStatistics.Statistics none =
                ScheduleStatistics.compute(null, 20);
        assertEquals(0, none.courseCount);
        assertEquals(0, none.totalCredits, 0.001);
    }

    @Test
    public void parseCredit_extractsFirstDecimalNumber() {
        assertEquals(3.5, ScheduleStatistics.parseCredit("3.5"), 0.001);
        assertEquals(3.0, ScheduleStatistics.parseCredit("3 学分"), 0.001);
        assertEquals(2.5, ScheduleStatistics.parseCredit("学分 2.5"), 0.001);
        assertEquals(0.0, ScheduleStatistics.parseCredit("无"), 0.001);
        assertEquals(0.0, ScheduleStatistics.parseCredit(null), 0.001);
        assertEquals(0.0, ScheduleStatistics.parseCredit(""), 0.001);
    }

    @Test
    public void meetingSections_mapsModesToPeriods() {
        CourseMeeting section = meeting(0, 3, 5,
                new WeekRule(WeekRule.Type.ALL, 0, 0,
                        Collections.emptyList(), "全周"), "张老师");
        assertEquals(3, ScheduleStatistics.meetingSections(section));

        CourseMeeting clock = new CourseMeeting("clock-2", 0, 1, 1,
                new WeekRule(WeekRule.Type.ALL, 0, 0,
                        Collections.emptyList(), "全周"), "机房", "赵老师", "raw",
                CourseTimeMode.CLOCK, 8 * 60, 8 * 60 + 45);
        assertEquals(1, ScheduleStatistics.meetingSections(clock));

        assertEquals(0, ScheduleStatistics.meetingSections(null));
    }

    @Test
    public void activeWeekCount_respectsRuleAndSemesterBounds() {
        WeekRule range = new WeekRule(WeekRule.Type.RANGE, 1, 16,
                Collections.emptyList(), "1-16周");
        assertEquals(16, ScheduleStatistics.activeWeekCount(range, 20));
        assertEquals(10, ScheduleStatistics.activeWeekCount(range, 10));

        WeekRule odd = new WeekRule(WeekRule.Type.ODD, 1, 9,
                Collections.emptyList(), "1-9周(单)");
        assertEquals(5, ScheduleStatistics.activeWeekCount(odd, 20));

        WeekRule explicit = new WeekRule(WeekRule.Type.RANGE, 0, 0,
                Arrays.asList(2, 5, 7), "2,5,7周");
        assertEquals(3, ScheduleStatistics.activeWeekCount(explicit, 20));

        assertTrue(ScheduleStatistics.activeWeekCount(null, 20) >= 1);
    }
}
