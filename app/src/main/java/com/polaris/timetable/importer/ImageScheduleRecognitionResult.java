package com.polaris.timetable.importer;

import com.polaris.timetable.model.StructuredCourse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImageScheduleRecognitionResult {
    public static final float MIN_IMPORT_CONFIDENCE = 0.65f;

    public enum Status {
        SUCCESS,
        PARTIAL,
        FAILURE
    }

    public final Status status;
    public final boolean success;
    public final List<StructuredCourse> structuredCourses;
    public final List<OcrTextBlock> ocrBlocks;
    public final List<String> warnings;
    public final float confidence;
    public final String notice;

    public ImageScheduleRecognitionResult(
            Status status,
            List<StructuredCourse> structuredCourses,
            List<OcrTextBlock> ocrBlocks,
            List<String> warnings,
            float confidence,
            String notice) {
        this.status = status == null ? Status.FAILURE : status;
        this.success = this.status != Status.FAILURE;
        this.structuredCourses = immutableCopy(structuredCourses);
        this.ocrBlocks = immutableCopy(ocrBlocks);
        this.warnings = immutableCopy(warnings);
        this.confidence = Math.max(0f, Math.min(1f, confidence));
        this.notice = notice == null ? "" : notice;
    }

    public int meetingCount() {
        int count = 0;
        for (StructuredCourse course : structuredCourses) {
            count += course.meetings.size();
        }
        return count;
    }

    public boolean isImportable() {
        return success
                && !structuredCourses.isEmpty()
                && meetingCount() > 0
                && confidence >= MIN_IMPORT_CONFIDENCE;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return source == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(source));
    }
}
