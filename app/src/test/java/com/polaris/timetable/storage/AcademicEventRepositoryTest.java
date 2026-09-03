package com.polaris.timetable.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.polaris.timetable.model.AcademicEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AcademicEventRepositoryTest {

    @Test
    public void saveAndLoad_roundTrip_preservesAllFields() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        AcademicEventRepository firstProcess = new AcademicEventRepository(preferences);
        Calendar date = Calendar.getInstance();
        date.set(2026, Calendar.SEPTEMBER, 26, 0, 0, 0);
        date.set(Calendar.MILLISECOND, 0);
        AcademicEvent original = new AcademicEvent(
                "event-1", "高数期中考试", "高等数学", AcademicEvent.Type.EXAM,
                date.getTimeInMillis(), 9 * 60 + 30, "教1-101", "3排5号",
                "带计算器", false, 1700000000000L);

        firstProcess.saveEvents("semester", Arrays.asList(original));
        AcademicEventRepository restarted = new AcademicEventRepository(preferences);
        List<AcademicEvent> restored = restarted.loadEvents("semester");

        assertEquals(1, restored.size());
        AcademicEvent event = restored.get(0);
        assertEquals("event-1", event.id);
        assertEquals("高数期中考试", event.title);
        assertEquals("高等数学", event.courseName);
        assertEquals(AcademicEvent.Type.EXAM, event.type);
        assertEquals(date.getTimeInMillis(), event.dateMillis);
        assertEquals(570, event.minuteOfDay);
        assertEquals("教1-101", event.location);
        assertEquals("3排5号", event.seat);
        assertEquals("带计算器", event.note);
        assertFalse(event.done);
        assertEquals(1700000000000L, event.createdAt);
    }

    @Test
    public void load_missingKey_returnsEmptyList() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        AcademicEventRepository repository = new AcademicEventRepository(preferences);
        assertTrue(repository.loadEvents("semester").isEmpty());
    }

    @Test
    public void load_corruptedJson_returnsEmptyList() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        preferences.edit().putString("academic_events_semester", "{not-json").apply();
        AcademicEventRepository repository = new AcademicEventRepository(preferences);
        assertTrue(repository.loadEvents("semester").isEmpty());
    }

    @Test
    public void load_skipsBrokenEntriesButKeepsValidOnes() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        preferences.edit().putString("academic_events_semester",
                "[{\"id\":\"event-1\",\"title\":\"有效事件\",\"type\":\"EXAM\"},"
                        + "{\"title\":\"缺 id 的条目\"}]").apply();
        AcademicEventRepository repository = new AcademicEventRepository(preferences);
        List<AcademicEvent> events = repository.loadEvents("semester");
        assertEquals(1, events.size());
        assertEquals("有效事件", events.get(0).title);
    }

    @Test
    public void load_unknownType_fallsBackToExam() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        preferences.edit().putString("academic_events_semester",
                "[{\"id\":\"event-1\",\"title\":\"未知类型\",\"type\":\"QUIZ\"}]").apply();
        AcademicEventRepository repository = new AcademicEventRepository(preferences);
        List<AcademicEvent> events = repository.loadEvents("semester");
        assertEquals(1, events.size());
        assertEquals(AcademicEvent.Type.EXAM, events.get(0).type);
    }

    @Test
    public void events_areIsolatedPerScheduleId() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        AcademicEventRepository repository = new AcademicEventRepository(preferences);
        repository.saveEvents("semester", Arrays.asList(event("a", "事件A")));
        repository.saveEvents("other", Arrays.asList(event("b", "事件B")));

        assertEquals(1, repository.loadEvents("semester").size());
        assertEquals("事件B", repository.loadEvents("other").get(0).title);
        assertEquals("事件A", repository.loadEvents("semester").get(0).title);
    }

    @Test
    public void save_emptyList_clearsStoredEvents() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        AcademicEventRepository repository = new AcademicEventRepository(preferences);
        repository.saveEvents("semester", Arrays.asList(event("a", "任务")));
        repository.saveEvents("semester", new ArrayList<AcademicEvent>());
        assertTrue(repository.loadEvents("semester").isEmpty());
    }

    @Test
    public void missingScheduleId_fallsBackToDefaultKey() {
        InMemorySharedPreferences preferences = new InMemorySharedPreferences();
        AcademicEventRepository repository = new AcademicEventRepository(preferences);
        repository.saveEvents(null, Arrays.asList(event("a", "默认课表事件")));
        assertEquals(1, repository.loadEvents("").size());
        assertTrue(preferences.contains("academic_events_default"));
    }

    @Test
    public void withDone_derivesNewInstancePreservingIdentity() {
        AcademicEvent original = event("a", "任务");
        AcademicEvent finished = original.withDone(true);
        assertEquals("a", finished.id);
        assertTrue(finished.done);
        assertFalse(original.done);
    }

    @Test
    public void normalizedDateMillis_zeroesTimeComponents() {
        Calendar date = Calendar.getInstance();
        date.set(2026, Calendar.MARCH, 3, 14, 27, 55);
        date.set(Calendar.MILLISECOND, 333);
        long normalized = AcademicEvent.normalizedDateMillis(date);
        Calendar result = Calendar.getInstance();
        result.setTimeInMillis(normalized);
        assertEquals(2026, result.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, result.get(Calendar.MONTH));
        assertEquals(3, result.get(Calendar.DAY_OF_MONTH));
        assertEquals(0, result.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, result.get(Calendar.MINUTE));
        assertEquals(0, result.get(Calendar.SECOND));
        assertEquals(0, result.get(Calendar.MILLISECOND));
    }

    private static AcademicEvent event(String id, String title) {
        return new AcademicEvent(id, title, "", AcademicEvent.Type.DEADLINE,
                1700000000000L, -1, "", "", "", false, 0L);
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
