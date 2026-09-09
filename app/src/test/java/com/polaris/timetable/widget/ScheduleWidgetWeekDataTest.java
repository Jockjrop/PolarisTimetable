package com.polaris.timetable.widget;

import com.polaris.timetable.Course;
import com.polaris.timetable.storage.ScheduleRepository;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScheduleWidgetWeekDataTest {

    @Test
    public void buildRows_placesCourseInDayAndSectionColumns() {
        ScheduleRepository.Config config = config();
        Course mondayMorning = course(0, 1, 2, "高等数学", "1-2周");
        Course fridayAfternoon = course(4, 6, 6, "大学物理", "1-2周");

        List<ScheduleWidgetWeekData.Row> rows = ScheduleWidgetWeekData.buildRows(
                Arrays.asList(mondayMorning, fridayAfternoon), config,
                date(2026, Calendar.MARCH, 2), 5);

        assertEquals(config.sectionCount, rows.size());
        // 跨节课程在覆盖的每个节次行重复占格（第1、2节）。
        assertEquals("高等数学", rows.get(0).cells[0].text);
        assertEquals("高等数学", rows.get(1).cells[0].text);
        assertEquals(rows.get(0).cells[0].color, rows.get(1).cells[0].color);
        // 第3节同列应为空格。
        assertEquals("", rows.get(2).cells[0].text);
        assertEquals("大学物理", rows.get(5).cells[4].text);
    }

    @Test
    public void buildRows_showsSectionStartTime() {
        ScheduleRepository.Config config = config();
        List<ScheduleWidgetWeekData.Row> rows = ScheduleWidgetWeekData.buildRows(
                Collections.singletonList(course(0, 1, 1, "高等数学", "1-2周")),
                config, date(2026, Calendar.MARCH, 2), 5);

        assertEquals("08:00", rows.get(0).sectionTime);
        // 第2节从 09:00 开始（50 分钟课 + 10 分钟休息）。
        assertEquals("09:00", rows.get(1).sectionTime);
    }

    @Test
    public void buildRows_hidesColumnsBeyondSelectedDayCount() {
        ScheduleRepository.Config config = config();
        Course saturday = course(5, 1, 2, "周六选修", "1-2周");
        Course sunday = course(6, 3, 4, "周日选修", "1-2周");

        List<ScheduleWidgetWeekData.Row> rows = ScheduleWidgetWeekData.buildRows(
                Arrays.asList(saturday, sunday), config,
                date(2026, Calendar.MARCH, 2), 5);
        // 5 天时周六/周日列不参与铺排：整周无可见课程 → 空列表（空态文案兜底）。
        assertTrue(rows.isEmpty());

        rows = ScheduleWidgetWeekData.buildRows(
                Arrays.asList(saturday, sunday), config,
                date(2026, Calendar.MARCH, 2), 6);
        assertEquals(config.sectionCount, rows.size());
        assertEquals("周六选修", rows.get(0).cells[5].text);
        assertEquals("", rows.get(0).cells[6].text);

        rows = ScheduleWidgetWeekData.buildRows(
                Arrays.asList(saturday, sunday), config,
                date(2026, Calendar.MARCH, 2), 7);
        assertEquals("周日选修", rows.get(2).cells[6].text);
    }

    @Test
    public void buildRows_outsideSemesterReturnsEmpty() {
        ScheduleRepository.Config config = config();
        // 2026/3/3 起的第 22 周（超出 semesterWeeks=20）。
        List<ScheduleWidgetWeekData.Row> rows = ScheduleWidgetWeekData.buildRows(
                Collections.singletonList(course(0, 1, 2, "高等数学", "1-2周")),
                config, date(2026, Calendar.JULY, 27), 5);
        assertTrue(rows.isEmpty());
    }

    @Test
    public void buildRows_picksEarliestCourseWhenSectionsOverlap() {
        ScheduleRepository.Config config = config();
        Course morning = course(0, 1, 2, "高等数学", "1-2周");
        Course second = course(0, 2, 2, "大学英语", "1-2周");

        List<ScheduleWidgetWeekData.Row> rows = ScheduleWidgetWeekData.buildRows(
                Arrays.asList(morning, second), config,
                date(2026, Calendar.MARCH, 2), 5);

        // 第2节同时被两门课覆盖：开始更早的高等数学占格。
        assertEquals("高等数学", rows.get(1).cells[0].text);
    }

    @Test
    public void buildRows_ignoresBannerOnlyCourses() {
        ScheduleRepository.Config config = config();
        Course practice = new Course(-1, 0, 0, "工程训练", "1-2周", "", "", "", "", "",
                com.polaris.timetable.model.CourseType.PRACTICE);

        List<ScheduleWidgetWeekData.Row> rows = ScheduleWidgetWeekData.buildRows(
                Collections.singletonList(practice), config,
                date(2026, Calendar.MARCH, 2), 5);
        assertTrue(rows.isEmpty());
    }

    @Test
    public void buildRows_carriesLocationOnStartSectionOnly() {
        ScheduleRepository.Config config = config();
        // 跨 1-2 节、带地点的课程：首节格携带地点，续节格地点留空。
        List<ScheduleWidgetWeekData.Row> rows = ScheduleWidgetWeekData.buildRows(
                Collections.singletonList(course(0, 1, 2, "高等数学", "1-2周")),
                config, date(2026, Calendar.MARCH, 2), 5);

        assertEquals("A101", rows.get(0).cells[0].location);
        assertTrue(rows.get(0).cells[0].startOfCourse);
        assertEquals("", rows.get(1).cells[0].location);
        assertTrue(!rows.get(1).cells[0].startOfCourse);
    }

    @Test
    public void buildRows_locationEmptyWhenCourseHasNoLocation() {
        ScheduleRepository.Config config = config();
        Course noLocation = new Course(0, 1, 1, "体育", "1-2周", "", "教师", "");
        List<ScheduleWidgetWeekData.Row> rows = ScheduleWidgetWeekData.buildRows(
                Collections.singletonList(noLocation), config,
                date(2026, Calendar.MARCH, 2), 5);

        assertEquals("", rows.get(0).cells[0].location);
    }

    @Test
    public void buildRows_todayEmptyColumnGetsTintAndNoText() {
        ScheduleRepository.Config config = config();
        // 2026/3/2 是周一（todayDay=0）：当天无课时，今天列空格应有淡强调底。
        Course tuesdayOnly = course(1, 1, 1, "大学英语", "1-2周");
        List<ScheduleWidgetWeekData.Row> rows = ScheduleWidgetWeekData.buildRows(
                Collections.singletonList(tuesdayOnly), config,
                date(2026, Calendar.MARCH, 2), 5);

        ScheduleWidgetWeekData.Cell todayCell = rows.get(0).cells[0];
        assertEquals("", todayCell.text);
        assertEquals(ScheduleWidgetWeekData.todayColumnTint(), todayCell.color);
        assertEquals("", todayCell.location);
    }

    @Test
    public void weekDates_alignedToMondayColumns() {
        Calendar wednesday = date(2026, Calendar.MARCH, 4);
        Calendar[] dates = ScheduleWidgetWeekData.weekDates(wednesday);
        assertEquals(7, dates.length);
        assertEquals(2, dates[0].get(Calendar.DAY_OF_MONTH)); // 2026/3/2 周一
        assertEquals(8, dates[6].get(Calendar.DAY_OF_MONTH)); // 2026/3/8 周日
    }

    @Test
    public void weekDates_sundayBelongsToCurrentWeek() {
        // 2026/3/8 是周日：本周一为 3/2，周日列即当天。
        Calendar sunday = date(2026, Calendar.MARCH, 8);
        Calendar[] dates = ScheduleWidgetWeekData.weekDates(sunday);
        assertEquals(2, dates[0].get(Calendar.DAY_OF_MONTH));
        assertEquals(8, dates[6].get(Calendar.DAY_OF_MONTH));
    }

    private ScheduleRepository.Config config() {
        ScheduleRepository.Config config = new ScheduleRepository.Config();
        config.firstWeekDay = "2026/3/3";
        config.semesterWeeks = 20;
        config.firstClassStartTime = "08:00";
        config.classDurationMinutes = 50;
        config.classBreakMinutes = 10;
        config.classBigBreakMinutes = 30;
        config.afternoonStartTime = "14:30";
        config.lateAfternoonStartTime = "16:35";
        return config;
    }

    private Course course(int day, int start, int end, String name, String weeks) {
        return new Course(day, start, end, name, weeks, "A101", "教师", "");
    }

    private Calendar date(int year, int month, int day) {
        Calendar date = Calendar.getInstance();
        date.set(year, month, day, 12, 0, 0);
        date.set(Calendar.MILLISECOND, 0);
        return date;
    }
}
