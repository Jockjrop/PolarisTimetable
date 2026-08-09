package com.polaris.timetable.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.WeekRule;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ScheduleRepositoryCanonicalTest {
    private static final String ID = "11111111-1111-4111-8111-111111111111";

    @Test
    public void structuredSave_thenRepositoryRestart_readsIdenticalCanonicalData() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        ScheduleRepository firstProcess = new ScheduleRepository(preferences);
        List<StructuredCourse> original = Collections.singletonList(course());

        firstProcess.saveStructuredCourses("semester", original);
        ScheduleRepository restartedProcess = new ScheduleRepository(preferences);
        List<StructuredCourse> restored = restartedProcess.loadStructuredCourses("semester");

        assertEquals(1, restored.size());
        assertStructuredEquals(original.get(0), restored.get(0));
        assertEquals(ScheduleStorageSchema.CURRENT_VERSION,
                preferences.getInt("schema_version_semester", -1));
        assertTrue(preferences.contains("structured_courses_semester"));
        assertTrue(preferences.contains("courses_semester"));
    }

    @Test
    public void structuredSave_derivesLegacyCompatibilityJsonWithSameSemantics() throws Exception {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        ScheduleRepository repository = new ScheduleRepository(preferences);
        StructuredCourse original = course();

        repository.saveStructuredCourses("semester", Collections.singletonList(original));
        String legacyJson = preferences.getString("courses_semester", null);
        List<Course> legacy = ScheduleRepository.legacyCoursesFromJson(legacyJson);
        List<StructuredCourse> restored = new CourseStructureMapper().fromLegacyCourses(legacy);
        List<Course> restoredView = new CourseStructureMapper().toLegacyCourses(restored);

        assertNotNull(legacyJson);
        assertEquals(2, legacy.size());
        assertEquals(ID, legacy.get(0).structuredCourseId);
        assertEquals(ID, legacy.get(1).structuredCourseId);
        assertEquals(1, restored.size());
        assertCourseViewsEqual(legacy, restoredView);
    }

    @Test
    public void legacyCourseOnlyData_recoversThroughMigrationAndPersistsCanonicalJson()
            throws Exception {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        String oldJson = "[{\"day\":0,\"startSection\":1,\"endSection\":2,"
                + "\"name\":\"高等数学\",\"weeks\":\"1-16周\","
                + "\"location\":\"A101\",\"teacher\":\"李老师\","
                + "\"raw\":\"raw\",\"credit\":\"5\",\"color\":\"#4FA4F3\","
                + "\"courseType\":\"LECTURE\"}]";
        preferences.edit()
                .putBoolean("courses_ready_legacy", true)
                .putString("courses_legacy", oldJson)
                .apply();

        ScheduleRepository repository = new ScheduleRepository(preferences);
        List<StructuredCourse> restored = repository.loadStructuredCourses("legacy");

        assertEquals(1, restored.size());
        assertEquals("高等数学", restored.get(0).name);
        assertTrue(StableCourseId.isValid(restored.get(0).id));
        assertTrue(preferences.contains("structured_courses_legacy"));
        assertEquals(ScheduleStorageSchema.CURRENT_VERSION,
                preferences.getInt("schema_version_legacy", -1));
    }

    @Test
    public void corruptedStructuredData_recoversFromLegacyCompatibilityJson() throws Exception {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        ScheduleRepository repository = new ScheduleRepository(preferences);
        repository.saveStructuredCourses("semester", Collections.singletonList(course()));
        preferences.edit().putString("structured_courses_semester", "[{broken]").apply();

        List<StructuredCourse> restored = new ScheduleRepository(preferences)
                .loadStructuredCourses("semester");

        assertEquals(1, restored.size());
        assertEquals(ID, restored.get(0).id);
        CourseStructureMapper mapper = new CourseStructureMapper();
        assertCourseViewsEqual(
                mapper.toLegacyCourses(Collections.singletonList(course())),
                mapper.toLegacyCourses(restored));
        assertTrue(preferences.getString("structured_courses_semester", "")
                .contains("\"id\":\"" + ID + "\""));
    }

    private StructuredCourse course() {
        List<CourseMeeting> meetings = Arrays.asList(
                new CourseMeeting(
                        0, 1, 2,
                        new WeekRule(WeekRule.Type.RANGE, 1, 16,
                                Collections.emptyList(), "1-16周"),
                        "A101", "张老师", "raw-1"),
                new CourseMeeting(
                        3, 5, 6,
                        new WeekRule(WeekRule.Type.EVEN, 2, 16,
                                Collections.emptyList(), "2-16周(双)"),
                        "B202", "李老师", "raw-2"));
        return new StructuredCourse(
                ID, "数字信号处理", "默认教师", "默认地点", meetings,
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

    private void assertCourseViewsEqual(List<Course> expected, List<Course> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            Course first = expected.get(index);
            Course second = actual.get(index);
            assertEquals(first.structuredCourseId, second.structuredCourseId);
            assertEquals(first.name, second.name);
            assertEquals(first.day, second.day);
            assertEquals(first.startSection, second.startSection);
            assertEquals(first.endSection, second.endSection);
            assertEquals(first.weeks, second.weeks);
            assertEquals(first.teacher, second.teacher);
            assertEquals(first.location, second.location);
            assertEquals(first.credit, second.credit);
            assertEquals(first.color, second.color);
            assertEquals(first.courseType, second.courseType);
        }
    }

    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(values);
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
                return true;
            }

            @Override
            public void apply() {
                commit();
            }
        }
    }
}
