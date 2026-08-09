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

import com.polaris.timetable.Course;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CourseReminderScheduler {
    public static final String ACTION_COURSE_REMINDER =
            "com.polaris.timetable.action.COURSE_REMINDER";
    public static final String CHANNEL_ID = "course_reminders";
    public static final String EXTRA_COURSE_NAME = "course_name";
    public static final String EXTRA_LOCATION = "location";
    public static final String EXTRA_TIME_TEXT = "time_text";
    public static final String EXTRA_WEEK = "week";
    public static final String EXTRA_MINUTES_BEFORE = "minutes_before";

    private static final String PREFS_NAME = "polaris_course_reminders";
    private static final String KEY_SCHEDULED_URIS = "scheduled_alarm_uris";
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(\\d{4})\\s*[/.-]\\s*(\\d{1,2})\\s*[/.-]\\s*(\\d{1,2})");

    private CourseReminderScheduler() {
    }

    public static int reschedule(Context context) {
        Context appContext = context.getApplicationContext();
        cancelAll(appContext);
        ScheduleRepository repository = new ScheduleRepository(appContext);
        String scheduleId = repository.activeScheduleId();
        ScheduleRepository.Config config = repository.loadConfig(scheduleId);
        if (!config.remindersEnabled || !hasNotificationPermission(appContext)) {
            return 0;
        }
        createNotificationChannel(appContext);
        List<Course> courses = repository.loadCourses(scheduleId);
        CourseTimeResolver.Settings timeSettings = new CourseTimeResolver.Settings(
                config.firstClassStartTime,
                config.classDurationMinutes,
                config.classBreakMinutes,
                config.classBigBreakMinutes,
                config.afternoonStartTime,
                config.lateAfternoonStartTime,
                config.classTimeConfig);
        Calendar now = Calendar.getInstance();
        List<CourseReminderPlanner.Entry> entries = CourseReminderPlanner.plan(
                courses,
                timeSettings,
                firstWeekStartMillis(config.firstWeekDay, now.getTimeZone()),
                config.semesterWeeks,
                config.reminderMinutesBefore,
                now,
                CourseReminderPlanner.DEFAULT_MAX_REMINDERS);
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(
                Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return 0;
        }
        boolean exact = canScheduleExactAlarms(appContext);
        Set<String> scheduledUris = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            CourseReminderPlanner.Entry entry = entries.get(index);
            Uri data = reminderUri(scheduleId, entry, index);
            Intent intent = reminderIntent(appContext, data)
                    .putExtra(EXTRA_COURSE_NAME, safe(entry.course.name))
                    .putExtra(EXTRA_LOCATION, safe(entry.course.location))
                    .putExtra(EXTRA_TIME_TEXT, entry.classTimeText)
                    .putExtra(EXTRA_WEEK, entry.week)
                    .putExtra(EXTRA_MINUTES_BEFORE,
                            Math.max(0, Math.min(180, config.reminderMinutesBefore)));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    data.toString().hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (exact) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, entry.triggerAtMillis, pendingIntent);
                } catch (SecurityException permissionChanged) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, entry.triggerAtMillis, pendingIntent);
                }
            } else {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, entry.triggerAtMillis, pendingIntent);
            }
            scheduledUris.add(data.toString());
        }
        reminderPreferences(appContext).edit()
                .putStringSet(KEY_SCHEDULED_URIS, scheduledUris)
                .apply();
        return entries.size();
    }

    public static void cancelAll(Context context) {
        Context appContext = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(
                Context.ALARM_SERVICE);
        Set<String> stored = reminderPreferences(appContext)
                .getStringSet(KEY_SCHEDULED_URIS, null);
        if (stored != null) {
            for (String value : new HashSet<>(stored)) {
                Uri data = Uri.parse(value);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        appContext,
                        value.hashCode(),
                        reminderIntent(appContext, data),
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pendingIntent != null) {
                    if (alarmManager != null) {
                        alarmManager.cancel(pendingIntent);
                    }
                    pendingIntent.cancel();
                }
            }
        }
        reminderPreferences(appContext).edit().remove(KEY_SCHEDULED_URIS).apply();
    }

    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager manager = (NotificationManager) context.getSystemService(
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

    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return manager != null && manager.canScheduleExactAlarms();
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
                CHANNEL_ID, "课程提醒", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("在课程开始前提醒");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    private static Intent reminderIntent(Context context, Uri data) {
        return new Intent(context, CourseReminderReceiver.class)
                .setAction(ACTION_COURSE_REMINDER)
                .setData(data);
    }

    private static Uri reminderUri(String scheduleId, CourseReminderPlanner.Entry entry,
                                   int index) {
        return new Uri.Builder()
                .scheme("polaris-reminder")
                .authority("course")
                .appendPath(safe(scheduleId))
                .appendPath(String.valueOf(entry.classStartMillis))
                .appendPath(String.valueOf(index))
                .build();
    }

    private static SharedPreferences reminderPreferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static long firstWeekStartMillis(String text, TimeZone timeZone) {
        Calendar first = Calendar.getInstance(timeZone);
        first.clear();
        Matcher matcher = DATE_PATTERN.matcher(safe(text));
        if (matcher.find()) {
            first.set(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)) - 1,
                    Integer.parseInt(matcher.group(3)), 0, 0, 0);
        } else {
            first.setTimeInMillis(System.currentTimeMillis());
            first.set(Calendar.HOUR_OF_DAY, 0);
            first.set(Calendar.MINUTE, 0);
            first.set(Calendar.SECOND, 0);
            first.set(Calendar.MILLISECOND, 0);
        }
        int day = first.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = day == Calendar.SUNDAY ? 6 : day - Calendar.MONDAY;
        first.add(Calendar.DATE, -daysFromMonday);
        return first.getTimeInMillis();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
