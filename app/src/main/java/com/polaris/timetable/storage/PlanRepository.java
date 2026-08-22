package com.polaris.timetable.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.polaris.timetable.model.StudyPlan;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 学习计划存储：与课表共用 SharedPreferences 文件，按 scheduleId 隔离。
 * 纯新增数据，无迁移负担；损坏数据按空列表容错。
 */
public class PlanRepository {
    private static final String TAG = "PlanRepository";
    private static final String PREFS_NAME = "polaris_schedule";
    private static final String KEY_PLANS_PREFIX = "plans_";

    private final Context appContext;
    private final SharedPreferences preferences;

    public PlanRepository(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    PlanRepository(SharedPreferences preferences) {
        appContext = null;
        this.preferences = preferences;
    }

    public List<StudyPlan> loadPlans(String scheduleId) {
        String json = preferences.getString(planKey(scheduleId), null);
        if (json == null || json.length() == 0) {
            return new ArrayList<>();
        }
        List<StudyPlan> plans = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                StudyPlan plan = fromJson(array.optJSONObject(i));
                if (plan != null) {
                    plans.add(plan);
                }
            }
        } catch (JSONException exception) {
            Log.w(TAG, "计划数据解析失败，按空列表处理", exception);
        }
        return plans;
    }

    public void savePlans(String scheduleId, List<StudyPlan> plans) {
        JSONArray array = new JSONArray();
        if (plans != null) {
            for (StudyPlan plan : plans) {
                if (plan != null) {
                    array.put(toJson(plan));
                }
            }
        }
        preferences.edit()
                .putString(planKey(scheduleId), array.toString())
                .apply();
    }

    private String planKey(String scheduleId) {
        String safeId = scheduleId == null || scheduleId.trim().isEmpty()
                ? "default" : scheduleId.trim();
        return KEY_PLANS_PREFIX + safeId;
    }

    private JSONObject toJson(StudyPlan plan) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", plan.id);
            object.put("title", plan.title);
            object.put("courseName", plan.courseName);
            object.put("week", plan.week);
            object.put("dayOfWeek", plan.dayOfWeek);
            object.put("done", plan.done);
            object.put("remindEnabled", plan.remindEnabled);
            object.put("remindMinute", plan.remindMinute);
            object.put("createdAt", plan.createdAt);
        } catch (JSONException ignored) {
            // 字段均为可序列化基础类型，不会失败。
        }
        return object;
    }

    private StudyPlan fromJson(JSONObject object) {
        if (object == null) {
            return null;
        }
        try {
            StudyPlan plan = new StudyPlan(
                    object.optString("id", ""),
                    object.optString("title", ""),
                    object.optString("courseName", ""),
                    object.optInt("week", 1),
                    object.optInt("dayOfWeek", 0),
                    object.optBoolean("done", false),
                    object.optBoolean("remindEnabled", true),
                    object.optInt("remindMinute", StudyPlan.REMIND_DEFAULT_MINUTE),
                    object.optLong("createdAt", 0L));
            return plan.id.length() == 0 ? null : plan;
        } catch (Exception exception) {
            Log.w(TAG, "跳过损坏的计划条目", exception);
            return null;
        }
    }
}
