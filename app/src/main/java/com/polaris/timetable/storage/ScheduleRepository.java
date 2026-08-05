package com.polaris.timetable.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ScheduleRepository {
    private static final String TAG = "ScheduleRepository";
    private static final String PREFS_NAME = "polaris_schedule";
    private static final String KEY_COURSES = "courses";
    private static final String KEY_STRUCTURED_COURSES = "structured_courses";
    private static final String KEY_CONFIG = "config";
    private static final String KEY_COURSES_READY = "courses_ready";
    private static final String KEY_ACTIVE_SCHEDULE = "active_schedule";
    private static final String KEY_SCHEDULES = "schedules";
    private static final String KEY_GLOBAL_DARK_MODE = "global_dark_mode";

    private final SharedPreferences preferences;
    private final CourseStructureMapper structureMapper = new CourseStructureMapper();

    public ScheduleRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveCourses(List<Course> courses) {
        saveCourses(activeScheduleId(), courses);
    }

    public void saveCourses(String scheduleId, List<Course> courses) {
        saveCourses(scheduleId, courses, structureMapper.fromLegacyCourses(courses));
    }

    public void saveCourses(
            String scheduleId,
            List<Course> courses,
            List<StructuredCourse> structuredCourses) {
        JSONArray array = new JSONArray();
        if (courses != null) {
            for (Course course : courses) {
                array.put(toJson(course));
            }
        }
        List<StructuredCourse> safeStructuredCourses = structuredCourses;
        if ((safeStructuredCourses == null || safeStructuredCourses.isEmpty())
                && courses != null && !courses.isEmpty()) {
            safeStructuredCourses = structureMapper.fromLegacyCourses(courses);
        }
        preferences.edit()
                .putString(courseKey(scheduleId), array.toString())
                .putString(structuredCourseKey(scheduleId), structuredCoursesToJson(safeStructuredCourses).toString())
                .putBoolean(courseReadyKey(scheduleId), true)
                .apply();
    }

    public List<Course> loadCourses() {
        return loadCourses(activeScheduleId());
    }

    public List<Course> loadCourses(String scheduleId) {
        List<StructuredCourse> structuredCourses = readStructuredCourses(scheduleId);
        if (structuredCourses != null) {
            return structureMapper.toLegacyCourses(structuredCourses);
        }
        return loadLegacyCourses(scheduleId);
    }

    public List<StructuredCourse> loadStructuredCourses(String scheduleId) {
        List<StructuredCourse> structuredCourses = readStructuredCourses(scheduleId);
        if (structuredCourses != null) {
            return structuredCourses;
        }
        return structureMapper.fromLegacyCourses(loadLegacyCourses(scheduleId));
    }

    private List<Course> loadLegacyCourses(String scheduleId) {
        List<Course> courses = new ArrayList<>();
        if (!preferences.getBoolean(courseReadyKey(scheduleId), false)) {
            return courses;
        }
        String json = preferences.getString(courseKey(scheduleId), "");
        if ((json == null || json.length() == 0) && "default".equals(scheduleId)) {
            json = preferences.getString(KEY_COURSES, "");
        }
        if (json == null || json.length() == 0) {
            return courses;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    courses.add(fromJson(item));
                }
            }
        } catch (JSONException exception) {
            Log.e(TAG, "Saved course data is corrupted", exception);
            return new ArrayList<>();
        }
        return courses;
    }

    public void saveConfig(Config config) {
        saveConfig(activeScheduleId(), config);
    }

    public void saveConfig(String scheduleId, Config config) {
        preferences.edit().putString(configKey(scheduleId), config.toJson().toString()).apply();
    }

    public Config loadConfig() {
        return loadConfig(activeScheduleId());
    }

    public Config loadConfig(String scheduleId) {
        String json = preferences.getString(configKey(scheduleId), "");
        if ((json == null || json.length() == 0) && "default".equals(scheduleId)) {
            json = preferences.getString(KEY_CONFIG, "");
        }
        if (json == null || json.length() == 0) {
            return new Config();
        }
        try {
            return Config.fromJson(new JSONObject(json));
        } catch (JSONException exception) {
            Log.e(TAG, "Saved schedule configuration is corrupted", exception);
            return new Config();
        }
    }

    public String activeScheduleId() {
        return preferences.getString(KEY_ACTIVE_SCHEDULE, "default");
    }

    public void setActiveScheduleId(String scheduleId) {
        preferences.edit().putString(KEY_ACTIVE_SCHEDULE, safeScheduleId(scheduleId)).apply();
    }

    public String loadGlobalDarkMode() {
        return preferences.getString(KEY_GLOBAL_DARK_MODE, "跟随系统");
    }

    public void saveGlobalDarkMode(String darkMode) {
        preferences.edit().putString(KEY_GLOBAL_DARK_MODE,
                darkMode == null || darkMode.length() == 0 ? "跟随系统" : darkMode).apply();
    }

    public List<ScheduleEntry> loadSchedules() {
        List<ScheduleEntry> schedules = new ArrayList<>();
        String json = preferences.getString(KEY_SCHEDULES, "");
        if (json != null && json.length() > 0) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item != null) {
                        schedules.add(new ScheduleEntry(
                                item.optString("id", "default"),
                                item.optString("name", "默认课表")));
                    }
                }
            } catch (JSONException exception) {
                Log.e(TAG, "Saved schedule list is corrupted", exception);
                schedules.clear();
            }
        }
        if (schedules.isEmpty()) {
            schedules.add(new ScheduleEntry("default", loadConfig("default").scheduleName));
            saveSchedules(schedules);
        }
        return schedules;
    }

    public void saveSchedules(List<ScheduleEntry> schedules) {
        JSONArray array = new JSONArray();
        for (ScheduleEntry schedule : schedules) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", schedule.id);
                object.put("name", schedule.name);
                array.put(object);
            } catch (JSONException exception) {
                Log.e(TAG, "Unable to serialize schedule entry", exception);
            }
        }
        preferences.edit().putString(KEY_SCHEDULES, array.toString()).apply();
    }

    public ScheduleEntry createSchedule(String name) {
        List<ScheduleEntry> schedules = loadSchedules();
        String id = "schedule_" + System.currentTimeMillis();
        ScheduleEntry entry = new ScheduleEntry(id, name == null || name.length() == 0 ? "新课表" : name);
        schedules.add(entry);
        saveSchedules(schedules);
        Config config = new Config();
        config.scheduleName = entry.name;
        saveConfig(id, config);
        saveCourses(id, new ArrayList<>());
        return entry;
    }

    private String courseKey(String scheduleId) {
        return KEY_COURSES + "_" + safeScheduleId(scheduleId);
    }

    private String structuredCourseKey(String scheduleId) {
        return KEY_STRUCTURED_COURSES + "_" + safeScheduleId(scheduleId);
    }

    private String courseReadyKey(String scheduleId) {
        return KEY_COURSES_READY + "_" + safeScheduleId(scheduleId);
    }

    private String configKey(String scheduleId) {
        return KEY_CONFIG + "_" + safeScheduleId(scheduleId);
    }

    private String safeScheduleId(String scheduleId) {
        return scheduleId == null || scheduleId.length() == 0 ? "default" : scheduleId;
    }

    private JSONArray structuredCoursesToJson(List<StructuredCourse> courses) {
        JSONArray array = new JSONArray();
        if (courses == null) {
            return array;
        }
        for (StructuredCourse course : courses) {
            if (course != null) {
                array.put(structuredCourseToJson(course));
            }
        }
        return array;
    }

    private JSONObject structuredCourseToJson(StructuredCourse course) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", course.id);
            object.put("name", course.name);
            object.put("teacher", course.teacher);
            object.put("defaultLocation", course.defaultLocation);
            object.put("rawText", course.rawText);
            object.put("credit", course.credit);
            object.put("color", course.color);
            JSONArray meetings = new JSONArray();
            for (CourseMeeting meeting : course.meetings) {
                meetings.put(courseMeetingToJson(meeting));
            }
            object.put("meetings", meetings);
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to serialize structured course", exception);
        }
        return object;
    }

    private JSONObject courseMeetingToJson(CourseMeeting meeting) {
        JSONObject object = new JSONObject();
        try {
            object.put("day", meeting.day);
            object.put("startSection", meeting.startSection);
            object.put("endSection", meeting.endSection);
            object.put("location", meeting.location);
            object.put("teacher", meeting.teacher);
            object.put("rawText", meeting.rawText);
            object.put("weekRule", weekRuleToJson(meeting.weekRule));
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to serialize course meeting", exception);
        }
        return object;
    }

    private JSONObject weekRuleToJson(WeekRule rule) {
        JSONObject object = new JSONObject();
        if (rule == null) {
            return object;
        }
        try {
            object.put("type", rule.type.name());
            object.put("startWeek", rule.startWeek);
            object.put("endWeek", rule.endWeek);
            object.put("rawText", rule.rawText);
            JSONArray explicitWeeks = new JSONArray();
            for (Integer week : rule.explicitWeeks) {
                explicitWeeks.put(week);
            }
            object.put("explicitWeeks", explicitWeeks);
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to serialize week rule", exception);
        }
        return object;
    }

    private List<StructuredCourse> readStructuredCourses(String scheduleId) {
        String json = preferences.getString(structuredCourseKey(scheduleId), null);
        if (json == null && "default".equals(scheduleId)) {
            json = preferences.getString(KEY_STRUCTURED_COURSES, null);
        }
        if (json == null) {
            return null;
        }
        List<StructuredCourse> courses = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) {
                    courses.add(structuredCourseFromJson(object));
                }
            }
            return courses;
        } catch (JSONException exception) {
            Log.e(TAG, "Saved structured course data is corrupted; using legacy data", exception);
            return null;
        }
    }

    private StructuredCourse structuredCourseFromJson(JSONObject object) {
        List<CourseMeeting> meetings = new ArrayList<>();
        JSONArray meetingArray = object.optJSONArray("meetings");
        if (meetingArray != null) {
            for (int i = 0; i < meetingArray.length(); i++) {
                JSONObject meeting = meetingArray.optJSONObject(i);
                if (meeting != null) {
                    meetings.add(courseMeetingFromJson(meeting));
                }
            }
        }
        return new StructuredCourse(
                object.optString("id", ""),
                object.optString("name", ""),
                object.optString("teacher", ""),
                object.optString("defaultLocation", ""),
                meetings,
                object.optString("rawText", ""),
                object.optString("credit", ""),
                object.optString("color", ""));
    }

    private CourseMeeting courseMeetingFromJson(JSONObject object) {
        return new CourseMeeting(
                object.optInt("day", -1),
                object.optInt("startSection", 1),
                object.optInt("endSection", 1),
                weekRuleFromJson(object.optJSONObject("weekRule")),
                object.optString("location", ""),
                object.optString("teacher", ""),
                object.optString("rawText", ""));
    }

    private WeekRule weekRuleFromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        WeekRule.Type type;
        try {
            type = WeekRule.Type.valueOf(object.optString("type", WeekRule.Type.UNKNOWN.name()));
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "Unknown saved week rule type", exception);
            type = WeekRule.Type.UNKNOWN;
        }
        List<Integer> explicitWeeks = new ArrayList<>();
        JSONArray explicitArray = object.optJSONArray("explicitWeeks");
        if (explicitArray != null) {
            for (int i = 0; i < explicitArray.length(); i++) {
                int week = explicitArray.optInt(i, -1);
                if (week > 0) {
                    explicitWeeks.add(week);
                }
            }
        }
        return new WeekRule(
                type,
                object.optInt("startWeek", 0),
                object.optInt("endWeek", 0),
                explicitWeeks,
                object.optString("rawText", ""));
    }

    private JSONObject toJson(Course course) {
        JSONObject object = new JSONObject();
        try {
            object.put("day", course.day);
            object.put("startSection", course.startSection);
            object.put("endSection", course.endSection);
            object.put("name", course.name);
            object.put("weeks", course.weeks);
            object.put("location", course.location);
            object.put("teacher", course.teacher);
            object.put("raw", course.raw);
            object.put("credit", course.credit);
            object.put("color", course.color);
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to serialize course", exception);
        }
        return object;
    }

    private Course fromJson(JSONObject object) {
        return new Course(
                object.optInt("day", -1),
                object.optInt("startSection", 1),
                object.optInt("endSection", 1),
                object.optString("name", "未命名课程"),
                object.optString("weeks", "周次见PDF"),
                object.optString("location", ""),
                object.optString("teacher", ""),
                object.optString("raw", ""),
                object.optString("credit", ""),
                object.optString("color", ""));
    }

    public static class Config {
        public String scheduleName = "默认课表";
        public String classTimeConfig = "08:00 开始";
        public String firstClassStartTime = "08:00";
        public int classDurationMinutes = 50;
        public int classBreakMinutes = 10;
        public int classBigBreakMinutes = 30;
        public String afternoonStartTime = "14:30";
        public String lateAfternoonStartTime = "16:35";
        public String parserModel = "";
        public String firstWeekDay = "2026/3/3";
        public String gradeAndSchool = "2025级 · Polaris大学";
        public String timetableBackground = "清爽蓝";
        public String backgroundImageUri = "";
        public String darkMode = "跟随系统";
        public int sectionCount = 11;
        public int semesterWeeks = 20;
        public boolean showSaturday = false;
        public boolean showSunday = false;
        public boolean showOutOfWeekCourses = true;
        public boolean collapseXautMiddleSections = false;
        public int courseCellHeight = 76;
        public int courseCornerRadius = 9;
        public int courseBlockOpacity = 100;
        public int timetableHeaderOpacity = 78;
        public int bottomNavOpacity = 86;
        public String bottomNavShape = "矩形";
        public int bottomNavHeight = 72;
        public int bottomNavCornerRadius = 58;
        public int bottomNavRectCornerRadius = 58;
        public int bottomNavSplitCornerRadius = 58;
        public int bottomNavSideCornerRadius = 58;
        public int bottomNavSideMargin = 10;
        public boolean shellBarsBlurEnabled = true;

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("scheduleName", scheduleName);
                object.put("classTimeConfig", classTimeConfig);
                object.put("firstClassStartTime", firstClassStartTime);
                object.put("classDurationMinutes", classDurationMinutes);
                object.put("classBreakMinutes", classBreakMinutes);
                object.put("classBigBreakMinutes", classBigBreakMinutes);
                object.put("afternoonStartTime", afternoonStartTime);
                object.put("lateAfternoonStartTime", lateAfternoonStartTime);
                object.put("parserModel", parserModel);
                object.put("firstWeekDay", firstWeekDay);
                object.put("gradeAndSchool", gradeAndSchool);
                object.put("timetableBackground", timetableBackground);
                object.put("backgroundImageUri", backgroundImageUri);
                object.put("darkMode", darkMode);
                object.put("sectionCount", sectionCount);
                object.put("semesterWeeks", semesterWeeks);
                object.put("showSaturday", showSaturday);
                object.put("showSunday", showSunday);
                object.put("showOutOfWeekCourses", showOutOfWeekCourses);
                object.put("collapseXautMiddleSections", collapseXautMiddleSections);
                object.put("courseCellHeight", courseCellHeight);
                object.put("courseCornerRadius", courseCornerRadius);
                object.put("courseBlockOpacity", courseBlockOpacity);
                object.put("timetableHeaderOpacity", timetableHeaderOpacity);
                object.put("bottomNavOpacity", bottomNavOpacity);
                object.put("bottomNavShape", bottomNavShape);
                object.put("bottomNavHeight", bottomNavHeight);
                object.put("bottomNavCornerRadius", bottomNavCornerRadius);
                object.put("bottomNavRectCornerRadius", bottomNavRectCornerRadius);
                object.put("bottomNavSplitCornerRadius", bottomNavSplitCornerRadius);
                object.put("bottomNavSideCornerRadius", bottomNavSideCornerRadius);
                object.put("bottomNavSideMargin", bottomNavSideMargin);
                object.put("shellBarsBlurEnabled", shellBarsBlurEnabled);
            } catch (JSONException exception) {
                Log.e(TAG, "Unable to serialize schedule configuration", exception);
            }
            return object;
        }

        static Config fromJson(JSONObject object) {
            Config config = new Config();
            config.scheduleName = object.optString("scheduleName", config.scheduleName);
            config.classTimeConfig = object.optString("classTimeConfig", config.classTimeConfig);
            config.firstClassStartTime = object.optString(
                    "firstClassStartTime", firstTimeFromConfig(config.classTimeConfig));
            config.classDurationMinutes = object.optInt("classDurationMinutes", config.classDurationMinutes);
            config.classBreakMinutes = object.optInt("classBreakMinutes", config.classBreakMinutes);
            config.classBigBreakMinutes = object.optInt("classBigBreakMinutes", config.classBigBreakMinutes);
            config.afternoonStartTime = object.optString("afternoonStartTime", config.afternoonStartTime);
            config.lateAfternoonStartTime = object.optString("lateAfternoonStartTime", config.lateAfternoonStartTime);
            config.parserModel = object.optString("parserModel", config.parserModel);
            config.firstWeekDay = object.optString("firstWeekDay", config.firstWeekDay);
            config.gradeAndSchool = object.optString("gradeAndSchool", config.gradeAndSchool);
            config.timetableBackground = object.optString("timetableBackground", config.timetableBackground);
            config.backgroundImageUri = object.optString("backgroundImageUri", config.backgroundImageUri);
            config.darkMode = object.optString("darkMode", config.darkMode);
            config.sectionCount = object.optInt("sectionCount", config.sectionCount);
            config.semesterWeeks = object.optInt("semesterWeeks", config.semesterWeeks);
            config.showSaturday = object.optBoolean("showSaturday", config.showSaturday);
            config.showSunday = object.optBoolean("showSunday", config.showSunday);
            config.showOutOfWeekCourses = object.optBoolean("showOutOfWeekCourses", config.showOutOfWeekCourses);
            config.collapseXautMiddleSections = object.optBoolean("collapseXautMiddleSections", config.collapseXautMiddleSections);
            config.courseCellHeight = object.optInt("courseCellHeight", config.courseCellHeight);
            config.courseCornerRadius = object.optInt("courseCornerRadius", config.courseCornerRadius);
            config.courseBlockOpacity = object.optInt("courseBlockOpacity", config.courseBlockOpacity);
            config.timetableHeaderOpacity = object.optInt("timetableHeaderOpacity", config.timetableHeaderOpacity);
            config.bottomNavOpacity = object.optInt("bottomNavOpacity", config.bottomNavOpacity);
            config.bottomNavShape = object.optString("bottomNavShape", config.bottomNavShape);
            config.bottomNavHeight = object.optInt("bottomNavHeight", config.bottomNavHeight);
            config.bottomNavCornerRadius = object.optInt("bottomNavCornerRadius", config.bottomNavCornerRadius);
            config.bottomNavRectCornerRadius = object.optInt("bottomNavRectCornerRadius", config.bottomNavRectCornerRadius);
            config.bottomNavSplitCornerRadius = object.optInt("bottomNavSplitCornerRadius", config.bottomNavSplitCornerRadius);
            config.bottomNavSideCornerRadius = object.optInt("bottomNavSideCornerRadius", config.bottomNavSideCornerRadius);
            config.bottomNavSideMargin = object.optInt("bottomNavSideMargin", config.bottomNavSideMargin);
            config.shellBarsBlurEnabled = object.optBoolean("shellBarsBlurEnabled", config.shellBarsBlurEnabled);
            return config;
        }

        private static String firstTimeFromConfig(String value) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})")
                    .matcher(value == null ? "" : value);
            if (!matcher.find()) {
                return "08:00";
            }
            int hour = Math.max(0, Math.min(23, Integer.parseInt(matcher.group(1))));
            int minute = Math.max(0, Math.min(59, Integer.parseInt(matcher.group(2))));
            return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
        }
    }

    public static class ScheduleEntry {
        public final String id;
        public final String name;

        public ScheduleEntry(String id, String name) {
            this.id = id == null ? "default" : id;
            this.name = name == null ? "默认课表" : name;
        }
    }
}
