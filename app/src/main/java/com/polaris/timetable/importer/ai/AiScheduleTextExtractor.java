package com.polaris.timetable.importer.ai;

import com.polaris.timetable.importer.ai.dto.AiScheduleImport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts one complete JSON object without attempting to repair its contents. */
public final class AiScheduleTextExtractor {
    private static final Pattern JSON_FENCE =
            Pattern.compile("```\\s*json(?:\\s|$)", Pattern.CASE_INSENSITIVE);

    public Result extract(String source) {
        String text = source == null ? "" : source;
        int markerIndex = text.indexOf(AiScheduleImport.OUTPUT_MARKER);
        if (markerIndex >= 0) {
            return extractObject(text,
                    markerIndex + AiScheduleImport.OUTPUT_MARKER.length(), text.length());
        }

        Matcher fenceMatcher = JSON_FENCE.matcher(text);
        if (fenceMatcher.find()) {
            int fenceEnd = text.indexOf("```", fenceMatcher.end());
            int searchEnd = fenceEnd >= 0 ? fenceEnd : text.length();
            return extractObject(text, fenceMatcher.end(), searchEnd);
        }
        return extractObject(text, 0, text.length());
    }

    private Result extractObject(String text, int searchStart, int searchEnd) {
        int objectStart = text.indexOf('{', searchStart);
        if (objectStart < 0 || objectStart >= searchEnd) {
            return Result.failure(AiImportIssue.extraction(
                    AiImportIssue.Code.JSON_NOT_FOUND, "未找到 JSON object"));
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = objectStart; index < searchEnd; index++) {
            char character = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) {
                    return Result.success(text.substring(objectStart, index + 1));
                }
            }
        }
        return Result.failure(AiImportIssue.extraction(
                AiImportIssue.Code.PARSE_ERROR, "JSON object 不完整，缺少匹配的结束花括号"));
    }

    public static final class Result {
        public final String json;
        public final AiImportIssue error;

        private Result(String json, AiImportIssue error) {
            this.json = json;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null;
        }

        static Result success(String json) {
            return new Result(json, null);
        }

        static Result failure(AiImportIssue error) {
            return new Result(null, error);
        }
    }
}
