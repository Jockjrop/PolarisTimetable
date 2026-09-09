package com.polaris.timetable.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.polaris.timetable.reminder.CourseReminderScheduler;
import com.polaris.timetable.storage.ScheduleRepository;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 整周课表与今日课表两个 provider 的课程边界闹钟
 * 统一由此安排。闹钟固定投递到 ScheduleWidgetProvider（驱动者），由它在
 * 响应时同步刷新整周 provider，避免两条闹钟链重复唤醒。
 * 小组件开关关闭（全局设置 → 桌面小组件）或两类小组件都不存在时取消闹钟。
 */
final class WidgetRefreshScheduler {
    static final String ACTION_COURSE_TIME_REFRESH =
            "com.polaris.timetable.action.WIDGET_COURSE_TIME_REFRESH";
    private static final int REFRESH_REQUEST_CODE = 4207;

    private WidgetRefreshScheduler() {
    }

    static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class);
        intent.setAction(ACTION_COURSE_TIME_REFRESH);
        return PendingIntent.getBroadcast(
                context,
                REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * 按所有小组件绑定的课表汇总下一个上下课边界并安排闹钟；
     * 无小组件、开关关闭或无未来边界时保持取消状态。
     */
    static void scheduleNext(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent refreshIntent = refreshIntent(context);
        alarmManager.cancel(refreshIntent);

        if (!ScheduleWidgetConfigActivity.widgetEnabled(context)) {
            return;
        }
        int[] ids = unionIds(context);
        if (ids.length == 0) {
            return;
        }

        ScheduleRepository repository = new ScheduleRepository(context);
        // 每个 widget 实例可绑定不同课表；只读取激活课表会让其他实例错过
        // 自己课表的上下课边界。按唯一绑定课表汇总，避免重复加载同一份数据。
        Map<String, ScheduleWidgetRefreshPlanner.ScheduleSource> sources = new LinkedHashMap<>();
        for (int appWidgetId : ids) {
            String scheduleId = ScheduleWidgetConfigActivity.boundScheduleId(context, appWidgetId);
            if (!sources.containsKey(scheduleId)) {
                sources.put(scheduleId, new ScheduleWidgetRefreshPlanner.ScheduleSource(
                        repository.loadCourseView(scheduleId), repository.loadConfig(scheduleId)));
            }
        }

        Calendar now = Calendar.getInstance();
        long nextBoundary = ScheduleWidgetRefreshPlanner.nextBoundaryAfter(sources.values(), now);
        if (nextBoundary > now.getTimeInMillis()) {
            scheduleBoundaryAlarm(context, alarmManager, nextBoundary, refreshIntent);
        }
    }

    static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(refreshIntent(context));
        }
    }

    private static int[] unionIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] smallIds = manager.getAppWidgetIds(
                new ComponentName(context, ScheduleWidgetProvider.class));
        int[] weekIds = manager.getAppWidgetIds(
                new ComponentName(context, WeekScheduleWidgetProvider.class));
        int[] union = new int[smallIds.length + weekIds.length];
        System.arraycopy(smallIds, 0, union, 0, smallIds.length);
        System.arraycopy(weekIds, 0, union, smallIds.length, weekIds.length);
        return union;
    }

    /**
     * 边界闹钟用唤醒型并允许 Doze：设备锁屏睡眠期间课程开始/结束（以及跨天兜底）
     * 也能按时触发，避免解锁后才补刷。精确闹钟权限不可用时降级为非精确调度。
     */
    private static void scheduleBoundaryAlarm(
            Context context,
            AlarmManager alarmManager,
            long triggerAtMillis,
            PendingIntent refreshIntent) {
        if (CourseReminderScheduler.canScheduleExactAlarms(context)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, refreshIntent);
            } catch (SecurityException permissionRevoked) {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, refreshIntent);
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, refreshIntent);
        }
    }
}
