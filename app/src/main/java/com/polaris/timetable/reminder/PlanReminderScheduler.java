package com.polaris.timetable.reminder;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.polaris.timetable.model.StudyPlan;
import com.polaris.timetable.storage.PlanRepository;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * 学习计划提醒调度：为每个「未完成且开启提醒」的计划在计划日当天
 * 指定时刻触发一次通知（与课程提醒共用 AlarmManager，独立渠道与 receiver）。
 */
public final class PlanReminderScheduler {
    private static final String TAG = "PlanReminderScheduler";
    public static final String ACTION_PLAN_REMINDER =
            "com.polaris.timetable.action.PLAN_REMINDER";
    public static final String CHANNEL_ID = "plan_reminders";
    public static final String EXTRA_TITLE = "plan_title";
    public static final String EXTRA_WEEK = "plan_week";
    public static final String EXTRA_DAY = "plan_day";

    private static final String PREFS_NAME = "polaris_plan_reminders";
    private static final String KEY_SCHEDULED_URIS = "scheduled_plan_uris";

    private PlanReminderScheduler() {
    }

    /**
     * 计划提醒的独立通知权限判断：系统通知开关 + POST_NOTIFICATIONS（Android 13+）
     * + plan_reminders 渠道非「无通知」。不依赖课程提醒渠道。
     */
    public static boolean hasPermission(Context context) {
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager manager = (NotificationManager) appContext.getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (manager == null || !manager.areNotificationsEnabled()) {
                return manager == null;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
                return channel == null
                        || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
            }
        }
        return true;
    }

    /** 全量重排计划提醒（先取消再按当前计划重新调度），返回已调度数量。 */
    public static int reschedule(Context context) {
        Context appContext = context.getApplicationContext();
        cancelAll(appContext);
        if (!hasPermission(appContext)) {
            Log.d(TAG, "reschedule skipped: no notification permission");
            return 0;
        }
        createNotificationChannel(appContext);
        ScheduleRepository repository = new ScheduleRepository(appContext);
        String scheduleId = repository.activeScheduleId();
        ScheduleRepository.Config config = repository.loadConfig(scheduleId);
        long firstWeekStart = firstWeekStartMillis(config.firstWeekDay);
        List<StudyPlan> plans = new PlanRepository(appContext).loadPlans(scheduleId);
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(
                Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return 0;
        }
        boolean exact = CourseReminderScheduler.canScheduleExactAlarms(appContext);
        long now = System.currentTimeMillis();
        Set<String> scheduledUris = new HashSet<>();
        for (StudyPlan plan : plans) {
            if (!plan.hasReminder()) {
                continue;
            }
            long triggerAtMillis = planTriggerMillis(plan, firstWeekStart);
            if (triggerAtMillis <= now) {
                continue;
            }
            Uri data = planUri(scheduleId, plan);
            Intent intent = planIntent(appContext, data)
                    .putExtra(EXTRA_TITLE, safe(plan.title))
                    .putExtra(EXTRA_WEEK, plan.week)
                    .putExtra(EXTRA_DAY, plan.dayOfWeek);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    data.toString().hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (exact) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } catch (SecurityException permissionChanged) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
            scheduledUris.add(data.toString());
        }
        planPreferences(appContext).edit()
                .putStringSet(KEY_SCHEDULED_URIS, scheduledUris)
                .apply();
        Log.d(TAG, "reschedule: " + plans.size() + " plans, "
                + scheduledUris.size() + " alarms scheduled");
        return scheduledUris.size();
    }

    public static void cancelAll(Context context) {
        Context appContext = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(
                Context.ALARM_SERVICE);
        Set<String> stored = planPreferences(appContext)
                .getStringSet(KEY_SCHEDULED_URIS, null);
        if (stored != null) {
            for (String value : new HashSet<>(stored)) {
                Uri data = Uri.parse(value);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        value.hashCode(),
                        planIntent(appContext, data),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pendingIntent != null) {
                    if (alarmManager != null) {
                        alarmManager.cancel(pendingIntent);
                    }
                    pendingIntent.cancel();
                }
            }
        }
        planPreferences(appContext).edit().remove(KEY_SCHEDULED_URIS).apply();
    }

    /**
     * 计划提醒触发时刻：学期第一周周一 00:00 + (week-1)×7 + dayOfWeek 天，
     * 再设为 remindMinute 时刻（本地时区）。
     */
    public static long planTriggerMillis(StudyPlan plan, long firstWeekStartMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(firstWeekStartMillis);
        calendar.add(Calendar.DATE, (plan.week - 1) * 7 + plan.dayOfWeek);
        calendar.set(Calendar.HOUR_OF_DAY, plan.remindMinute / 60);
        calendar.set(Calendar.MINUTE, plan.remindMinute % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    /**
     * 学期第一周周一 00:00（本地时区）。统一委托 {@link CourseTimeResolver}，
     * 与课表 UI、课程提醒共用同一解析与回退规则。
     */
    public static long firstWeekStartMillis(String firstWeekDayText) {
        return CourseTimeResolver.firstWeekStartMillis(firstWeekDayText);
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "计划提醒", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("在计划到期当天提醒学习任务");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    private static Intent planIntent(Context context, Uri data) {
        return new Intent(context, PlanReminderReceiver.class)
                .setAction(ACTION_PLAN_REMINDER)
                .setData(data);
    }

    private static Uri planUri(String scheduleId, StudyPlan plan) {
        return new Uri.Builder()
                .scheme("polaris-plan")
                .authority("plan")
                .appendPath(safe(scheduleId))
                .appendPath(plan.id)
                .build();
    }

    private static SharedPreferences planPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** 供测试校验的时区无关计算入口（与 planTriggerMillis 相同，但指定时区）。 */
    static long planTriggerMillisInTimeZone(StudyPlan plan, long firstWeekStartMillis,
                                            TimeZone timeZone) {
        Calendar calendar = Calendar.getInstance(timeZone);
        calendar.setTimeInMillis(firstWeekStartMillis);
        calendar.add(Calendar.DATE, (plan.week - 1) * 7 + plan.dayOfWeek);
        calendar.set(Calendar.HOUR_OF_DAY, plan.remindMinute / 60);
        calendar.set(Calendar.MINUTE, plan.remindMinute % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
