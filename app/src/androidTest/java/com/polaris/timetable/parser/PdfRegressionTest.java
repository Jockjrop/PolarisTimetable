package com.polaris.timetable.parser;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.polaris.timetable.Course;
import com.polaris.timetable.ScheduleParser;
import com.polaris.timetable.model.ParseResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * PDF 解析回归护栏（两层）：
 *
 * 1. 合成文本层基线：用反射构造解析器内部 TextBlock，注入 XUPT 解析管道，
 *    对课程数/名称/节次/周次/地点做硬断言——不依赖外部文件，CI 每次都跑，
 *    防止解析器改动让已知好输入退化。
 * 2. 三校匿名 PDF 基线：每所学校独立使用仓库内固定版式样例，
 *    从测试 APK assets 复制后走完整 PDFBox 解析链，校验课程数与关键字段。
 */
@RunWith(AndroidJUnit4.class)
public class PdfRegressionTest {

    private static final String DIR = "pdfregression";

    // ---------- 第 1 层：合成文本层基线 ----------

    @Test
    public void syntheticXuptBlocks_parseCoursesWithFields() throws Exception {
        List<Object> blocks = new ArrayList<>();
        // 列头：星期一~星期五，x=60/160/260/360/460，width=60（centerX=90/190/.../490）
        for (int day = 0; day < 5; day++) {
            blocks.add(newBlock("星期" + "一二三四五".charAt(day), 0, 60f + 100f * day, 760f, 60f, 12f));
        }
        // 左侧节次行标题也会包含 "1-2节"，但不能被误判为第一天的课程种子。
        for (int row = 0; row < 6; row++) {
            int start = row * 2 + 1;
            blocks.add(newBlock(start + "-" + (start + 1) + "节",
                    0, 20f, 660f + row * 50f, 28f, 12f));
        }
        // 单元格格式：名称(起-止节)周次/地点/教师；地点用 ROOM_PATTERN 支持的
        // 教务格式（字母楼号+数字，如 A-101），与真实 XUPT PDF 一致。
        blocks.add(newBlock("高等数学(1-2节)1-16周/A-101/张三", 0, 70f, 700f, 90f, 30f));
        blocks.add(newBlock("大学英语(3-4节)1-16周/B-202/李四", 0, 270f, 700f, 90f, 30f));
        blocks.add(newBlock("数据结构(5-6节)2-8周/C-303/王五", 0, 470f, 700f, 90f, 30f));

        ParseResult result = invokeXuptPipeline(blocks);

        assertTrue("合成基线应解析成功，errors=" + result.errors, result.success);
        assertEquals("课程数应为 3", 3, result.courses.size());

        Course math = findCourse(result.courses, 0);
        assertEquals("高等数学", math.name);
        assertEquals(1, math.startSection);
        assertEquals(2, math.endSection);
        assertTrue("周次应含 1-16，实际=" + math.weeks, math.weeks.contains("1-16"));
        assertTrue("地点应含 A-101，实际=" + math.location, math.location.contains("A-101"));

        Course english = findCourse(result.courses, 2);
        assertEquals("大学英语", english.name);
        assertEquals(3, english.startSection);
        assertEquals(4, english.endSection);

        Course structure = findCourse(result.courses, 4);
        assertEquals("数据结构", structure.name);
        assertTrue("周次应含 2-8，实际=" + structure.weeks, structure.weeks.contains("2-8"));
        assertTrue("地点应含 C-303，实际=" + structure.location, structure.location.contains("C-303"));
    }

    @Test
    public void syntheticXuptBlocks_emptyInput_failsCleanly() throws Exception {
        ParseResult result = invokeXuptPipeline(new ArrayList<>());
        assertEquals("空输入不应解析出课程", 0, result.courses.size());
        assertTrue("空输入不应标记 success", !result.success);
    }

    /** 反射进入 ScheduleParser.parseBlocks(List&lt;TextBlock&gt;, ParseDiagnostics, int)。 */
    private ParseResult invokeXuptPipeline(List<Object> blocks) throws Exception {
        Method parseBlocks = ScheduleParser.class.getDeclaredMethod("parseBlocks",
                List.class, ParseDiagnostics.class, int.class);
        parseBlocks.setAccessible(true);
        return (ParseResult) parseBlocks.invoke(
                new ScheduleParser(), blocks, new ParseDiagnostics(), 1);
    }

    /** 按解析器内部 TextBlock 签名构造块实例。 */
    private Object newBlock(String text, int page, float x, float y, float width, float height)
            throws Exception {
        Constructor<?> blockCtor = Class
                .forName("com.polaris.timetable.ScheduleParser$TextBlock")
                .getDeclaredConstructor(String.class, int.class, float.class,
                        float.class, float.class, float.class);
        blockCtor.setAccessible(true);
        return blockCtor.newInstance(text, page, x, y, width, height);
    }

    private static Course findCourse(List<Course> courses, int day) {
        for (Course course : courses) {
            if (course.day == day) {
                return course;
            }
        }
        throw new AssertionError("未找到 day=" + day + " 的课程，实际=" + courses);
    }

    // ---------- 第 2 层：三校匿名 PDF 基线 ----------

    @Test
    public void xuptAnonymousFixture_parseFields() throws Exception {
        ParseResult result = parseFixture("xupt_anonymous.pdf", SchoolParserModel.XUPT);
        assertFixture(result, "XUPT", 3);
        assertCourse(result, 0, "高等数学", 1, 2, "1-16", "A-101", "李老师");
        assertCourse(result, 2, "数据结构", 3, 4, "2-8", "B-202", "王老师");
        assertCourse(result, 4, "大学英语", 5, 6, "1-17", "C-303", "赵老师");
    }

    @Test
    public void xautAnonymousFixture_parseFields() throws Exception {
        ParseResult result = parseFixture("xaut_anonymous.pdf", SchoolParserModel.XAUT);
        assertFixture(result, "XAUT", 3);
        assertCourse(result, 0, "电路分析", 1, 2, "1-16", "曲江1-101", "王老师");
        assertCourse(result, 2, "信号与系统", 3, 4, "2-14", "工程训练基地", "赵老师");
        assertCourse(result, 4, "自动控制原理", 7, 8, "3-15", "三电实验中心教1-201", "周老师");
    }

    @Test
    public void hduAnonymousFixture_parseFields() throws Exception {
        ParseResult result = parseFixture("hdu_anonymous.pdf", SchoolParserModel.HDU);
        assertFixture(result, "HDU", 3);
        assertCourse(result, 1, "操作系统", 1, 2, "1-17", "教学楼101", "陈老师");
        assertCourse(result, 3, "计算机网络", 3, 4, "1-16", "教学楼202", "刘老师");
        assertCourse(result, 5, "算法设计", 5, 6, "2-16", "实验楼303", "孙老师");
    }

    private ParseResult parseFixture(String name, SchoolParserModel parserModel)
            throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File directory = new File(ctx.getCacheDir(), DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建 PDF 回归样例目录: " + directory);
        }
        File file = new File(directory, name);
        try (InputStream input = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(DIR + "/" + name);
             FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
        }
        return new ScheduleParser().parseDetailed(ctx, Uri.fromFile(file), parserModel);
    }

    private void assertFixture(ParseResult result, String label, int courseCount) {
        assertTrue(label + " 匿名样例应解析成功，errors=" + result.errors, result.success);
        assertEquals(label + " 课程数漂移", courseCount, result.courses.size());
        assertEquals(label + " 应为单页样例", 1, result.pageCount);
        assertEquals(label + " 结构化课程数漂移", courseCount, result.structuredCourses.size());
    }

    private void assertCourse(ParseResult result, int day, String name,
                              int start, int end, String weeks,
                              String location, String teacher) {
        Course course = findCourse(result.courses, day);
        assertEquals(name, course.name);
        assertEquals(start, course.startSection);
        assertEquals(end, course.endSection);
        assertTrue(name + " 周次不匹配: " + course.weeks, course.weeks.contains(weeks));
        assertTrue(name + " 地点不匹配: " + course.location,
                course.location.contains(location));
        assertTrue(name + " 教师不匹配: " + course.teacher,
                course.teacher.contains(teacher));
    }
}
