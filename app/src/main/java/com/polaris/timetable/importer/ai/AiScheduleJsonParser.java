package com.polaris.timetable.importer.ai;

import com.polaris.timetable.importer.ai.dto.AiCourse;
import com.polaris.timetable.importer.ai.dto.AiMeeting;
import com.polaris.timetable.importer.ai.dto.AiPracticeCourse;
import com.polaris.timetable.importer.ai.dto.AiScheduleImport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Strict JSON decoder for Polaris Schedule JSON v1 DTOs. */
public final class AiScheduleJsonParser {
    public Result parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            String format = optionalString(root, "format", Context.root());
            String semester = optionalString(root, "semester", Context.root());
            List<AiCourse> courses = parseCourses(requiredArray(root, "courses", Context.root()));
            List<AiPracticeCourse> practiceCourses = parsePracticeCourses(
                    requiredArray(root, "practiceCourses", Context.root()));
            return Result.success(new AiScheduleImport(
                    format, semester, courses, practiceCourses));
        } catch (SchemaException exception) {
            return Result.failure(exception.issue);
        } catch (JSONException | NullPointerException exception) {
            String detail = exception.getMessage();
            String message = detail == null || detail.trim().isEmpty()
                    ? "JSON 语法错误"
                    : "JSON 语法错误: " + detail;
            return Result.failure(AiImportIssue.parsing(
                    AiImportIssue.Code.PARSE_ERROR, -1, "", -1, -1,
                    "", message));
        }
    }

    private List<AiCourse> parseCourses(JSONArray array)
            throws JSONException, SchemaException {
        List<AiCourse> courses = new ArrayList<>();
        for (int courseIndex = 0; courseIndex < array.length(); courseIndex++) {
            Context context = Context.course(courseIndex, "");
            JSONObject object = requiredObject(array, courseIndex, context, "courses");
            String name = optionalString(object, "name", context);
            context = Context.course(courseIndex, name);
            String teacher = optionalString(object, "teacher", context);
            String teachingClass = optionalString(object, "teachingClass", context);
            Double credit = optionalDouble(object, "credit", context);
            JSONArray meetingsArray = requiredArray(object, "meetings", context);
            List<AiMeeting> meetings = parseMeetings(meetingsArray, context);
            courses.add(new AiCourse(name, teacher, teachingClass, credit, meetings));
        }
        return courses;
    }

    private List<AiMeeting> parseMeetings(JSONArray array, Context courseContext)
            throws JSONException, SchemaException {
        List<AiMeeting> meetings = new ArrayList<>();
        for (int meetingIndex = 0; meetingIndex < array.length(); meetingIndex++) {
            Context context = courseContext.meeting(meetingIndex);
            JSONObject object = requiredObject(array, meetingIndex, context, "meetings");
            int dayOfWeek = requiredInt(object, "dayOfWeek", context);
            int startSection = requiredInt(object, "startSection", context);
            int endSection = requiredInt(object, "endSection", context);
            String weekRule = optionalString(object, "weekRule", context);
            String campus = optionalString(object, "campus", context);
            String location = optionalString(object, "location", context);
            meetings.add(new AiMeeting(dayOfWeek, startSection, endSection,
                    weekRule, campus, location));
        }
        return meetings;
    }

    private List<AiPracticeCourse> parsePracticeCourses(JSONArray array)
            throws JSONException, SchemaException {
        List<AiPracticeCourse> courses = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            Context context = Context.practiceCourse(index, "");
            JSONObject object = requiredObject(array, index, context, "practiceCourses");
            String name = optionalString(object, "name", context);
            context = Context.practiceCourse(index, name);
            String teacher = optionalString(object, "teacher", context);
            String weekRule = optionalString(object, "weekRule", context);
            courses.add(new AiPracticeCourse(name, teacher, weekRule));
        }
        return courses;
    }

    private JSONArray requiredArray(JSONObject object, String field, Context context)
            throws JSONException, SchemaException {
        if (!object.has(field) || object.isNull(field)) {
            throw schema(AiImportIssue.Code.REQUIRED_FIELD, context, field,
                    field + " 为必填数组");
        }
        Object value = object.get(field);
        if (!(value instanceof JSONArray)) {
            throw schema(AiImportIssue.Code.FIELD_TYPE, context, field,
                    field + " 必须是 JSON array");
        }
        return (JSONArray) value;
    }

    private JSONObject requiredObject(JSONArray array, int index, Context context, String field)
            throws JSONException, SchemaException {
        Object value = array.get(index);
        if (!(value instanceof JSONObject)) {
            throw schema(AiImportIssue.Code.FIELD_TYPE, context, field,
                    field + "[" + index + "] 必须是 JSON object");
        }
        return (JSONObject) value;
    }

    private String optionalString(JSONObject object, String field, Context context)
            throws JSONException, SchemaException {
        if (!object.has(field) || object.isNull(field)) {
            return null;
        }
        Object value = object.get(field);
        if (!(value instanceof String)) {
            throw schema(AiImportIssue.Code.FIELD_TYPE, context, field,
                    field + " 必须是 string 或 null");
        }
        return (String) value;
    }

    private Double optionalDouble(JSONObject object, String field, Context context)
            throws JSONException, SchemaException {
        if (!object.has(field) || object.isNull(field)) {
            return null;
        }
        Object value = object.get(field);
        if (!(value instanceof Number)) {
            throw schema(AiImportIssue.Code.FIELD_TYPE, context, field,
                    field + " 必须是 number 或 null");
        }
        return ((Number) value).doubleValue();
    }

    private int requiredInt(JSONObject object, String field, Context context)
            throws JSONException, SchemaException {
        if (!object.has(field) || object.isNull(field)) {
            throw schema(AiImportIssue.Code.REQUIRED_FIELD, context, field,
                    field + " 为必填整数");
        }
        Object value = object.get(field);
        if (!(value instanceof Number)) {
            throw schema(AiImportIssue.Code.FIELD_TYPE, context, field,
                    field + " 必须是 integer");
        }
        double numericValue = ((Number) value).doubleValue();
        if (Double.isNaN(numericValue)
                || Double.isInfinite(numericValue)
                || numericValue != Math.rint(numericValue)
                || numericValue < Integer.MIN_VALUE
                || numericValue > Integer.MAX_VALUE) {
            throw schema(AiImportIssue.Code.FIELD_TYPE, context, field,
                    field + " 必须是 32 位 integer");
        }
        return (int) numericValue;
    }

    private SchemaException schema(AiImportIssue.Code code, Context context,
                                   String field, String message) {
        return new SchemaException(AiImportIssue.parsing(
                code, context.courseIndex, context.courseName, context.meetingIndex,
                context.practiceCourseIndex, field, message));
    }

    public static final class Result {
        public final AiScheduleImport data;
        public final List<AiImportIssue> errors;

        private Result(AiScheduleImport data, List<AiImportIssue> errors) {
            this.data = data;
            this.errors = errors;
        }

        public boolean isSuccess() {
            return data != null && errors.isEmpty();
        }

        static Result success(AiScheduleImport data) {
            return new Result(data, Collections.emptyList());
        }

        static Result failure(AiImportIssue issue) {
            return new Result(null, Collections.singletonList(issue));
        }
    }

    private static final class SchemaException extends Exception {
        final AiImportIssue issue;

        SchemaException(AiImportIssue issue) {
            this.issue = issue;
        }
    }

    private static final class Context {
        final int courseIndex;
        final String courseName;
        final int meetingIndex;
        final int practiceCourseIndex;

        private Context(int courseIndex, String courseName,
                        int meetingIndex, int practiceCourseIndex) {
            this.courseIndex = courseIndex;
            this.courseName = courseName;
            this.meetingIndex = meetingIndex;
            this.practiceCourseIndex = practiceCourseIndex;
        }

        static Context root() {
            return new Context(-1, "", -1, -1);
        }

        static Context course(int index, String name) {
            return new Context(index, name, -1, -1);
        }

        static Context practiceCourse(int index, String name) {
            return new Context(-1, name, -1, index);
        }

        Context meeting(int index) {
            return new Context(courseIndex, courseName, index, -1);
        }
    }
}
