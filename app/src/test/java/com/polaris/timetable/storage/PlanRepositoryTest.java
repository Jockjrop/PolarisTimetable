package com.polaris.timetable.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.polaris.timetable.model.StudyPlan;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlanRepositoryTest {

    @Test
    public void saveAndLoad_roundTrip_preservesAllFields() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        PlanRepository firstProcess = new PlanRepository(preferences);
        StudyPlan original = new StudyPlan(
                "plan-1", "预习高等数学第3章", "高等数学", 3, 4,
                false, true, 8 * 60 + 30, 1700000000000L);

        firstProcess.savePlans("semester", Arrays.asList(original));
        PlanRepository restarted = new PlanRepository(preferences);
        List<StudyPlan> restored = restarted.loadPlans("semester");

        assertEquals(1, restored.size());
        StudyPlan plan = restored.get(0);
        assertEquals("plan-1", plan.id);
        assertEquals("预习高等数学第3章", plan.title);
        assertEquals("高等数学", plan.courseName);
        assertEquals(3, plan.week);
        assertEquals(4, plan.dayOfWeek);
        assertFalse(plan.done);
        assertTrue(plan.remindEnabled);
        assertEquals(510, plan.remindMinute);
        assertEquals(1700000000000L, plan.createdAt);
    }

    @Test
    public void load_missingKey_returnsEmptyList() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        PlanRepository repository = new PlanRepository(preferences);
        assertTrue(repository.loadPlans("semester").isEmpty());
    }

    @Test
    public void load_corruptedJson_returnsEmptyList() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        preferences.edit().putString("plans_semester", "{not-json").apply();
        PlanRepository repository = new PlanRepository(preferences);
        assertTrue(repository.loadPlans("semester").isEmpty());
    }

    @Test
    public void load_skipsBrokenEntriesButKeepsValidOnes() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        preferences.edit().putString("plans_semester",
                "[{\"id\":\"plan-1\",\"title\":\"有效计划\",\"week\":2},"
                        + "{\"title\":\"缺 id 的条目\"}]").apply();
        PlanRepository repository = new PlanRepository(preferences);
        List<StudyPlan> plans = repository.loadPlans("semester");
        assertEquals(1, plans.size());
        assertEquals("有效计划", plans.get(0).title);
    }

    @Test
    public void plans_areIsolatedPerScheduleId() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        PlanRepository repository = new PlanRepository(preferences);
        repository.savePlans("semester", Arrays.asList(plan("a", "学期A计划")));
        repository.savePlans("other", Arrays.asList(plan("b", "学期B计划")));

        assertEquals(1, repository.loadPlans("semester").size());
        assertEquals("学期B计划", repository.loadPlans("other").get(0).title);
        assertEquals("学期A计划", repository.loadPlans("semester").get(0).title);
    }

    @Test
    public void save_emptyList_clearsStoredPlans() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        PlanRepository repository = new PlanRepository(preferences);
        repository.savePlans("semester", Arrays.asList(plan("a", "任务")));
        repository.savePlans("semester", new ArrayList<StudyPlan>());
        assertTrue(repository.loadPlans("semester").isEmpty());
    }

    @Test
    public void missingScheduleId_fallsBackToDefaultKey() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        PlanRepository repository = new PlanRepository(preferences);
        repository.savePlans(null, Arrays.asList(plan("a", "默认课表计划")));
        assertEquals(1, repository.loadPlans("").size());
        assertTrue(preferences.contains("plans_default"));
    }

    @Test
    public void withDone_derivesNewInstancePreservingIdentity() {
        StudyPlan original = plan("a", "任务");
        StudyPlan finished = original.withDone(true);
        assertEquals("a", finished.id);
        assertTrue(finished.done);
        assertFalse(original.done);
    }

    private static StudyPlan plan(String id, String title) {
        return new StudyPlan(id, title, "", 1, 0, false, true,
                StudyPlan.REMIND_DEFAULT_MINUTE, 0L);
    }

    /** 内存版 SharedPreferences：仅用于单测，生产代码不引用。 */
    private static final class InMemorySharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return new HashMap<>(values);
        }

        @Override
        public String getString(String key, String defValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                @Override
                public Editor putString(String key, String value) {
                    values.put(key, value);
                    return this;
                }

                @Override
                public Editor putStringSet(String key, Set<String> values) {
                    return this;
                }

                @Override
                public Editor putInt(String key, int value) {
                    values.put(key, value);
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    values.put(key, value);
                    return this;
                }

                @Override
                public Editor putFloat(String key, float value) {
                    values.put(key, value);
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    values.put(key, value);
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    values.remove(key);
                    return this;
                }

                @Override
                public Editor clear() {
                    values.clear();
                    return this;
                }

                @Override
                public boolean commit() {
                    return true;
                }

                @Override
                public void apply() {
                }
            };
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
        }
    }
}
