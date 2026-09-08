package com.polaris.timetable.storage;

import android.content.SharedPreferences;

import com.polaris.timetable.model.AcademicEvent;
import com.polaris.timetable.model.StudyPlan;
import com.polaris.timetable.model.StructuredCourse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Full-state backup/restore for Polaris course schedules.
 *
 * <p>A backup captures every schedule (structured courses, per-schedule config,
 * study plans and academic events), the active schedule id, the global dark mode
 * and the account profile, and is stored as a header line followed by a JSON
 * payload. The format is versioned; V1 files remain readable. Image fields keep
 * external URI references only and never claim to contain the image bytes.</p>
 */
public final class ScheduleBackupManager {
    public static final String MIME_TYPE = "application/vnd.polaris.backup";
    public static final String EXTENSION = ".polarisbackup";

    private static final String HEADER_V1 = "POLARIS_SCHEDULE_BACKUP_V1\n";
    private static final String HEADER = "POLARIS_SCHEDULE_BACKUP_V2\n";
    private static final String FORMAT_NAME = "polaris_backup";
    private static final int FORMAT_VERSION = 2;
    private static final String IMAGE_POLICY_EXTERNAL_URI_ONLY = "external_uri_only";
    static final int MAX_BACKUP_BYTES = 5_000_000;

    private ScheduleBackupManager() {
    }

    /** One schedule inside a backup: its identity, config and canonical courses. */
    public static final class ScheduleBackup {
        public final String id;
        public final String name;
        public final ScheduleRepository.Config config;
        public final List<StructuredCourse> structuredCourses;
        public final List<StudyPlan> studyPlans;
        public final List<AcademicEvent> academicEvents;

        public ScheduleBackup(String id, String name,
                              ScheduleRepository.Config config,
                              List<StructuredCourse> structuredCourses) {
            this(id, name, config, structuredCourses,
                    Collections.<StudyPlan>emptyList(),
                    Collections.<AcademicEvent>emptyList());
        }

        public ScheduleBackup(String id, String name,
                              ScheduleRepository.Config config,
                              List<StructuredCourse> structuredCourses,
                              List<StudyPlan> studyPlans,
                              List<AcademicEvent> academicEvents) {
            this.id = normalizeScheduleId(id);
            this.name = name == null ? "默认课表" : name;
            this.config = config == null ? new ScheduleRepository.Config() : config;
            this.structuredCourses = immutableCopy(structuredCourses);
            this.studyPlans = immutableCopy(studyPlans);
            this.academicEvents = immutableCopy(academicEvents);
        }
    }

    /** The complete captured state of the app. */
    public static final class BackupBundle {
        public final String createdAt;
        public final String appVersion;
        public final String activeScheduleId;
        public final List<ScheduleBackup> schedules;
        public final String globalDarkMode;
        public final ScheduleRepository.AccountProfile accountProfile;

        public BackupBundle(String createdAt, String appVersion, String activeScheduleId,
                            List<ScheduleBackup> schedules, String globalDarkMode,
                            ScheduleRepository.AccountProfile accountProfile) {
            this.createdAt = createdAt == null ? "" : createdAt;
            this.appVersion = appVersion == null ? "" : appVersion;
            this.activeScheduleId = normalizeScheduleId(activeScheduleId);
            this.schedules = immutableCopy(schedules);
            this.globalDarkMode = globalDarkMode == null
                    ? "跟随系统" : globalDarkMode;
            this.accountProfile = accountProfile == null
                    ? new ScheduleRepository.AccountProfile() : accountProfile;
        }
    }

    /**
     * Restore failed after the target commit reported failure.
     *
     * <p>The two status flags deliberately distinguish the observable in-memory
     * map from the synchronous persistence result returned by SharedPreferences.
     * A process restart is still the final external verification for a real device.</p>
     */
    public static final class RestoreFailure extends IllegalStateException {
        public final boolean memoryRestored;
        public final boolean persistenceConfirmed;

        private RestoreFailure(String message, boolean memoryRestored,
                               boolean persistenceConfirmed) {
            super(message);
            this.memoryRestored = memoryRestored;
            this.persistenceConfirmed = persistenceConfirmed;
        }
    }

    /** Human-readable summary shown in the restore confirmation dialog. */
    public static final class BackupSummary {
        public final String createdAt;
        public final String appVersion;
        public final int scheduleCount;
        public final int courseCount;
        public final int studyPlanCount;
        public final int academicEventCount;
        public final int imageReferenceCount;

        BackupSummary(String createdAt, String appVersion,
                      int scheduleCount, int courseCount,
                      int studyPlanCount, int academicEventCount,
                      int imageReferenceCount) {
            this.createdAt = createdAt == null ? "" : createdAt;
            this.appVersion = appVersion == null ? "" : appVersion;
            this.scheduleCount = scheduleCount;
            this.courseCount = courseCount;
            this.studyPlanCount = studyPlanCount;
            this.academicEventCount = academicEventCount;
            this.imageReferenceCount = imageReferenceCount;
        }
    }

    /** Snapshots the current app state through the repository. */
    public static BackupBundle capture(ScheduleRepository repository, String appVersion) {
        if (repository == null) {
            throw new IllegalArgumentException("无法访问本机存储");
        }
        PlanRepository planRepository = new PlanRepository(repository.sharedPreferencesForStorage());
        AcademicEventRepository academicEventRepository =
                new AcademicEventRepository(repository.sharedPreferencesForStorage());
        List<ScheduleRepository.ScheduleEntry> entries = repository.loadSchedules();
        List<ScheduleBackup> schedules = new ArrayList<>(entries.size());
        for (ScheduleRepository.ScheduleEntry entry : entries) {
            schedules.add(new ScheduleBackup(
                    entry.id,
                    entry.name,
                    repository.loadConfig(entry.id),
                    repository.loadStructuredCourses(entry.id),
                    planRepository.loadPlans(entry.id),
                    academicEventRepository.loadEvents(entry.id)));
        }
        BackupBundle bundle = new BackupBundle(
                timestamp(),
                appVersion == null ? "" : appVersion,
                repository.activeScheduleId(),
                schedules,
                repository.loadGlobalDarkMode(),
                repository.loadAccountProfile());
        validateBundle(bundle);
        return bundle;
    }

    /** Serializes a bundle to the on-disk backup format (header + JSON payload). */
    public static byte[] encode(BackupBundle bundle) throws JSONException {
        validateBundle(bundle);
        JSONObject root = new JSONObject();
        root.put("format", FORMAT_NAME);
        root.put("version", FORMAT_VERSION);
        root.put("imagePolicy", IMAGE_POLICY_EXTERNAL_URI_ONLY);
        root.put("createdAt", bundle.createdAt);
        root.put("appVersion", bundle.appVersion);
        root.put("activeScheduleId", bundle.activeScheduleId);
        root.put("globalDarkMode", bundle.globalDarkMode);

        ScheduleRepository.AccountProfile profile = bundle.accountProfile;
        JSONObject account = new JSONObject();
        account.put("name", profile.name);
        account.put("avatarUri", profile.avatarUri);
        account.put("cropLeft", profile.cropLeft);
        account.put("cropTop", profile.cropTop);
        account.put("cropRight", profile.cropRight);
        account.put("cropBottom", profile.cropBottom);
        root.put("account", account);

        JSONArray scheduleArray = new JSONArray();
        for (ScheduleBackup backup : bundle.schedules) {
            JSONObject item = new JSONObject();
            item.put("id", backup.id);
            item.put("name", backup.name);
            item.put("config", backup.config.toJson());
            item.put("structuredCourses",
                    ScheduleRepository.structuredCoursesToJson(backup.structuredCourses));
            item.put("studyPlans", studyPlansToJson(backup.studyPlans));
            item.put("academicEvents", academicEventsToJson(backup.academicEvents));
            scheduleArray.put(item);
        }
        root.put("schedules", scheduleArray);

        byte[] bytes = (HEADER + root.toString()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BACKUP_BYTES) {
            throw new IllegalArgumentException("备份数据过大，无法生成备份文件");
        }
        return bytes;
    }

    /** Parses backup file bytes with strict header, version and size checks. */
    public static BackupBundle decode(byte[] bytes) throws IllegalArgumentException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BACKUP_BYTES) {
            throw new IllegalArgumentException("备份文件为空或过大");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        boolean legacyHeader = text.startsWith(HEADER_V1);
        if (!legacyHeader && !text.startsWith(HEADER)) {
            throw new IllegalArgumentException("不是有效的 Polaris 备份文件");
        }
        String json = text.substring(legacyHeader ? HEADER_V1.length() : HEADER.length());
        try {
            JSONObject root = new JSONObject(json);
            if (!FORMAT_NAME.equals(root.optString("format", ""))) {
                throw new IllegalArgumentException("不是 Polaris 备份文件");
            }
            int version = root.optInt("version", 0);
            if (version < 1) {
                throw new IllegalArgumentException("备份文件版本无效");
            }
            if (legacyHeader && version > 1) {
                throw new IllegalArgumentException("备份文件头与内容版本不一致");
            }
            if (version > FORMAT_VERSION) {
                throw new IllegalArgumentException("备份文件由更新版本创建，请先升级 Polaris 课程表");
            }

            String imagePolicy = root.optString("imagePolicy", "");
            if (imagePolicy.length() > 0
                    && !IMAGE_POLICY_EXTERNAL_URI_ONLY.equals(imagePolicy)) {
                throw new IllegalArgumentException("备份文件的图片策略不受支持");
            }

            JSONArray scheduleArray = root.optJSONArray("schedules");
            if (scheduleArray == null || scheduleArray.length() == 0) {
                throw new IllegalArgumentException("备份文件中没有课表数据");
            }
            List<ScheduleBackup> schedules = new ArrayList<>(scheduleArray.length());
            for (int i = 0; i < scheduleArray.length(); i++) {
                JSONObject item = scheduleArray.optJSONObject(i);
                if (item == null) {
                    throw new IllegalArgumentException("备份中的课表条目格式无效");
                }
                String id = normalizeScheduleId(item.optString("id", "default"));
                String name = item.optString("name", "默认课表");
                ScheduleRepository.Config config = new ScheduleRepository.Config();
                if (item.has("config")) {
                    JSONObject configObject = item.optJSONObject("config");
                    if (configObject == null) {
                        throw new IllegalArgumentException("备份中的课表配置格式无效");
                    }
                    config = ScheduleRepository.Config.fromJson(configObject);
                }

                List<StructuredCourse> courses = new ArrayList<>();
                if (item.has("structuredCourses")) {
                    JSONArray courseArray = optionalArray(item, "structuredCourses");
                    courses.addAll(ScheduleRepository.structuredCoursesFromJson(
                            courseArray.toString()));
                }
                schedules.add(new ScheduleBackup(
                        id, name, config, courses,
                        decodeStudyPlans(optionalArray(item, "studyPlans")),
                        decodeAcademicEvents(optionalArray(item, "academicEvents"))));
            }
            if (schedules.isEmpty()) {
                throw new IllegalArgumentException("备份文件中没有课表数据");
            }

            ScheduleRepository.AccountProfile profile =
                    new ScheduleRepository.AccountProfile();
            if (root.has("account")) {
                JSONObject accountObject = root.optJSONObject("account");
                if (accountObject == null) {
                    throw new IllegalArgumentException("备份中的账户信息格式无效");
                }
                profile.name = accountObject.optString("name", profile.name);
                profile.avatarUri = accountObject.optString("avatarUri", profile.avatarUri);
                profile.cropLeft = (float) accountObject.optDouble("cropLeft", profile.cropLeft);
                profile.cropTop = (float) accountObject.optDouble("cropTop", profile.cropTop);
                profile.cropRight = (float) accountObject.optDouble("cropRight", profile.cropRight);
                profile.cropBottom = (float) accountObject.optDouble("cropBottom", profile.cropBottom);
            }

            BackupBundle bundle = new BackupBundle(
                    root.optString("createdAt", ""),
                    root.optString("appVersion", ""),
                    root.optString("activeScheduleId", "default"),
                    schedules,
                    root.optString("globalDarkMode", "跟随系统"),
                    profile);
            validateBundle(bundle);
            return bundle;
        } catch (JSONException exception) {
            throw new IllegalArgumentException("备份文件内容已损坏", exception);
        }
    }

    /** Reads and validates a backup from an input stream with a size cap. */
    public static BackupBundle read(InputStream input) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("无法读取备份文件");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) {
                continue;
            }
            if (output.size() + count > MAX_BACKUP_BYTES) {
                throw new IllegalArgumentException("备份文件过大");
            }
            output.write(buffer, 0, count);
        }
        return decode(output.toByteArray());
    }

    /** Overwrites the whole local state with the backup contents. */
    public static void restoreTo(ScheduleRepository repository, BackupBundle bundle) {
        if (repository == null) {
            throw new IllegalArgumentException("无法访问本机存储");
        }
        validateBundle(bundle);

        SharedPreferences preferences = repository.sharedPreferencesForStorage();
        Map<String, Object> previousState = snapshotPreferences(preferences);
        SharedPreferences.Editor editor = preferences.edit();
        Set<String> existingKeys = previousState.keySet();
        ScheduleRepository.clearBackupKeys(editor, existingKeys);
        PlanRepository.clearBackupKeys(editor, existingKeys);
        AcademicEventRepository.clearBackupKeys(editor, existingKeys);

        PlanRepository planRepository = new PlanRepository(preferences);
        AcademicEventRepository academicEventRepository =
                new AcademicEventRepository(preferences);
        List<ScheduleRepository.ScheduleEntry> entries =
                new ArrayList<>(bundle.schedules.size());
        for (ScheduleBackup backup : bundle.schedules) {
            String id = normalizeScheduleId(backup.id);
            entries.add(new ScheduleRepository.ScheduleEntry(id, backup.name));
            repository.stageStructuredCourses(editor, id, backup.structuredCourses);
            repository.stageConfig(editor, id, backup.config);
            planRepository.stagePlans(editor, id, backup.studyPlans);
            academicEventRepository.stageEvents(editor, id, backup.academicEvents);
        }
        repository.stageSchedules(editor, entries);
        repository.stageActiveScheduleId(editor, normalizeScheduleId(bundle.activeScheduleId));
        repository.stageGlobalDarkMode(editor, bundle.globalDarkMode);
        repository.stageAccountProfile(editor, bundle.accountProfile);
        if (!editor.commit()) {
            RollbackResult rollback = rollbackPreferences(preferences, previousState);
            if (rollback.persistenceConfirmed) {
                throw new RestoreFailure("无法写入备份数据，已恢复本机状态", true, true);
            }
            if (rollback.memoryRestored) {
                throw new RestoreFailure(
                        "无法写入备份数据，内存状态已恢复，但磁盘状态未确认，请重启应用确认",
                        true, false);
            }
            throw new RestoreFailure("无法写入备份数据，且恢复本机状态失败", false, false);
        }
        repository.notifyWidgetsAfterStorageRestore();
    }

    /**
     * Copies all supported SharedPreferences values before a restore.
     *
     * <p>SharedPreferences#getAll() returns a shallow map; String sets need a
     * defensive copy because the platform exposes their backing value. Keeping
     * the complete map also preserves unrelated preferences if rollback is
     * needed.</p>
     */
    private static Map<String, Object> snapshotPreferences(SharedPreferences preferences) {
        Map<String, ?> values = preferences.getAll();
        Map<String, Object> snapshot = new HashMap<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            snapshot.put(entry.getKey(), copyPreferenceValue(entry.getKey(), entry.getValue()));
        }
        return snapshot;
    }

    private static Object copyPreferenceValue(String key, Object value) {
        if (value instanceof String || value instanceof Integer
                || value instanceof Long || value instanceof Float
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Set) {
            Set<?> source = (Set<?>) value;
            Set<String> copy = new HashSet<>();
            for (Object item : source) {
                if (!(item instanceof String)) {
                    throw new IllegalStateException("本机设置项类型不受支持: " + key);
                }
                copy.add((String) item);
            }
            return copy;
        }
        throw new IllegalStateException("本机设置项类型不受支持: " + key);
    }

    /** Best-effort reverse commit; Android applies commitToMemory before reporting disk failure. */
    private static RollbackResult rollbackPreferences(SharedPreferences preferences,
                                                       Map<String, Object> snapshot) {
        try {
            SharedPreferences.Editor editor = preferences.edit().clear();
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                putPreferenceValue(editor, entry.getKey(), entry.getValue());
            }
            boolean committed = editor.commit();
            // A platform commit may return false after the in-memory map has
            // already been restored. Verify the observable state explicitly.
            boolean stateRestored = snapshot.equals(preferences.getAll());
            return new RollbackResult(stateRestored, committed && stateRestored);
        } catch (RuntimeException exception) {
            // Preserve the original restore failure; the equality check still
            // detects the Android case where memory was restored before false.
            try {
                return new RollbackResult(snapshot.equals(preferences.getAll()), false);
            } catch (RuntimeException ignored) {
                return new RollbackResult(false, false);
            }
        }
    }

    private static final class RollbackResult {
        final boolean memoryRestored;
        final boolean persistenceConfirmed;

        RollbackResult(boolean memoryRestored, boolean persistenceConfirmed) {
            this.memoryRestored = memoryRestored;
            this.persistenceConfirmed = persistenceConfirmed;
        }
    }

    private static void putPreferenceValue(SharedPreferences.Editor editor,
                                            String key, Object value) {
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Set) {
            Set<?> source = (Set<?>) value;
            Set<String> stringSet = new HashSet<>();
            for (Object item : source) {
                if (!(item instanceof String)) {
                    throw new IllegalStateException("本机设置项类型不受支持: " + key);
                }
                stringSet.add((String) item);
            }
            editor.putStringSet(key, stringSet);
        } else {
            throw new IllegalStateException("本机设置项类型不受支持: " + key);
        }
    }

    public static BackupSummary summaryOf(BackupBundle bundle) {
        if (bundle == null) {
            return new BackupSummary("", "", 0, 0, 0, 0, 0);
        }
        int courseCount = 0;
        int studyPlanCount = 0;
        int academicEventCount = 0;
        int imageReferenceCount = 0;
        for (ScheduleBackup backup : bundle.schedules) {
            if (backup == null) {
                continue;
            }
            courseCount += backup.structuredCourses.size();
            studyPlanCount += backup.studyPlans.size();
            academicEventCount += backup.academicEvents.size();
            if (hasText(backup.config.backgroundImageUri)) {
                imageReferenceCount++;
            }
        }
        if (hasText(bundle.accountProfile.avatarUri)) {
            imageReferenceCount++;
        }
        return new BackupSummary(
                bundle.createdAt, bundle.appVersion,
                bundle.schedules.size(), courseCount,
                studyPlanCount, academicEventCount, imageReferenceCount);
    }

    private static JSONArray studyPlansToJson(List<StudyPlan> plans) {
        JSONArray array = new JSONArray();
        if (plans != null) {
            for (StudyPlan plan : plans) {
                if (plan != null) {
                    array.put(PlanRepository.toJson(plan));
                }
            }
        }
        return array;
    }

    private static JSONArray academicEventsToJson(List<AcademicEvent> events) {
        JSONArray array = new JSONArray();
        if (events != null) {
            for (AcademicEvent event : events) {
                if (event != null) {
                    array.put(AcademicEventRepository.toJson(event));
                }
            }
        }
        return array;
    }

    private static List<StudyPlan> decodeStudyPlans(JSONArray array) {
        List<StudyPlan> plans = new ArrayList<>();
        if (array == null) {
            return plans;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                throw new IllegalArgumentException("备份中的学习计划条目格式无效");
            }
            StudyPlan plan = PlanRepository.fromJson(object);
            if (plan == null || !hasText(plan.id)) {
                throw new IllegalArgumentException("备份中的学习计划条目无效");
            }
            plans.add(plan);
        }
        return plans;
    }

    private static List<AcademicEvent> decodeAcademicEvents(JSONArray array) {
        List<AcademicEvent> events = new ArrayList<>();
        if (array == null) {
            return events;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) {
                throw new IllegalArgumentException("备份中的考试/DDL条目格式无效");
            }
            String rawType = object.optString("type", "");
            if (rawType.length() > 0 && !isKnownAcademicEventType(rawType)) {
                throw new IllegalArgumentException("备份中的考试/DDL类型无效");
            }
            AcademicEvent event = AcademicEventRepository.fromJson(object);
            if (event == null || !hasText(event.id)) {
                throw new IllegalArgumentException("备份中的考试/DDL条目无效");
            }
            events.add(event);
        }
        return events;
    }

    private static JSONArray optionalArray(JSONObject object, String key) {
        if (!object.has(key)) {
            return null;
        }
        JSONArray array = object.optJSONArray(key);
        if (array == null) {
            throw new IllegalArgumentException("备份中的" + key + "格式无效");
        }
        return array;
    }

    private static boolean isKnownAcademicEventType(String rawType) {
        for (AcademicEvent.Type type : AcademicEvent.Type.values()) {
            if (type.name().equals(rawType)) {
                return true;
            }
        }
        return false;
    }

    private static void validateBundle(BackupBundle bundle) {
        if (bundle == null || bundle.schedules == null || bundle.schedules.isEmpty()) {
            throw new IllegalArgumentException("备份中没有课表数据");
        }
        Set<String> scheduleIds = new HashSet<>();
        for (ScheduleBackup backup : bundle.schedules) {
            if (backup == null) {
                throw new IllegalArgumentException("备份中的课表条目格式无效");
            }
            String scheduleId = normalizeScheduleId(backup.id);
            if (!scheduleIds.add(scheduleId)) {
                throw new IllegalArgumentException("备份中存在重复课表");
            }
            if (backup.structuredCourses == null || backup.studyPlans == null
                    || backup.academicEvents == null) {
                throw new IllegalArgumentException("备份中的课表数据不完整");
            }
            ScheduleStorageSchema.MigrationResult migration = ScheduleStorageSchema.migrate(
                    ScheduleStorageSchema.CURRENT_VERSION, backup.structuredCourses);
            if (!migration.canPersist) {
                throw new IllegalArgumentException("备份中的课程数据无效");
            }
            validateUniqueIds(backup.studyPlans, "学习计划", true);
            validateUniqueIds(backup.academicEvents, "考试/DDL", false);
            validateConfigImages(backup.config);
        }
        if (!scheduleIds.contains(normalizeScheduleId(bundle.activeScheduleId))) {
            throw new IllegalArgumentException("备份中的激活课表不存在");
        }
        validateAccountProfile(bundle.accountProfile);
    }

    private static void validateUniqueIds(
            List<?> items, String label, boolean studyPlan) {
        Set<String> ids = new HashSet<>();
        for (Object item : items) {
            String id;
            if (studyPlan) {
                id = item instanceof StudyPlan ? ((StudyPlan) item).id : "";
            } else {
                id = item instanceof AcademicEvent ? ((AcademicEvent) item).id : "";
            }
            if (!hasText(id) || !ids.add(id)) {
                throw new IllegalArgumentException("备份中的" + label + "标识无效或重复");
            }
        }
    }

    private static void validateConfigImages(ScheduleRepository.Config config) {
        if (config == null
                || !isFinite(config.backgroundCropLeft)
                || !isFinite(config.backgroundCropTop)
                || !isFinite(config.backgroundCropRight)
                || !isFinite(config.backgroundCropBottom)) {
            throw new IllegalArgumentException("备份中的背景图裁剪参数无效");
        }
    }

    private static void validateAccountProfile(ScheduleRepository.AccountProfile profile) {
        if (profile == null
                || !isFinite(profile.cropLeft)
                || !isFinite(profile.cropTop)
                || !isFinite(profile.cropRight)
                || !isFinite(profile.cropBottom)) {
            throw new IllegalArgumentException("备份中的头像裁剪参数无效");
        }
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private static String normalizeScheduleId(String id) {
        String safeId = id == null ? "" : id.trim();
        return safeId.length() == 0 ? "default" : safeId;
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
                .format(new Date());
    }
}
