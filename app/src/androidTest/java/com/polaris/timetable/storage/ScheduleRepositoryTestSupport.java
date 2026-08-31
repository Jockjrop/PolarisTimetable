package com.polaris.timetable.storage;

import android.content.Context;

/**
 * 测试数据隔离支持:清空应用 SharedPreferences 持久化状态。
 * PREFS_NAME 与 ScheduleRepository 保持一致,避免测试间数据串扰。
 */
public final class ScheduleRepositoryTestSupport {

    public static final String PREFS_NAME = "polaris_schedule";

    private ScheduleRepositoryTestSupport() {
    }

    public static void clearAll(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }
}
