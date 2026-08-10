package com.polaris.timetable.importer;

import android.graphics.Bitmap;

import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Fixed recognition output used to validate the image-import product flow before OCR exists. */
public final class MockImageScheduleRecognition implements ImageScheduleRecognizer {
    public MockImageScheduleRecognition() {
    }

    public static ImageScheduleRecognitionResult recognize() {
        List<StructuredCourse> courses = Arrays.asList(
                course(
                        "高等数学",
                        "张老师",
                        "A101",
                        "4.0",
                        "#4FA4F3",
                        meeting(0, 1, 2, "A101", "张老师", "1-16周"),
                        meeting(2, 1, 2, "A101", "张老师", "1-16周")),
                course(
                        "大学英语",
                        "李老师",
                        "B202",
                        "2.0",
                        "#36B889",
                        meeting(1, 3, 4, "B202", "李老师", "1-16周")),
                course(
                        "大学英语",
                        "王老师",
                        "C303",
                        "2.0",
                        "#956BD6",
                        meeting(3, 5, 6, "C303", "王老师", "1-16周")));
        return new ImageScheduleRecognitionResult(
                ImageScheduleRecognitionResult.Status.SUCCESS,
                courses,
                Collections.emptyList(),
                Collections.emptyList(),
                1f,
                "模拟识别结果，仅用于验证图片导入流程");
    }

    @Override
    public void recognize(Bitmap image, Callback callback) {
        if (callback != null) {
            callback.onResult(recognize());
        }
    }

    private static StructuredCourse course(
            String name,
            String teacher,
            String location,
            String credit,
            String color,
            CourseMeeting... meetings) {
        return new StructuredCourse(
                StableCourseId.create(),
                name,
                teacher,
                location,
                Arrays.asList(meetings),
                "模拟图片识别",
                credit,
                color,
                CourseType.LECTURE);
    }

    private static CourseMeeting meeting(
            int day,
            int startSection,
            int endSection,
            String location,
            String teacher,
            String weeksText) {
        WeekRule weeks = new WeekRule(
                WeekRule.Type.RANGE,
                1,
                16,
                Collections.emptyList(),
                weeksText);
        return new CourseMeeting(
                day,
                startSection,
                endSection,
                weeks,
                location,
                teacher,
                "模拟图片识别");
    }

}
