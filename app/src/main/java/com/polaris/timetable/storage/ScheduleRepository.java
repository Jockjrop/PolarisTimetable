package com.polaris.timetable.storage;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.polaris.timetable.Course;
import com.polaris.timetable.SemesterStartDateDefaults;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;
import com.polaris.timetable.time.CourseTimeResolver;
import com.polaris.timetable.widget.ScheduleWidgetProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

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
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_ACCOUNT_AVATAR_URI = "account_avatar_uri";
    private static final String KEY_ACCOUNT_AVATAR_CROP_LEFT = "account_avatar_crop_left";
    private static final String KEY_ACCOUNT_AVATAR_CROP_TOP = "account_avatar_crop_top";
    private static final String KEY_ACCOUNT_AVATAR_CROP_RIGHT = "account_avatar_crop_right";
    private static final String KEY_ACCOUNT_AVATAR_CROP_BOTTOM = "account_avatar_crop_bottom";

    private final Context appContext;
    private final SharedPreferences preferences;
    private final CourseStructureMapper structureMapper = new CourseStructureMapper();

    public ScheduleRepository(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    ScheduleRepository(SharedPreferences preferences) {
        appContext = null;
        this.preferences = preferences;
    }

    public void saveStructuredCourses(List<StructuredCourse> structuredCourses) {
        saveStructuredCourses(activeScheduleId(), structuredCourses);
    }

    /**
     * Persists the canonical course model and derives the legacy JSON from the same snapshot.
     * Runtime callers must never rebuild this data from an edited flat Course list.
     */
    public void saveStructuredCourses(
            String scheduleId, List<StructuredCourse> structuredCourses) {
        ScheduleStorageSchema.MigrationResult normalized = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.CURRENT_VERSION, structuredCourses);
        List<StructuredCourse> safeStructuredCourses = normalized.courses;
        List<Course> canonicalLegacyCourses = structureMapper.toLegacyCourses(safeStructuredCourses);
        JSONArray array = new JSONArray();
        if (canonicalLegacyCourses != null) {
            for (Course course : canonicalLegacyCourses) {
                array.put(toJson(course));
            }
        }
        preferences.edit()
                .putString(courseKey(scheduleId), array.toString())
                .putString(structuredCourseKey(scheduleId), structuredCoursesToJson(safeStructuredCourses).toString())
                .putBoolean(courseReadyKey(scheduleId), true)
                .putInt(ScheduleStorageSchema.versionKey(safeScheduleId(scheduleId)),
                        ScheduleStorageSchema.CURRENT_VERSION)
                .apply();
        notifyWidgets();
    }

    /**
     * Explicit compatibility import. This is the only public write path that accepts Course[].
     * It is intended for old share payloads and migration/recovery, not runtime editing.
     */
    public List<StructuredCourse> replaceFromLegacyCourses(
            String scheduleId, List<Course> legacyCourses) {
        List<StructuredCourse> structuredCourses = structureMapper.fromLegacyCourses(legacyCourses);
        ScheduleStorageSchema.MigrationResult normalized = ScheduleStorageSchema.migrate(
                ScheduleStorageSchema.CURRENT_VERSION, structuredCourses);
        saveStructuredCourses(scheduleId, normalized.courses);
        return new ArrayList<>(normalized.courses);
    }

    public List<Course> loadCourseView() {
        return loadCourseView(activeScheduleId());
    }

    /** Returns a temporary compatibility view derived from canonical StructuredCourse data. */
    public List<Course> loadCourseView(String scheduleId) {
        return structureMapper.toLegacyCourses(loadStructuredCourses(scheduleId));
    }

    public List<StructuredCourse> loadStructuredCourses(String scheduleId) {
        List<StructuredCourse> structuredCourses = readStructuredCourses(scheduleId);
        if (structuredCourses != null) {
            return structuredCourses;
        }
        List<Course> legacyCourses = loadLegacyCourses(scheduleId);
        List<StructuredCourse> migrated = structureMapper.fromLegacyCourses(legacyCourses);
        if (!legacyCourses.isEmpty()) {
            persistMigratedCourses(scheduleId, migrated);
        }
        return migrated;
    }

    private List<Course> loadLegacyCourses(String scheduleId) {
        List<Course> courses = new ArrayList<>();
        boolean coursesReady;
        try {
            coursesReady = preferences.getBoolean(courseReadyKey(scheduleId), false);
        } catch (ClassCastException exception) {
            Log.e(TAG, "Saved course readiness flag has an invalid value type", exception);
            return courses;
        }
        if (!coursesReady) {
            return courses;
        }
        String json;
        try {
            json = preferences.getString(courseKey(scheduleId), "");
            if ((json == null || json.length() == 0) && "default".equals(scheduleId)) {
                json = preferences.getString(KEY_COURSES, "");
            }
        } catch (ClassCastException exception) {
            Log.e(TAG, "Saved legacy course data has an invalid value type", exception);
            return courses;
        }
        if (json == null || json.length() == 0) {
            return courses;
        }
        try {
            courses.addAll(legacyCoursesFromJson(json));
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
        notifyWidgets();
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
        notifyWidgets();
    }

    public String loadGlobalDarkMode() {
        return preferences.getString(KEY_GLOBAL_DARK_MODE, "跟随系统");
    }

    public void saveGlobalDarkMode(String darkMode) {
        preferences.edit().putString(KEY_GLOBAL_DARK_MODE,
                darkMode == null || darkMode.length() == 0 ? "跟随系统" : darkMode).apply();
        notifyWidgets();
    }

    public AccountProfile loadAccountProfile() {
        AccountProfile profile = new AccountProfile();
        profile.name = preferences.getString(KEY_ACCOUNT_NAME, profile.name);
        profile.avatarUri = preferences.getString(KEY_ACCOUNT_AVATAR_URI, profile.avatarUri);
        profile.cropLeft = preferences.getFloat(KEY_ACCOUNT_AVATAR_CROP_LEFT, profile.cropLeft);
        profile.cropTop = preferences.getFloat(KEY_ACCOUNT_AVATAR_CROP_TOP, profile.cropTop);
        profile.cropRight = preferences.getFloat(KEY_ACCOUNT_AVATAR_CROP_RIGHT, profile.cropRight);
        profile.cropBottom = preferences.getFloat(KEY_ACCOUNT_AVATAR_CROP_BOTTOM, profile.cropBottom);
        return profile;
    }

    public void saveAccountProfile(AccountProfile profile) {
        AccountProfile safeProfile = profile == null ? new AccountProfile() : profile;
        String name = safeProfile.name == null ? "" : safeProfile.name.trim();
        // 空名原样落库：默认名（用户+随机码）由 UI 加载侧生成并回写，这里不再代填旧默认。
        preferences.edit()
                .putString(KEY_ACCOUNT_NAME, name)
                .putString(KEY_ACCOUNT_AVATAR_URI,
                        safeProfile.avatarUri == null ? "" : safeProfile.avatarUri)
                .putFloat(KEY_ACCOUNT_AVATAR_CROP_LEFT, safeProfile.cropLeft)
                .putFloat(KEY_ACCOUNT_AVATAR_CROP_TOP, safeProfile.cropTop)
                .putFloat(KEY_ACCOUNT_AVATAR_CROP_RIGHT, safeProfile.cropRight)
                .putFloat(KEY_ACCOUNT_AVATAR_CROP_BOTTOM, safeProfile.cropBottom)
                .apply();
    }

    private void notifyWidgets() {
        if (appContext == null) {
            return;
        }
        Intent intent = new Intent(ScheduleWidgetProvider.ACTION_SCHEDULE_CHANGED);
        intent.setPackage(appContext.getPackageName());
        appContext.sendBroadcast(intent);
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
        // 新表开学日期取当前学期锚点：Config 默认值是静态日期（随学期更替必然过期），
        // 直接落库会让新建课表立刻"学期外"，周次钳制到末周导致头部显示错误的周日期。
        config.firstWeekDay = SemesterStartDateDefaults.resolve(Calendar.getInstance());
        saveConfig(id, config);
        saveStructuredCourses(id, new ArrayList<>());
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

    static JSONArray structuredCoursesToJson(List<StructuredCourse> courses) {
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

    private static JSONObject structuredCourseToJson(StructuredCourse course) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", course.id);
            object.put("name", course.name);
            object.put("teacher", course.teacher);
            object.put("defaultLocation", course.defaultLocation);
            object.put("rawText", course.rawText);
            object.put("credit", course.credit);
            object.put("color", course.color);
            object.put("courseType", course.courseType.name());
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

    private static JSONObject courseMeetingToJson(CourseMeeting meeting) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", meeting.id);
            object.put("day", meeting.day);
            object.put("startSection", meeting.startSection);
            object.put("endSection", meeting.endSection);
            object.put("timeMode", meeting.timeMode.name());
            object.put("startMinuteOfDay", meeting.startMinuteOfDay);
            object.put("endMinuteOfDay", meeting.endMinuteOfDay);
            object.put("location", meeting.location);
            object.put("teacher", meeting.teacher);
            object.put("rawText", meeting.rawText);
            object.put("weekRule", weekRuleToJson(meeting.weekRule));
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to serialize course meeting", exception);
        }
        return object;
    }

    private static JSONObject weekRuleToJson(WeekRule rule) {
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
        String json;
        try {
            json = preferences.getString(structuredCourseKey(scheduleId), null);
            if (json == null && "default".equals(scheduleId)) {
                json = preferences.getString(KEY_STRUCTURED_COURSES, null);
            }
        } catch (ClassCastException exception) {
            Log.e(TAG, "Saved structured course data has an invalid value type", exception);
            return null;
        }
        if (json == null) {
            return null;
        }
        List<StructuredCourse> courses = new ArrayList<>();
        try {
            courses.addAll(structuredCoursesFromJson(json));
            int storedVersion;
            try {
                storedVersion = preferences.getInt(
                        ScheduleStorageSchema.versionKey(safeScheduleId(scheduleId)),
                        ScheduleStorageSchema.LEGACY_VERSION);
            } catch (ClassCastException exception) {
                Log.w(TAG, "Invalid saved schema version; treating data as legacy", exception);
                storedVersion = ScheduleStorageSchema.LEGACY_VERSION;
            }
            ScheduleStorageSchema.MigrationResult migration =
                    ScheduleStorageSchema.migrate(storedVersion, courses);
            if (migration.changed && migration.canPersist) {
                persistMigratedCourses(scheduleId, migration.courses);
            }
            return migration.courses;
        } catch (JSONException exception) {
            Log.e(TAG, "Saved structured course data is corrupted; using legacy data", exception);
            return null;
        }
    }

    private void persistMigratedCourses(String scheduleId, List<StructuredCourse> structuredCourses) {
        List<Course> legacyCourses = structureMapper.toLegacyCourses(structuredCourses);
        JSONArray legacyArray = new JSONArray();
        for (Course course : legacyCourses) {
            legacyArray.put(toJson(course));
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString(structuredCourseKey(scheduleId),
                        structuredCoursesToJson(structuredCourses).toString())
                .putInt(ScheduleStorageSchema.versionKey(safeScheduleId(scheduleId)),
                        ScheduleStorageSchema.CURRENT_VERSION)
                .putBoolean(courseReadyKey(scheduleId), true);
        if (!legacyCourses.isEmpty() || !hasLegacyCourseData(scheduleId)) {
            editor.putString(courseKey(scheduleId), legacyArray.toString());
        }
        if (!editor.commit()) {
            Log.e(TAG, "Unable to persist migrated schedule data");
        }
    }

    private boolean hasLegacyCourseData(String scheduleId) {
        if (preferences.contains(courseKey(scheduleId))) {
            return true;
        }
        return "default".equals(scheduleId) && preferences.contains(KEY_COURSES);
    }

    static List<StructuredCourse> structuredCoursesFromJson(String json) throws JSONException {
        List<StructuredCourse> courses = new ArrayList<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                throw new JSONException("Structured course entry is not an object at index " + i);
            }
            courses.add(structuredCourseFromJson(object));
        }
        return courses;
    }

    static List<Course> legacyCoursesFromJson(String json) throws JSONException {
        List<Course> courses = new ArrayList<>();
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                throw new JSONException("Legacy course entry is not an object at index " + i);
            }
            courses.add(fromJson(object));
        }
        return courses;
    }

    private static StructuredCourse structuredCourseFromJson(JSONObject object) throws JSONException {
        List<CourseMeeting> meetings = new ArrayList<>();
        JSONArray meetingArray = object.optJSONArray("meetings");
        if (meetingArray != null) {
            for (int i = 0; i < meetingArray.length(); i++) {
                JSONObject meeting = meetingArray.optJSONObject(i);
                if (meeting == null) {
                    throw new JSONException("Course meeting entry is not an object at index " + i);
                }
                meetings.add(courseMeetingFromJson(meeting));
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
                object.optString("color", ""),
                CourseType.fromStorage(object.optString("courseType", "")));
    }

    private static CourseMeeting courseMeetingFromJson(JSONObject object) {
        return new CourseMeeting(
                object.optString("id", ""),
                object.optInt("day", -1),
                object.optInt("startSection", 1),
                object.optInt("endSection", 1),
                weekRuleFromJson(object.optJSONObject("weekRule")),
                object.optString("location", ""),
                object.optString("teacher", ""),
                object.optString("rawText", ""),
                CourseTimeMode.fromStorage(
                        object.optString("timeMode", ""),
                        object.optInt("day", -1),
                        object.optInt("startSection", 1),
                        object.optInt("endSection", 1),
                        object.optInt("startMinuteOfDay", -1),
                        object.optInt("endMinuteOfDay", -1)),
                object.optInt("startMinuteOfDay", -1),
                object.optInt("endMinuteOfDay", -1));
    }

    private static WeekRule weekRuleFromJson(JSONObject object) {
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

    static JSONObject toJson(Course course) {
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
            object.put("courseType", course.courseType.name());
            object.put("structuredCourseId", course.structuredCourseId);
            object.put("meetingId", course.meetingId);
            object.put("timeMode", course.timeMode.name());
            object.put("startMinuteOfDay", course.startMinuteOfDay);
            object.put("endMinuteOfDay", course.endMinuteOfDay);
        } catch (JSONException exception) {
            Log.e(TAG, "Unable to serialize course", exception);
        }
        return object;
    }

    private static Course fromJson(JSONObject object) {
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
                object.optString("color", ""),
                CourseType.fromStorage(object.optString("courseType", "")),
                object.optString("structuredCourseId", ""),
                object.optString("meetingId", ""),
                CourseTimeMode.fromStorage(
                        object.optString("timeMode", ""),
                        object.optInt("day", -1),
                        object.optInt("startSection", 1),
                        object.optInt("endSection", 1),
                        object.optInt("startMinuteOfDay", -1),
                        object.optInt("endMinuteOfDay", -1)),
                object.optInt("startMinuteOfDay", -1),
                object.optInt("endMinuteOfDay", -1));
    }

    /** 旧版默认账户名：升级后首次加载时迁移为随机默认名（1.27.7）。 */
    public static final String LEGACY_DEFAULT_ACCOUNT_NAME = "管理员";

    /**
     * 默认账户名：「用户」+ 6 位随机字母数字码（1.27.7），首位必须为字母，
     * 保证头像首字母不会抽到数字）。首次生成后由调用方持久化，保证同一设备身份
     * 稳定；仅在新装/旧默认迁移时调用。
     */
    public static String defaultAccountName() {
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String alphanumerics = letters + "0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder(6);
        // 首位强制取字母（1.27.7）：头像首字母直接取码首字符，不能是数字。
        code.append(letters.charAt(random.nextInt(letters.length())));
        for (int index = 1; index < 6; index++) {
            code.append(alphanumerics.charAt(random.nextInt(alphanumerics.length())));
        }
        return "用户" + code;
    }

    /**
     * 判断名字是否为「用户」+ 6 位字母数字码的默认名形状（1.27.7）：
     * 头像首字母对这种形状取码首字符，避免所有默认账户都显示同一个「用」字；
     * 数字开头的码是历史版本产物，形状一致则同样按码首字符显示。
     */
    public static boolean isDefaultAccountName(String name) {
        String safe = name == null ? "" : name.trim();
        if (!safe.startsWith("用户") || safe.length() != 8) {
            return false;
        }
        for (int index = 2; index < safe.length(); index++) {
            char candidate = safe.charAt(index);
            boolean isLetter = (candidate >= 'A' && candidate <= 'Z')
                    || (candidate >= 'a' && candidate <= 'z');
            boolean isDigit = candidate >= '0' && candidate <= '9';
            if (!isLetter && !isDigit) {
                return false;
            }
        }
        return true;
    }

    /**
     * 头像首字母（1.27.7）：默认名取第一个随机字符（新装生成时保证是字母，
     * 历史数字开头的码照实显示），其余名字取首字符，空名回退「用」。
     */
    public static String avatarInitial(String name) {
        String safe = name == null ? "" : name.trim();
        if (safe.isEmpty()) {
            return "用";
        }
        if (isDefaultAccountName(safe)) {
            return safe.substring(2, 3);
        }
        return safe.substring(0, safe.offsetByCodePoints(0, 1));
    }

    public static class AccountProfile {
        public String name = "";
        public String avatarUri = "";
        public float cropLeft = 0f;
        public float cropTop = 0f;
        public float cropRight = 1f;
        public float cropBottom = 1f;
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
        public String firstWeekDay = CourseTimeResolver.DEFAULT_SEMESTER_START_TEXT;
        public String semesterName = "";
        public String schoolName = "";
        public String timetableBackground = "清爽蓝";
        public String visualTheme = "极简风格";
        public String backgroundImageUri = "";
        public float backgroundCropLeft = 0f;
        public float backgroundCropTop = 0f;
        public float backgroundCropRight = 1f;
        public float backgroundCropBottom = 1f;
        public String darkMode = "跟随系统";
        public int sectionCount = 11;
        public int semesterWeeks = 20;
        public boolean remindersEnabled = true;
        public int reminderMinutesBefore = 15;
        public boolean showSaturday = false;
        public boolean showSunday = false;
        public boolean showOutOfWeekCourses = true;
        public boolean showPracticeBanner = true;
        public boolean collapseLunchBreak = true;
        public boolean collapseXautMiddleSections = true;
        public int courseCellHeight = 76;
        public int courseCornerRadius = 9;
        public int courseBlockOpacity = 100;
        public int timetableHeaderOpacity = 78;
        public int bottomNavOpacity = 86;
        public String bottomNavShape = "矩形";
        public int bottomNavHeight = 60;
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
                object.put("semesterName", semesterName);
                object.put("schoolName", schoolName);
                object.put("timetableBackground", timetableBackground);
                object.put("visualTheme", visualTheme);
                object.put("backgroundImageUri", backgroundImageUri);
                object.put("backgroundCropLeft", backgroundCropLeft);
                object.put("backgroundCropTop", backgroundCropTop);
                object.put("backgroundCropRight", backgroundCropRight);
                object.put("backgroundCropBottom", backgroundCropBottom);
                object.put("darkMode", darkMode);
                object.put("sectionCount", sectionCount);
                object.put("semesterWeeks", semesterWeeks);
                object.put("remindersEnabled", remindersEnabled);
                object.put("reminderMinutesBefore", reminderMinutesBefore);
                object.put("showSaturday", showSaturday);
                object.put("showSunday", showSunday);
                object.put("showOutOfWeekCourses", showOutOfWeekCourses);
                object.put("showPracticeBanner", showPracticeBanner);
                object.put("collapseLunchBreak", collapseLunchBreak);
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
            config.semesterName = object.optString("semesterName", config.semesterName);
            config.schoolName = object.optString("schoolName", config.schoolName);
            String legacyGradeAndSchool = object.optString("gradeAndSchool", "");
            if (legacyGradeAndSchool.contains(" · ")) {
                String[] legacyParts = legacyGradeAndSchool.split(" · ", 2);
                if (config.semesterName.length() == 0 && legacyParts[0].contains("学期")) {
                    config.semesterName = legacyParts[0];
                }
                if (config.schoolName.length() == 0) {
                    config.schoolName = legacyParts[1];
                }
            }
            config.timetableBackground = object.optString("timetableBackground", config.timetableBackground);
            config.visualTheme = object.optString("visualTheme", config.visualTheme);
            config.backgroundImageUri = object.optString("backgroundImageUri", config.backgroundImageUri);
            config.backgroundCropLeft = (float) object.optDouble(
                    "backgroundCropLeft", config.backgroundCropLeft);
            config.backgroundCropTop = (float) object.optDouble(
                    "backgroundCropTop", config.backgroundCropTop);
            config.backgroundCropRight = (float) object.optDouble(
                    "backgroundCropRight", config.backgroundCropRight);
            config.backgroundCropBottom = (float) object.optDouble(
                    "backgroundCropBottom", config.backgroundCropBottom);
            config.darkMode = object.optString("darkMode", config.darkMode);
            config.sectionCount = object.optInt("sectionCount", config.sectionCount);
            config.semesterWeeks = object.optInt("semesterWeeks", config.semesterWeeks);
            config.remindersEnabled = object.optBoolean(
                    "remindersEnabled", config.remindersEnabled);
            config.reminderMinutesBefore = object.optInt(
                    "reminderMinutesBefore", config.reminderMinutesBefore);
            config.showSaturday = object.optBoolean("showSaturday", config.showSaturday);
            config.showSunday = object.optBoolean("showSunday", config.showSunday);
            config.showOutOfWeekCourses = object.optBoolean("showOutOfWeekCourses", config.showOutOfWeekCourses);
            config.showPracticeBanner = object.optBoolean("showPracticeBanner", config.showPracticeBanner);
            config.collapseLunchBreak = object.optBoolean(
                    "collapseLunchBreak", config.collapseLunchBreak);
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
