package com.polaris.timetable;

import com.polaris.timetable.model.CourseType;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ScheduleParserTest {
    @Test
    public void cleanupCourseName_removesOrphanedCreditPrefixFromXuptName() throws Exception {
        assertEquals("无线定位技术", cleanupCourseName("分:3.0无线定位技术◆"));
        assertEquals("无线定位技术", cleanupCourseName("分 ： 3.0 无线定位技术◇"));
    }

    @Test
    public void cleanupCourseName_preservesLegitimateNamesContainingFen() throws Exception {
        assertEquals("微积分", cleanupCourseName("微积分◆"));
        assertEquals("学分制专题", cleanupCourseName("学分制专题◇"));
    }

    @Test
    public void extractCourseType_readsXuptCourseMarkersBeforeSection() throws Exception {
        assertEquals(CourseType.LECTURE, extractCourseType("高等数学◆/(1-2节)1-16周"));
        assertEquals(CourseType.EXPERIMENT, extractCourseType("大学物理实验◇/(3-4节)1-8周"));
        assertEquals(CourseType.PRACTICE, extractCourseType("工程训练●/(5-6节)项目周"));
        assertEquals(CourseType.ONLINE, extractCourseType("网络课程○/(7-8节)1-16周"));
        assertEquals(CourseType.LECTURE, extractCourseType("没有标记的课程/(1-2节)1-16周"));
    }

    @Test
    public void extractXuptFooterCourses_readsExperimentAndPracticeWithoutSections() throws Exception {
        String text = "实践课程： 物联网智能计算实验◇王宏刚(共8周)/9-16周; "
                + "物联网综合工程实践●庞胜利(共16周)/1-16周; "
                + "○: 网络 ◆: 讲课 ◇: 实验 ●: 实践 打印时间:2026-07-23";

        List<Course> courses = extractXuptFooterCourses(text);

        assertEquals(2, courses.size());
        assertBannerCourse(courses.get(0), "物联网智能计算实验", "王宏刚",
                "9-16周", CourseType.EXPERIMENT);
        assertBannerCourse(courses.get(1), "物联网综合工程实践", "庞胜利",
                "1-16周", CourseType.PRACTICE);
    }

    @Test
    public void extractXuptFooterCourses_ignoresLegendWithoutPracticePrefix() throws Exception {
        assertEquals(0, extractXuptFooterCourses("○: 网络 ◆: 讲课 ◇: 实验 ●: 实践").size());
    }

    @Test
    public void cleanupWeeks_preservesUnifiedWeekExpressions() throws Exception {
        assertEquals("1-8周 单周", cleanupWeeks("1-8周 单周"));
        assertEquals("2、5-6周", cleanupWeeks("2、5-6周"));
        assertEquals("1，3，5周", cleanupWeeks("1，3，5周"));
        assertEquals("1-2周，5–6周", cleanupWeeks("1-2周，5–6周"));
    }

    @Test
    public void mergeDuplicates_keepsSameCourseWithDifferentTeachersSeparate() throws Exception {
        Course first = new Course(
                0, 1, 2, "大学英语", "1-16周", "A101", "张老师", "raw-1");
        Course second = new Course(
                0, 1, 2, "大学英语", "1-16周", "A101", "李老师", "raw-2");

        List<Course> merged = mergeDuplicates(Arrays.asList(first, second));

        assertEquals(2, merged.size());
        assertEquals("张老师", merged.get(0).teacher);
        assertEquals("李老师", merged.get(1).teacher);
    }

    @Test
    public void mergeDuplicates_stillCollapsesExactDuplicateFromSameTeacher() throws Exception {
        Course first = new Course(
                0, 1, 2, "大学英语", "1-16周", "A101", "张老师", "raw-1");
        Course duplicate = new Course(
                0, 1, 2, "大学英语", "1-16周", "A101", " 张 老师 ", "raw-2");

        List<Course> merged = mergeDuplicates(Arrays.asList(first, duplicate));

        assertEquals(1, merged.size());
    }

    private String cleanupCourseName(String value) throws Exception {
        ScheduleParser parser = new ScheduleParser();
        Method method = ScheduleParser.class.getDeclaredMethod("cleanupCourseName", String.class);
        method.setAccessible(true);
        return (String) method.invoke(parser, value);
    }

    private CourseType extractCourseType(String value) throws Exception {
        ScheduleParser parser = new ScheduleParser();
        Method method = ScheduleParser.class.getDeclaredMethod("extractCourseType", String.class);
        method.setAccessible(true);
        return (CourseType) method.invoke(parser, value);
    }

    private String cleanupWeeks(String value) throws Exception {
        ScheduleParser parser = new ScheduleParser();
        Method method = ScheduleParser.class.getDeclaredMethod("cleanupWeeks", String.class);
        method.setAccessible(true);
        return (String) method.invoke(parser, value);
    }

    @SuppressWarnings("unchecked")
    private List<Course> mergeDuplicates(List<Course> courses) throws Exception {
        ScheduleParser parser = new ScheduleParser();
        Method method = ScheduleParser.class.getDeclaredMethod("mergeDuplicates", List.class);
        method.setAccessible(true);
        return (List<Course>) method.invoke(parser, courses);
    }

    @SuppressWarnings("unchecked")
    private List<Course> extractXuptFooterCourses(String value) throws Exception {
        ScheduleParser parser = new ScheduleParser();
        Method method = ScheduleParser.class.getDeclaredMethod("extractXuptFooterCourses", String.class);
        method.setAccessible(true);
        return (List<Course>) method.invoke(parser, value);
    }

    private void assertBannerCourse(Course course, String name, String teacher,
                                    String weeks, CourseType courseType) {
        assertEquals(-1, course.day);
        assertEquals(0, course.startSection);
        assertEquals(0, course.endSection);
        assertEquals(name, course.name);
        assertEquals(teacher, course.teacher);
        assertEquals(weeks, course.weeks);
        assertEquals(courseType, course.courseType);
    }
}
