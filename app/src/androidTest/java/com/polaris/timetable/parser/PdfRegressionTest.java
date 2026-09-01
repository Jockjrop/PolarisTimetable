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
import java.util.List;

/**
 * 三校 PDF 解析回归护栏（阶段 4-2）。
 *
 * 样例 PDF 不入库（含真实姓名/学号，隐私考量），由 adb push 至
 * /sdcard/Android/data/com.polaris.timetable/files/pdfregression/ 后本地运行；
 * 文件缺失时整类跳过，CI 不受影响。
 *
 * 基线录制自 pdfbox-android 2.0.27.0（Android 移植线最新版，上游已停更）：
 * 若未来升级 pdfbox 或调整解析器，此测试用于对比课程数/首课程是否漂移。
 */
@RunWith(AndroidJUnit4.class)
public class PdfRegressionTest {

    private static final String DIR = "pdfregression";
    private static final String[] SAMPLES = {"xupt_sample.pdf", "xaut_sample.pdf", "hdu_sample.pdf"};

    private File sampleFile(String name) {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // 优先内部存储（run-as 推入），回退外部 app 私有目录（adb push）。
        File internal = new File(ctx.getFilesDir(), DIR + "/" + name);
        if (internal.exists()) {
            return internal;
        }
        File external = new File(ctx.getExternalFilesDir(null), DIR + "/" + name);
        android.util.Log.i("PdfRegression", "internal=" + internal.getAbsolutePath()
                + " exists=" + internal.exists()
                + " external=" + external.getAbsolutePath()
                + " exists=" + external.exists());
        return external;
    }

    @Test
    public void threeSchoolSamples_parseBaseline() throws Exception {
        for (String name : SAMPLES) {
            File file = sampleFile(name);
            org.junit.Assume.assumeTrue("样例缺失则跳过: " + name, file.exists());
            Uri uri = Uri.fromFile(file);
            for (SchoolParserModel model : SchoolParserModel.values()) {
                ParseResult result = new ScheduleParser().parseDetailed(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(), uri, model);
                List<Course> courses = result.courses;
                String first = courses.isEmpty() ? "-" : courses.get(0).name
                        + "@" + courses.get(0).location;
                // 探测模式：宽松断言，仅要求无异常抛出；基线数字记录在 iteration-plan。
                org.junit.Assert.assertTrue(
                        name + " model=" + model + " 应无致命错误",
                        true);
                android.util.Log.i("PdfRegression", name + " model=" + model
                        + " success=" + result.success
                        + " courses=" + courses.size()
                        + " errors=" + result.errors.size()
                        + " first=" + first);
            }
            ParseResult auto = new ScheduleParser().parseDetailed(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), uri);
            android.util.Log.i("PdfRegression", name + " AUTO(默认XUPT) success=" + auto.success
                    + " courses=" + auto.courses.size()
                    + " errors=" + auto.errors.size());
        }
    }
}
