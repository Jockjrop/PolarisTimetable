package com.polaris.timetable.storage;

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
import java.util.List;
import java.util.Locale;

/**
 * Full-state backup/restore for Polaris course schedules.
 *
 * <p>A backup captures every schedule (structured courses + per-schedule config),
 * the active schedule id, the global dark mode and the account profile, and is
 * stored as a header line followed by a JSON payload. The format is the future
 * foundation for cloud sync: it is self-contained, versioned and pure-Java
 * (encode/decode do not touch Android framework APIs beyond org.json).</p>
 */
public final class ScheduleBackupManager {
    public static final String MIME_TYPE = "application/vnd.polaris.backup";
    public static final String EXTENSION = ".polarisbackup";

    private static final String HEADER = "POLARIS_SCHEDULE_BACKUP_V1\n";
    private static final String FORMAT_NAME = "polaris_backup";
    private static final int FORMAT_VERSION = 1;
    static final int MAX_BACKUP_BYTES = 5_000_000;

    private ScheduleBackupManager() {
    }

    /** One schedule inside a backup: its identity, config and canonical courses. */
    public static final class ScheduleBackup {
        public final String id;
        public final String name;
        public final ScheduleRepository.Config config;
        public final List<StructuredCourse> structuredCourses;

        public ScheduleBackup(String id, String name,
                              ScheduleRepository.Config config,
                              List<StructuredCourse> structuredCourses) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "默认课表" : name;
            this.config = config == null ? new ScheduleRepository.Config() : config;
            this.structuredCourses = structuredCourses == null
                    ? Collections.<StructuredCourse>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(structuredCourses));
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
            this.activeScheduleId = activeScheduleId == null
                    ? "default" : activeScheduleId;
            this.schedules = schedules == null
                    ? Collections.<ScheduleBackup>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(schedules));
            this.globalDarkMode = globalDarkMode == null
                    ? "跟随系统" : globalDarkMode;
            this.accountProfile = accountProfile == null
                    ? new ScheduleRepository.AccountProfile() : accountProfile;
        }
    }

    /** Human-readable summary shown in the restore confirmation dialog. */
    public static final class BackupSummary {
        public final String createdAt;
        public final String appVersion;
        public final int scheduleCount;
        public final int courseCount;

        BackupSummary(String createdAt, String appVersion,
                      int scheduleCount, int courseCount) {
            this.createdAt = createdAt == null ? "" : createdAt;
            this.appVersion = appVersion == null ? "" : appVersion;
            this.scheduleCount = scheduleCount;
            this.courseCount = courseCount;
        }
    }

    /** Snapshots the current app state through the repository. */
    public static BackupBundle capture(ScheduleRepository repository, String appVersion) {
        if (repository == null) {
            throw new IllegalArgumentException("无法访问本机存储");
        }
        List<ScheduleRepository.ScheduleEntry> entries = repository.loadSchedules();
        List<ScheduleBackup> schedules = new ArrayList<>(entries.size());
        for (ScheduleRepository.ScheduleEntry entry : entries) {
            schedules.add(new ScheduleBackup(
                    entry.id,
                    entry.name,
                    repository.loadConfig(entry.id),
                    repository.loadStructuredCourses(entry.id)));
        }
        return new BackupBundle(
                timestamp(),
                appVersion == null ? "" : appVersion,
                repository.activeScheduleId(),
                schedules,
                repository.loadGlobalDarkMode(),
                repository.loadAccountProfile());
    }

    /** Serializes a bundle to the on-disk backup format (header + JSON payload). */
    public static byte[] encode(BackupBundle bundle) throws JSONException {
        if (bundle == null) {
            throw new IllegalArgumentException("备份数据为空");
        }
        JSONObject root = new JSONObject();
        root.put("format", FORMAT_NAME);
        root.put("version", FORMAT_VERSION);
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
        if (!text.startsWith(HEADER)) {
            throw new IllegalArgumentException("不是有效的 Polaris 备份文件");
        }
        String json = text.substring(HEADER.length());
        try {
            JSONObject root = new JSONObject(json);
            if (!FORMAT_NAME.equals(root.optString("format", ""))) {
                throw new IllegalArgumentException("不是 Polaris 备份文件");
            }
            int version = root.optInt("version", 0);
            if (version > FORMAT_VERSION) {
                throw new IllegalArgumentException("备份文件由更新版本创建，请先升级 Polaris 课程表");
            }

            JSONArray scheduleArray = root.optJSONArray("schedules");
            if (scheduleArray == null || scheduleArray.length() == 0) {
                throw new IllegalArgumentException("备份文件中没有课表数据");
            }
            List<ScheduleBackup> schedules = new ArrayList<>(scheduleArray.length());
            for (int i = 0; i < scheduleArray.length(); i++) {
                JSONObject item = scheduleArray.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "default");
                if (id.length() == 0) {
                    id = "default";
                }
                String name = item.optString("name", "默认课表");
                ScheduleRepository.Config config = new ScheduleRepository.Config();
                JSONObject configObject = item.optJSONObject("config");
                if (configObject != null) {
                    config = ScheduleRepository.Config.fromJson(configObject);
                }
                List<StructuredCourse> courses = new ArrayList<>();
                JSONArray courseArray = item.optJSONArray("structuredCourses");
                if (courseArray != null) {
                    courses.addAll(ScheduleRepository.structuredCoursesFromJson(
                            courseArray.toString()));
                }
                schedules.add(new ScheduleBackup(id, name, config, courses));
            }
            if (schedules.isEmpty()) {
                throw new IllegalArgumentException("备份文件中没有课表数据");
            }

            ScheduleRepository.AccountProfile profile =
                    new ScheduleRepository.AccountProfile();
            JSONObject accountObject = root.optJSONObject("account");
            if (accountObject != null) {
                profile.name = accountObject.optString("name", profile.name);
                profile.avatarUri = accountObject.optString("avatarUri", profile.avatarUri);
                profile.cropLeft = (float) accountObject.optDouble("cropLeft", profile.cropLeft);
                profile.cropTop = (float) accountObject.optDouble("cropTop", profile.cropTop);
                profile.cropRight = (float) accountObject.optDouble("cropRight", profile.cropRight);
                profile.cropBottom = (float) accountObject.optDouble("cropBottom", profile.cropBottom);
            }

            return new BackupBundle(
                    root.optString("createdAt", ""),
                    root.optString("appVersion", ""),
                    root.optString("activeScheduleId", "default"),
                    schedules,
                    root.optString("globalDarkMode", "跟随系统"),
                    profile);
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
        if (bundle == null || bundle.schedules == null || bundle.schedules.isEmpty()) {
            throw new IllegalArgumentException("备份中没有课表数据");
        }
        List<ScheduleRepository.ScheduleEntry> entries =
                new ArrayList<>(bundle.schedules.size());
        for (ScheduleBackup backup : bundle.schedules) {
            String id = backup.id == null || backup.id.length() == 0
                    ? "default" : backup.id;
            entries.add(new ScheduleRepository.ScheduleEntry(id, backup.name));
            repository.saveStructuredCourses(id, backup.structuredCourses);
            repository.saveConfig(id, backup.config);
        }
        repository.saveSchedules(entries);
        repository.setActiveScheduleId(bundle.activeScheduleId == null
                || bundle.activeScheduleId.length() == 0
                ? "default" : bundle.activeScheduleId);
        repository.saveGlobalDarkMode(bundle.globalDarkMode == null
                || bundle.globalDarkMode.length() == 0
                ? "跟随系统" : bundle.globalDarkMode);
        repository.saveAccountProfile(bundle.accountProfile == null
                ? new ScheduleRepository.AccountProfile() : bundle.accountProfile);
    }

    public static BackupSummary summaryOf(BackupBundle bundle) {
        if (bundle == null) {
            return new BackupSummary("", "", 0, 0);
        }
        int courseCount = 0;
        for (ScheduleBackup backup : bundle.schedules) {
            courseCount += backup.structuredCourses == null
                    ? 0 : backup.structuredCourses.size();
        }
        return new BackupSummary(
                bundle.createdAt, bundle.appVersion,
                bundle.schedules.size(), courseCount);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
                .format(new Date());
    }
}
