package com.polaris.timetable.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;
import android.widget.RemoteViews;

import com.polaris.timetable.MainActivity;
import com.polaris.timetable.R;
import com.polaris.timetable.storage.ScheduleRepository;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScheduleWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_SCHEDULE_CHANGED =
            "com.polaris.timetable.action.SCHEDULE_CHANGED";
    private static final String ACTION_COURSE_TIME_REFRESH =
            "com.polaris.timetable.action.WIDGET_COURSE_TIME_REFRESH";
    private static final int COURSE_TIME_REFRESH_REQUEST_CODE = 4207;
    static final String EXTRA_DAY_OFFSET = "day_offset";
    private static final int WIDE_WIDGET_MIN_WIDTH_DP = 230;
    private static final String[] DAY_NAMES = {
            "周日", "周一", "周二", "周三", "周四", "周五", "周六"
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, manager, appWidgetId);
        }
        scheduleNextCourseTimeRefresh(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        updateWidget(context, manager, appWidgetId);
        scheduleNextCourseTimeRefresh(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_SCHEDULE_CHANGED.equals(action)
                || ACTION_COURSE_TIME_REFRESH.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_CONFIGURATION_CHANGED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            updateAll(context);
        }
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        for (int id : ids) {
            updateWidget(context, manager, id);
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_today_list);
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_tomorrow_list);
        }
        if (ids.length > 0) {
            scheduleNextCourseTimeRefresh(context);
        } else {
            cancelCourseTimeRefresh(context);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        cancelCourseTimeRefresh(context);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Map<SizeF, RemoteViews> responsiveViews = new LinkedHashMap<>();
            responsiveViews.put(new SizeF(110f, 110f), buildViews(context, appWidgetId, false));
            responsiveViews.put(new SizeF(230f, 110f), buildViews(context, appWidgetId, true));
            manager.updateAppWidget(appWidgetId, new RemoteViews(responsiveViews));
            return;
        }
        Bundle options = manager.getAppWidgetOptions(appWidgetId);
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        boolean wide = minWidth >= WIDE_WIDGET_MIN_WIDTH_DP;
        manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId, wide));
    }

    private static RemoteViews buildViews(Context context, int appWidgetId, boolean wide) {
        int layout = wide ? R.layout.widget_schedule_large : R.layout.widget_schedule_small;
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        PendingIntent openApp = openAppIntent(context, appWidgetId);
        views.setOnClickPendingIntent(R.id.widget_root, openApp);

        Calendar today = Calendar.getInstance();
        views.setTextViewText(R.id.widget_today_title, dateTitle("今天", today));
        configureList(context, views, R.id.widget_today_list, R.id.widget_today_empty,
                appWidgetId, 0, openApp);
        if (wide) {
            Calendar tomorrow = (Calendar) today.clone();
            tomorrow.add(Calendar.DATE, 1);
            views.setTextViewText(R.id.widget_tomorrow_title, dateTitle("明天", tomorrow));
            configureList(context, views, R.id.widget_tomorrow_list, R.id.widget_tomorrow_empty,
                    appWidgetId, 1, openApp);
        }
        return views;
    }

    private static void configureList(
            Context context,
            RemoteViews views,
            int listId,
            int emptyId,
            int appWidgetId,
            int dayOffset,
            PendingIntent openApp) {
        Intent serviceIntent = new Intent(context, ScheduleWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.putExtra(EXTRA_DAY_OFFSET, dayOffset);
        serviceIntent.setData(Uri.parse("polaris://widget/" + appWidgetId + "/" + dayOffset));
        views.setRemoteAdapter(listId, serviceIntent);
        views.setEmptyView(listId, emptyId);
        views.setPendingIntentTemplate(listId, openApp);
    }

    private static PendingIntent openAppIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getActivity(context, appWidgetId, intent, flags);
    }

    private static String dateTitle(String prefix, Calendar date) {
        int dayOfWeek = date.get(Calendar.DAY_OF_WEEK);
        String dayName = dayOfWeek >= Calendar.SUNDAY && dayOfWeek <= Calendar.SATURDAY
                ? DAY_NAMES[dayOfWeek - 1] : "";
        return prefix + "·" + dayName + " "
                + (date.get(Calendar.MONTH) + 1) + "/" + date.get(Calendar.DAY_OF_MONTH);
    }

    private static void scheduleNextCourseTimeRefresh(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        PendingIntent refreshIntent = courseTimeRefreshIntent(context);
        alarmManager.cancel(refreshIntent);

        ScheduleRepository repository = new ScheduleRepository(context);
        String scheduleId = repository.activeScheduleId();
        long nextEnd = ScheduleWidgetData.nextCourseEndAfter(
                repository.loadCourseView(scheduleId),
                repository.loadConfig(scheduleId),
                Calendar.getInstance());
        if (nextEnd > System.currentTimeMillis()) {
            alarmManager.setWindow(AlarmManager.RTC, nextEnd, 60_000L, refreshIntent);
        }
    }

    private static void cancelCourseTimeRefresh(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(courseTimeRefreshIntent(context));
        }
    }

    private static PendingIntent courseTimeRefreshIntent(Context context) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class);
        intent.setAction(ACTION_COURSE_TIME_REFRESH);
        return PendingIntent.getBroadcast(
                context,
                COURSE_TIME_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
