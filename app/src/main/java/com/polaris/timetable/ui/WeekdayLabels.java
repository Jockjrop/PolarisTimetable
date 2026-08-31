package com.polaris.timetable.ui;

import android.content.Context;

/** 周一~周日的完整标签,统一取自字符串资源。 */
public final class WeekdayLabels {
    private static final int[] RES_IDS = {
            com.polaris.timetable.R.string.weekday_mon,
            com.polaris.timetable.R.string.weekday_tue,
            com.polaris.timetable.R.string.weekday_wed,
            com.polaris.timetable.R.string.weekday_thu,
            com.polaris.timetable.R.string.weekday_fri,
            com.polaris.timetable.R.string.weekday_sat,
            com.polaris.timetable.R.string.weekday_sun,
    };

    private WeekdayLabels() {
    }

    /** 合法范围 0~6,越界时就近取边界值。 */
    public static String label(Context context, int day) {
        int index = Math.max(0, Math.min(RES_IDS.length - 1, day));
        return context.getString(RES_IDS[index]);
    }

    public static int count() {
        return RES_IDS.length;
    }
}
