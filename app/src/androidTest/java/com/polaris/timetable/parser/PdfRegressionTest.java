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
 * 2. 三校真实 PDF 基线：样例含真实姓名/学号不入库，由 adb push 至
 *    files/pdfregression/ 后本地运行；文件存在时按录制基线校验课程数，
 *    缺失时跳过（CI 不受影响）。
 */
@RunWith(AndroidJUnit4.class)
public class PdfRegressionTest {

    private static final String DIR = "pdfregression";
    private static final String[] SAMPLES = {"xupt_sample.pdf", "xaut_sample.pdf", "hdu_sample.pdf"};
    /** 录制基线（pdfbox-android 2.0.27.0）：西邮 / 西理工 / 杭电 的课程数。 */
    private static final int[] SAMPLE_BASELINE_COURSES = {11, 24, 27};

    // ---------- 第 1 层：合成文本层基线 ----------

    @Test
    public void syntheticXuptBlocks_parseCoursesWithFields() throws Exception {
        List<Object> blocks = new ArrayList<>();
        // 列头：星期一~星期五，x=60/160/260/360/460，width=60（centerX=90/190/.../490）
        for (int day = 0; day < 5; day++) {
            blocks.add(newBlock("星期" + "一二三四五".charAt(day), 0, 60f + 100f * day, 760f, 60f, 12f));
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

    // ---------- 第 2 层：三校真实 PDF 基线 ----------

    @Test
    public void threeSchoolSamples_parseBaseline() throws Exception {
        for (int i = 0; i < SAMPLES.length; i++) {
            File file = sampleFile(SAMPLES[i]);
            org.junit.Assume.assumeTrue("样例缺失则跳过: " + SAMPLES[i], file.exists());
            Uri uri = Uri.fromFile(file);
            ParseResult result = new ScheduleParser().parseDetailed(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), uri);
            // 基线断言：课程数与录制值一致（漂移即回归信号），并要求解析成功。
            assertTrue(SAMPLES[i] + " 应解析成功，errors=" + result.errors, result.success);
            assertEquals(SAMPLES[i] + " 课程数漂移（升级 pdfbox/改解析器后需复核基线）",
                    SAMPLE_BASELINE_COURSES[i], result.courses.size());
            android.util.Log.i("PdfRegression", SAMPLES[i]
                    + " courses=" + result.courses.size()
                    + " errors=" + result.errors.size());
        }
    }

    private File sampleFile(String name) {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // 优先内部存储（run-as 推入），回退外部 app 私有目录（adb push）。
        File internal = new File(ctx.getFilesDir(), DIR + "/" + name);
        if (internal.exists()) {
            return internal;
        }
        return new File(ctx.getExternalFilesDir(null), DIR + "/" + name);
    }
}
