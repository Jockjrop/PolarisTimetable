package com.polaris.timetable.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.SharedPreferences;

import com.polaris.timetable.model.AcademicEvent;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StudyPlan;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScheduleBackupManagerTest {
    private static final String FIRST_ID = "11111111-1111-4111-8111-111111111111";
    private static final String SECOND_ID = "22222222-2222-4222-8222-222222222222";

    @Test
    public void captureEncodeDecode_roundtripPreservesEveryField() throws Exception {
        ScheduleRepository repository = repositoryWithTwoSchedules();

        ScheduleBackupManager.BackupBundle bundle =
                ScheduleBackupManager.capture(repository, "1.2.0");
        byte[] bytes = ScheduleBackupManager.encode(bundle);
        assertTrue(new String(bytes, StandardCharsets.UTF_8)
                .startsWith("POLARIS_SCHEDULE_BACKUP_V2\n"));
        ScheduleBackupManager.BackupBundle restored =
                ScheduleBackupManager.decode(bytes);

        assertEquals("1.2.0", restored.appVersion);
        assertEquals("default", restored.activeScheduleId);
        assertEquals("深色", restored.globalDarkMode);
        assertEquals("小明", restored.accountProfile.name);
        assertEquals(0.1f, restored.accountProfile.cropLeft, 0.001f);
        assertEquals(0.9f, restored.accountProfile.cropBottom, 0.001f);
        assertEquals(2, restored.schedules.size());

        ScheduleBackupManager.ScheduleBackup first = restored.schedules.get(0);
        assertEquals("default", first.id);
        assertEquals("2026春课表", first.name);
        assertEquals("西安邮电大学", first.config.schoolName);
        assertEquals(20, first.config.semesterWeeks);
        assertEquals(1, first.structuredCourses.size());
        assertStructuredEquals(course(FIRST_ID), first.structuredCourses.get(0));
        assertEquals(1, first.studyPlans.size());
        assertEquals("复习信号处理", first.studyPlans.get(0).title);
        assertEquals(4, first.studyPlans.get(0).week);
        assertEquals(1, first.academicEvents.size());
        assertEquals("实验报告截止", first.academicEvents.get(0).title);
        assertEquals(AcademicEvent.Type.DEADLINE, first.academicEvents.get(0).type);

        ScheduleBackupManager.ScheduleBackup second = restored.schedules.get(1);
        assertEquals("schedule_1", second.id);
        assertEquals("备用课表", second.name);
        assertEquals(0, second.structuredCourses.size());
        assertTrue(second.studyPlans.isEmpty());
        assertTrue(second.academicEvents.isEmpty());
    }

    @Test
    public void restoreTo_overwritesEmptyLocalStateWithBackup() throws Exception {
        ScheduleRepository source = repositoryWithTwoSchedules();
        ScheduleBackupManager.BackupBundle bundle =
                ScheduleBackupManager.capture(source, "1.2.0");

        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        ScheduleRepository target = new ScheduleRepository(preferences);
        ScheduleBackupManager.restoreTo(target, bundle);

        assertEquals("default", target.activeScheduleId());
        assertEquals("深色", target.loadGlobalDarkMode());
        assertEquals("小明", target.loadAccountProfile().name);
        List<ScheduleRepository.ScheduleEntry> schedules = target.loadSchedules();
        assertEquals(2, schedules.size());
        assertEquals("2026春课表", schedules.get(0).name);
        assertEquals("备用课表", schedules.get(1).name);
        List<StructuredCourse> courses = target.loadStructuredCourses("default");
        assertEquals(1, courses.size());
        assertStructuredEquals(course(FIRST_ID), courses.get(0));
        assertEquals("西安邮电大学", target.loadConfig("default").schoolName);
        assertEquals(0, target.loadStructuredCourses("schedule_1").size());
        assertEquals(1, new PlanRepository(preferences).loadPlans("default").size());
        assertEquals(1, new AcademicEventRepository(preferences).loadEvents("default").size());

        // SharedPreferences 缓存被重新创建后仍应读到已恢复的持久化快照。
        ScheduleRepository restarted = new ScheduleRepository(preferences.reopened());
        assertEquals("default", restarted.activeScheduleId());
        assertEquals("深色", restarted.loadGlobalDarkMode());
        assertEquals("小明", restarted.loadAccountProfile().name);
        assertEquals(1, restarted.loadStructuredCourses("default").size());
        assertEquals(1, new PlanRepository(preferences.reopened()).loadPlans("default").size());
        assertEquals(1, new AcademicEventRepository(preferences.reopened())
                .loadEvents("default").size());
    }

    @Test
    public void restoreTo_withDifferentActiveSchedule_switchesActiveSchedule() throws Exception {
        ScheduleRepository source = repositoryWithTwoSchedules();
        ScheduleBackupManager.BackupBundle bundle =
                ScheduleBackupManager.capture(source, "1.2.0");

        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        ScheduleRepository target = new ScheduleRepository(preferences);
        target.setActiveScheduleId("some_other_schedule");

        ScheduleBackupManager.restoreTo(target, bundle);

        assertEquals("default", target.activeScheduleId());
    }

    @Test
    public void backupRestoreController_restoresAndReportsAllCounts() throws Exception {
        ScheduleRepository source = repositoryWithTwoSchedules();
        ScheduleBackupManager.BackupBundle bundle =
                ScheduleBackupManager.capture(source, "1.2.0");
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        ScheduleRepository target = new ScheduleRepository(preferences);

        BackupRestoreController.RestoreResult result =
                new BackupRestoreController(target).restore(bundle);

        assertEquals(2, result.scheduleCount);
        assertEquals(1, result.courseCount);
        assertEquals(1, result.studyPlanCount);
        assertEquals(1, result.academicEventCount);
        assertEquals("default", target.activeScheduleId());
        assertEquals(1, target.loadStructuredCourses("default").size());
        assertEquals(1, new PlanRepository(preferences).loadPlans("default").size());
        assertEquals(1, new AcademicEventRepository(preferences).loadEvents("default").size());
    }

    @Test
    public void restoreTo_clearsStaleScheduleAndRelatedData() throws Exception {
        ScheduleRepository source = repositoryWithTwoSchedules();
        ScheduleBackupManager.BackupBundle bundle =
                ScheduleBackupManager.capture(source, "1.2.0");

        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        ScheduleRepository target = new ScheduleRepository(preferences);
        target.saveSchedules(Collections.singletonList(
                new ScheduleRepository.ScheduleEntry("stale", "旧课表")));
        target.saveStructuredCourses("stale", Collections.singletonList(course(SECOND_ID)));
        new PlanRepository(preferences).savePlans("stale", Collections.singletonList(
                new StudyPlan("stale-plan", "旧计划", "", 1, 0,
                        false, false, StudyPlan.REMIND_DEFAULT_MINUTE, 0L)));
        new AcademicEventRepository(preferences).saveEvents("stale", Collections.singletonList(
                new AcademicEvent("stale-event", "旧事件", "", AcademicEvent.Type.EXAM,
                        1700000000000L, -1, "", "", "", false, 0L)));

        ScheduleBackupManager.restoreTo(target, bundle);

        assertTrue(target.loadStructuredCourses("stale").isEmpty());
        assertTrue(new PlanRepository(preferences).loadPlans("stale").isEmpty());
        assertTrue(new AcademicEventRepository(preferences).loadEvents("stale").isEmpty());
        assertEquals(2, target.loadSchedules().size());
    }

    @Test
    public void restoreTo_commitFailureLeavesExistingStateUntouched() throws Exception {
        ScheduleRepository source = repositoryWithTwoSchedules();
        ScheduleBackupManager.BackupBundle bundle =
                ScheduleBackupManager.capture(source, "1.2.0");

        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        ScheduleRepository target = new ScheduleRepository(preferences);
        target.saveSchedules(Collections.singletonList(
                new ScheduleRepository.ScheduleEntry("target", "本机课表")));
        target.setActiveScheduleId("target");
        target.saveGlobalDarkMode("浅色");
        ScheduleRepository.Config config = new ScheduleRepository.Config();
        config.schoolName = "本机学校";
        target.saveConfig("target", config);
        target.saveStructuredCourses("target", Collections.singletonList(course(SECOND_ID)));
        new PlanRepository(preferences).savePlans("target", Collections.singletonList(
                new StudyPlan("target-plan", "本机计划", "", 1, 0,
                        false, false, StudyPlan.REMIND_DEFAULT_MINUTE, 0L)));
        new AcademicEventRepository(preferences).saveEvents("target", Collections.singletonList(
                new AcademicEvent("target-event", "本机事件", "", AcademicEvent.Type.PRACTICE,
                        1700000000000L, -1, "", "", "", false, 0L)));
        preferences.edit()
                .putString("unrelated_setting", "本机保留")
                .putStringSet("unrelated_set", new HashSet<>(Arrays.asList("a", "b")))
                .commit();
        Map<String, ?> persistedBeforeFailure = preferences.persistedSnapshot();

        preferences.commitResult = false;
        try {
            ScheduleBackupManager.restoreTo(target, bundle);
            fail("Expected restore to report a failed transaction");
        } catch (ScheduleBackupManager.RestoreFailure expected) {
            assertTrue(expected.getMessage().contains("写入"));
            assertTrue(expected.memoryRestored);
            assertTrue(!expected.persistenceConfirmed);
            assertTrue(expected.getMessage().contains("内存状态已恢复"));
            assertTrue(expected.getMessage().contains("磁盘状态未确认"));
        }

        assertEquals("target", target.activeScheduleId());
        assertEquals("浅色", target.loadGlobalDarkMode());
        assertEquals("本机课表", target.loadSchedules().get(0).name);
        assertEquals("本机学校", target.loadConfig("target").schoolName);
        assertStructuredEquals(course(SECOND_ID), target.loadStructuredCourses("target").get(0));
        assertEquals(1, new PlanRepository(preferences).loadPlans("target").size());
        assertEquals(1, new AcademicEventRepository(preferences).loadEvents("target").size());
        assertEquals("本机保留", preferences.getString("unrelated_setting", ""));
        assertEquals(new HashSet<>(Arrays.asList("a", "b")),
                preferences.getStringSet("unrelated_set", Collections.emptySet()));
        assertEquals("持久化快照也应保持失败前状态",
                persistedBeforeFailure, preferences.persistedSnapshot());

        ScheduleRepository restarted = new ScheduleRepository(preferences.reopened());
        assertEquals("target", restarted.activeScheduleId());
        assertEquals("浅色", restarted.loadGlobalDarkMode());
        assertEquals("本机课表", restarted.loadSchedules().get(0).name);
        assertEquals("本机学校", restarted.loadConfig("target").schoolName);
        assertEquals(1, restarted.loadStructuredCourses("target").size());
        assertEquals(1, new PlanRepository(preferences.reopened()).loadPlans("target").size());
        assertEquals(1, new AcademicEventRepository(preferences.reopened())
                .loadEvents("target").size());
    }

    @Test
    public void decode_rejectsFileWithoutHeader() {
        try {
            ScheduleBackupManager.decode(
                    "{\"format\":\"polaris_backup\",\"version\":1}".getBytes(StandardCharsets.UTF_8));
            fail("Expected IllegalArgumentException for missing header");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("不是有效的"));
        }
    }

    @Test
    public void decode_rejectsNewerFormatVersion() throws Exception {
        ScheduleRepository repository = repositoryWithTwoSchedules();
        ScheduleBackupManager.BackupBundle bundle =
                ScheduleBackupManager.capture(repository, "1.2.0");
        byte[] original = ScheduleBackupManager.encode(bundle);
        String json = new String(original, StandardCharsets.UTF_8)
                .replace("\"version\":2", "\"version\":99");

        try {
            ScheduleBackupManager.decode(json.getBytes(StandardCharsets.UTF_8));
            fail("Expected IllegalArgumentException for newer format version");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("升级"));
        }
    }

    @Test
    public void decode_rejectsEmptyScheduleList() {
        String payload = "POLARIS_SCHEDULE_BACKUP_V1\n"
                + "{\"format\":\"polaris_backup\",\"version\":1,\"schedules\":[]}";
        try {
            ScheduleBackupManager.decode(payload.getBytes(StandardCharsets.UTF_8));
            fail("Expected IllegalArgumentException for empty schedules");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("没有课表数据"));
        }
    }

    @Test
    public void decode_v1WithoutNewCollections_remainsCompatible() {
        String payload = "POLARIS_SCHEDULE_BACKUP_V1\n"
                + "{\"format\":\"polaris_backup\",\"version\":1,"
                + "\"activeScheduleId\":\"default\",\"schedules\":["
                + "{\"id\":\"default\",\"name\":\"旧课表\","
                + "\"config\":{},\"structuredCourses\":[]}]}";

        ScheduleBackupManager.BackupBundle bundle = ScheduleBackupManager.decode(
                payload.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, bundle.schedules.size());
        assertEquals("旧课表", bundle.schedules.get(0).name);
        assertTrue(bundle.schedules.get(0).studyPlans.isEmpty());
        assertTrue(bundle.schedules.get(0).academicEvents.isEmpty());
    }

    @Test
    public void decode_rejectsCorruptedJson() {
        String payload = "POLARIS_SCHEDULE_BACKUP_V1\n{broken";
        try {
            ScheduleBackupManager.decode(payload.getBytes(StandardCharsets.UTF_8));
            fail("Expected IllegalArgumentException for corrupted JSON");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("损坏"));
        }
    }

    @Test
    public void decode_rejectsOversizedBytes() {
        byte[] bytes = new byte[ScheduleBackupManager.MAX_BACKUP_BYTES + 1];
        Arrays.fill(bytes, (byte) 'x');
        try {
            ScheduleBackupManager.decode(bytes);
            fail("Expected IllegalArgumentException for oversized backup");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("过大"));
        }
    }

    @Test
    public void restoreTo_rejectsEmptyBundle() {
        ScheduleRepository target = new ScheduleRepository(new InMemorySharedPreferences());
        try {
            ScheduleBackupManager.restoreTo(target, new ScheduleBackupManager.BackupBundle(
                    "2026-01-01 08:00", "1.2.0", "default",
                    Collections.emptyList(), "跟随系统",
                    new ScheduleRepository.AccountProfile()));
            fail("Expected IllegalArgumentException for empty backup bundle");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("没有课表数据"));
        }
    }

    @Test
    public void summary_countsSchedulesAndCourses() throws Exception {
        ScheduleRepository repository = repositoryWithTwoSchedules();
        ScheduleBackupManager.BackupBundle bundle =
                ScheduleBackupManager.capture(repository, "1.2.0");

        ScheduleBackupManager.BackupSummary summary =
                ScheduleBackupManager.summaryOf(bundle);

        assertEquals(2, summary.scheduleCount);
        assertEquals(1, summary.courseCount);
        assertEquals(1, summary.studyPlanCount);
        assertEquals(1, summary.academicEventCount);
        assertEquals(1, summary.imageReferenceCount);
        assertNotNull(summary.createdAt);
        assertEquals("1.2.0", summary.appVersion);
    }

    private ScheduleRepository repositoryWithTwoSchedules() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        ScheduleRepository repository = new ScheduleRepository(preferences);
        List<ScheduleRepository.ScheduleEntry> entries = Arrays.asList(
                new ScheduleRepository.ScheduleEntry("default", "2026春课表"),
                new ScheduleRepository.ScheduleEntry("schedule_1", "备用课表"));
        repository.saveSchedules(entries);
        repository.setActiveScheduleId("default");
        repository.saveGlobalDarkMode("深色");
        ScheduleRepository.AccountProfile profile = new ScheduleRepository.AccountProfile();
        profile.name = "小明";
        profile.avatarUri = "content://avatar/1";
        profile.cropLeft = 0.1f;
        profile.cropTop = 0.2f;
        profile.cropRight = 0.8f;
        profile.cropBottom = 0.9f;
        repository.saveAccountProfile(profile);

        ScheduleRepository.Config config = new ScheduleRepository.Config();
        config.scheduleName = "2026春课表";
        config.schoolName = "西安邮电大学";
        config.semesterWeeks = 20;
        config.visualTheme = "清爽蓝";
        repository.saveConfig("default", config);
        repository.saveConfig("schedule_1", new ScheduleRepository.Config());

        repository.saveStructuredCourses("default",
                Collections.singletonList(course(FIRST_ID)));
        repository.saveStructuredCourses("schedule_1", new ArrayList<StructuredCourse>());

        new PlanRepository(preferences).savePlans("default", Collections.singletonList(
                new StudyPlan("plan-1", "复习信号处理", "数字信号处理", 4, 2,
                        false, true, 8 * 60, 1700000000000L)));
        new AcademicEventRepository(preferences).saveEvents("default", Collections.singletonList(
                new AcademicEvent("event-1", "实验报告截止", "数字信号处理",
                        AcademicEvent.Type.DEADLINE, 1700000000000L, 18 * 60,
                        "实验室", "", "提交电子版", false, 1700000000000L)));
        return repository;
    }

    private StructuredCourse course(String id) {
        List<CourseMeeting> meetings = Arrays.asList(
                new CourseMeeting(
                        "33333333-3333-4333-8333-333333333333",
                        0, 1, 2,
                        new WeekRule(WeekRule.Type.RANGE, 1, 16,
                                Collections.emptyList(), "1-16周"),
                        "A101", "张老师", "raw-1"),
                new CourseMeeting(
                        "44444444-4444-4444-8444-444444444444",
                        3, 5, 6,
                        new WeekRule(WeekRule.Type.EVEN, 2, 16,
                                Collections.emptyList(), "2-16周(双)"),
                        "B202", "李老师", "raw-2"));
        return new StructuredCourse(
                id, "数字信号处理", "默认教师", "默认地点", meetings,
                "raw-course", "3.5", "#4FA4F3", CourseType.EXPERIMENT);
    }

    private void assertStructuredEquals(StructuredCourse expected, StructuredCourse actual) {
        assertEquals(expected.id, actual.id);
        assertEquals(expected.name, actual.name);
        assertEquals(expected.teacher, actual.teacher);
        assertEquals(expected.defaultLocation, actual.defaultLocation);
        assertEquals(expected.rawText, actual.rawText);
        assertEquals(expected.credit, actual.credit);
        assertEquals(expected.color, actual.color);
        assertEquals(expected.courseType, actual.courseType);
        assertEquals(expected.meetings.size(), actual.meetings.size());
        for (int index = 0; index < expected.meetings.size(); index++) {
            CourseMeeting first = expected.meetings.get(index);
            CourseMeeting second = actual.meetings.get(index);
            assertEquals(first.id, second.id);
            assertEquals(first.day, second.day);
            assertEquals(first.startSection, second.startSection);
            assertEquals(first.endSection, second.endSection);
            assertEquals(first.location, second.location);
            assertEquals(first.teacher, second.teacher);
            assertEquals(first.rawText, second.rawText);
            assertEquals(first.weekRule.type, second.weekRule.type);
            assertEquals(first.weekRule.startWeek, second.weekRule.startWeek);
            assertEquals(first.weekRule.endWeek, second.weekRule.endWeek);
            assertEquals(first.weekRule.explicitWeeks, second.weekRule.explicitWeeks);
            assertEquals(first.weekRule.rawText, second.weekRule.rawText);
        }
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private final Map<String, Object> persistedValues = new HashMap<>();
        private boolean commitResult = true;

        @Override
        public Map<String, ?> getAll() {
            return copyValues(values);
        }

        Map<String, ?> persistedSnapshot() {
            return copyValues(persistedValues);
        }

        InMemorySharedPreferences reopened() {
            InMemorySharedPreferences reopened = new InMemorySharedPreferences();
            reopened.values.putAll(copyValues(persistedValues));
            reopened.persistedValues.putAll(copyValues(persistedValues));
            return reopened;
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<String> getStringSet(String key, Set<String> defaultValues) {
            Object value = values.get(key);
            return value instanceof Set ? new HashSet<>((Set<String>) value) : defaultValues;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defaultValue;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new MemoryEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        private final class MemoryEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private final Set<String> removals = new HashSet<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                return put(key, value);
            }

            @Override
            public Editor putStringSet(String key, Set<String> value) {
                return put(key, value == null ? null : new HashSet<>(value));
            }

            @Override
            public Editor putInt(String key, int value) {
                return put(key, value);
            }

            @Override
            public Editor putLong(String key, long value) {
                return put(key, value);
            }

            @Override
            public Editor putFloat(String key, float value) {
                return put(key, value);
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                return put(key, value);
            }

            private Editor put(String key, Object value) {
                if (value == null) {
                    return remove(key);
                }
                pending.put(key, value);
                removals.remove(key);
                return this;
            }

            @Override
            public Editor remove(String key) {
                pending.remove(key);
                removals.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                pending.clear();
                removals.clear();
                return this;
            }

            @Override
            public boolean commit() {
                if (clear) {
                    values.clear();
                }
                for (String key : removals) {
                    values.remove(key);
                }
                values.putAll(pending);
                if (commitResult) {
                    persistedValues.clear();
                    persistedValues.putAll(copyValues(values));
                }
                return commitResult;
            }

            @Override
            public void apply() {
                commit();
            }
        }

        private static Map<String, Object> copyValues(Map<String, Object> source) {
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Set) {
                    Set<?> rawSet = (Set<?>) value;
                    Set<String> setCopy = new HashSet<>();
                    for (Object item : rawSet) {
                        setCopy.add((String) item);
                    }
                    copy.put(entry.getKey(), setCopy);
                } else {
                    copy.put(entry.getKey(), value);
                }
            }
            return copy;
        }
    }
}
