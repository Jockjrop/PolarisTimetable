package com.polaris.timetable.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** FreeSlotCalculator 纯逻辑测试：节次占用按分钟重叠判定，两种时间模式统一处理。 */
public class FreeSlotCalculatorTest {

    private static final CourseTimeResolver.Settings SETTINGS =
            CourseTimeResolver.defaultSettings();
    private static final int SECTION_COUNT = 11;

    private static Course sectionCourse(int day, int start, int end, String weeks) {
        return new Course(day, start, end, "课程", weeks, "", "", "");
    }

    @Test
    public void noCourses_wholeDayFree() {
        List<int[]> ranges = FreeSlotCalculator.freeSectionsForDay(
                new ArrayList<>(), 0, SETTINGS, SECTION_COUNT);
        assertEquals(1, ranges.size());
        assertRange(ranges.get(0), 1, SECTION_COUNT);
    }

    @Test
    public void morningCourse_leavesAfternoonFree() {
        List<Course> courses = Collections.singletonList(
                sectionCourse(0, 1, 2, "1-20"));
        List<int[]> monday = FreeSlotCalculator.freeSectionsForDay(
                courses, 0, SETTINGS, SECTION_COUNT);
        assertEquals(1, monday.size());
        assertRange(monday.get(0), 3, SECTION_COUNT);
        // 其他天不受影响
        List<int[]> tuesday = FreeSlotCalculator.freeSectionsForDay(
                courses, 1, SETTINGS, SECTION_COUNT);
        assertRange(tuesday.get(0), 1, SECTION_COUNT);
    }

    @Test
    public void twoCourses_mergeIntoContiguousOccupancy() {
        // 第一节默认 08:00-08:50（节1），第三节 10:20-11:10 覆盖节 3-4 附近，
        // 用具体节次构造两个间隔课程，验证空闲区间被正确切分与合并。
        List<Course> courses = Arrays.asList(
                sectionCourse(0, 1, 2, "1-20"),
                sectionCourse(0, 5, 6, "1-20"));
        List<int[]> ranges = FreeSlotCalculator.freeSectionsForDay(
                courses, 0, SETTINGS, SECTION_COUNT);
        assertEquals(2, ranges.size());
        assertRange(ranges.get(0), 3, 4);
        assertRange(ranges.get(1), 7, SECTION_COUNT);
    }

    @Test
    public void clockMode_courseOccupiesOverlappingSections() {
        // 第一节 08:00-08:50，第二节 09:00-09:50：08:20-09:30 的钟点课占节 1-2。
        Course clock = new Course(0, -1, -1, "实验", "1-20", "", "", "", "", "",
                CourseType.LECTURE, "", "", CourseTimeMode.CLOCK, 8 * 60 + 20, 9 * 60 + 30);
        List<Course> courses = Collections.singletonList(clock);
        List<int[]> ranges = FreeSlotCalculator.freeSectionsForDay(
                courses, 0, SETTINGS, SECTION_COUNT);
        assertEquals(1, ranges.size());
        assertRange(ranges.get(0), 3, SECTION_COUNT);
    }

    @Test
    public void courseOutsideSectionSpans_occupiesNothing() {
        // 深夜钟点课不落在任何节次区间内，不影响空闲计算。
        Course night = new Course(0, -1, -1, "晚自习", "1-20", "", "", "", "", "",
                CourseType.LECTURE, "", "", CourseTimeMode.CLOCK, 23 * 60, 23 * 60 + 50);
        List<int[]> ranges = FreeSlotCalculator.freeSectionsForDay(
                Collections.singletonList(night), 0, SETTINGS, SECTION_COUNT);
        assertRange(ranges.get(0), 1, SECTION_COUNT);
    }

    @Test
    public void weekFiltering_leftToCaller() {
        // 计算器不解析周次：weeks="2-20" 的课程在 day 参数匹配时仍按占用计，
        // 周过滤由调用方（按当周课程传入）负责——此处验证非本周传入也会占用，
        // 提醒调用方必须先过滤。
        List<Course> courses = Collections.singletonList(
                sectionCourse(0, 1, 2, "2-20"));
        List<int[]> ranges = FreeSlotCalculator.freeSectionsForDay(
                courses, 0, SETTINGS, SECTION_COUNT);
        assertTrue(ranges.get(0)[0] > 1);
    }

    private static void assertRange(int[] range, int start, int end) {
        assertEquals(start, range[0]);
        assertEquals(end, range[1]);
    }
}
