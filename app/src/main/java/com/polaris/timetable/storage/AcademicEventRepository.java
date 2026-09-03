package com.polaris.timetable.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.polaris.timetable.model.AcademicEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 学业事件存储：与计划/课表共用 SharedPreferences 文件，按 scheduleId 隔离。
 * 纯新增数据，无迁移负担；损坏数据按空列表容错（与 PlanRepository 同策略）。
 */
public class AcademicEventRepository {
    private static final String TAG = "AcademicEventRepo";
    private static final String PREFS_NAME = "polaris_schedule";
    private static final String KEY_EVENTS_PREFIX = "academic_events_";

    private final Context appContext;
    private final SharedPreferences preferences;

    public AcademicEventRepository(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    AcademicEventRepository(SharedPreferences preferences) {
        appContext = null;
        this.preferences = preferences;
    }

    public List<AcademicEvent> loadEvents(String scheduleId) {
        String json = preferences.getString(eventsKey(scheduleId), null);
        if (json == null || json.length() == 0) {
            return new ArrayList<>();
        }
        List<AcademicEvent> events = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                AcademicEvent event = fromJson(array.optJSONObject(i));
                if (event != null) {
                    events.add(event);
                }
            }
        } catch (JSONException exception) {
            Log.w(TAG, "事件数据解析失败，按空列表处理", exception);
        }
        return events;
    }

    public void saveEvents(String scheduleId, List<AcademicEvent> events) {
        JSONArray array = new JSONArray();
        if (events != null) {
            for (AcademicEvent event : events) {
                if (event != null) {
                    array.put(toJson(event));
                }
            }
        }
        preferences.edit()
                .putString(eventsKey(scheduleId), array.toString())
                .apply();
    }

    private String eventsKey(String scheduleId) {
        String safeId = scheduleId == null || scheduleId.trim().isEmpty()
                ? "default" : scheduleId.trim();
        return KEY_EVENTS_PREFIX + safeId;
    }

    private JSONObject toJson(AcademicEvent event) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", event.id);
            object.put("title", event.title);
            object.put("courseName", event.courseName);
            object.put("type", event.type.name());
            object.put("dateMillis", event.dateMillis);
            object.put("minuteOfDay", event.minuteOfDay);
            object.put("location", event.location);
            object.put("seat", event.seat);
            object.put("note", event.note);
            object.put("done", event.done);
            object.put("createdAt", event.createdAt);
        } catch (JSONException ignored) {
            // 字段均为可序列化基础类型，不会失败。
        }
        return object;
    }

    private AcademicEvent fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        try {
            AcademicEvent.Type type = parseType(object.optString("type", ""));
            AcademicEvent event = new AcademicEvent(
                    object.optString("id", ""),
                    object.optString("title", ""),
                    object.optString("courseName", ""),
                    type,
                    object.optLong("dateMillis", 0L),
                    object.optInt("minuteOfDay", -1),
                    object.optString("location", ""),
                    object.optString("seat", ""),
                    object.optString("note", ""),
                    object.optBoolean("done", false),
                    object.optLong("createdAt", 0L));
            return event.id.length() == 0 ? null : event;
        } catch (Exception exception) {
            Log.w(TAG, "跳过损坏的事件条目", exception);
            return null;
        }
    }

    private static AcademicEvent.Type parseType(String raw) {
        for (AcademicEvent.Type type : AcademicEvent.Type.values()) {
            if (type.name().equals(raw)) {
                return type;
            }
        }
        return AcademicEvent.Type.EXAM;
    }
}
