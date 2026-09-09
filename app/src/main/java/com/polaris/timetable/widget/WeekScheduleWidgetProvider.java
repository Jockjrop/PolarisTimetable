package com.polaris.timetable.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import com.polaris.timetable.R;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.Calendar;

/**
 * 整周课表小组件：按节次行 × 天列的网格展示完整一周课程（课程表同款布局），
 * 远程列表支持在小组件内上下滑动查看全部节次。展示天数（5/6/7）由配置页
 * 按 widgetId 独立选择；数据源课表与今日课表小组件共用绑定偏好。
 * 刷新闹钟统一由 {@link WidgetRefreshScheduler} 安排，边界闹钟由
 * ScheduleWidgetProvider 驱动并同步刷新本形态。
 */
public final class WeekScheduleWidgetProvider extends AppWidgetProvider {
    static final String EXTRA_FORM = "form";
    static final String FORM_WEEK = "week";
    static final String EXTRA_DAY_COUNT = "day_count";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, manager, appWidgetId);
        }
        WidgetRefreshScheduler.scheduleNext(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context, AppWidgetManager manager, int appWidgetId, Bundle newOptions) {
        updateWidget(context, manager, appWidgetId);
        WidgetRefreshScheduler.scheduleNext(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? "" : intent.getAction();
        if (ScheduleWidgetProvider.ACTION_SCHEDULE_CHANGED.equals(action)
                || WidgetRefreshScheduler.ACTION_COURSE_TIME_REFRESH.equals(action)
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
        ComponentName provider = new ComponentName(context, WeekScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        for (int id : ids) {
            updateWidget(context, manager, id);
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_week_list);
        }
        WidgetRefreshScheduler.scheduleNext(context);
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        // 本形态最后一个实例被移除时，若今日小组件仍在则保留闹钟链。
        WidgetRefreshScheduler.scheduleNext(context);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId));
    }

    private static RemoteViews buildViews(Context context, int appWidgetId) {
        if (!ScheduleWidgetConfigActivity.widgetEnabled(context)) {
            // 开关关闭（全局设置 → 桌面小组件）：渲染「已关闭」占位，不挂远程列表；
            // 应用无法从桌面移除小组件，占位文案引导用户长按手动移除。
            return new RemoteViews(context.getPackageName(), R.layout.widget_disabled);
        }
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_week);
        PendingIntent openApp = ScheduleWidgetProvider.openAppIntent(context, appWidgetId);
        views.setOnClickPendingIntent(R.id.widget_week_root, openApp);

        int dayCount = ScheduleWidgetConfigActivity.widgetDayCount(context, appWidgetId);
        Calendar today = Calendar.getInstance();
        int todayColumn = CourseTimeResolver.mondayBasedDay(today);
        Calendar[] weekDates = ScheduleWidgetWeekData.weekDates(today);
        views.setTextViewText(R.id.widget_week_month,
                (weekDates[0].get(Calendar.MONTH) + 1) + "月");
        for (int column = 0; column < ScheduleWidgetWeekData.DAY_NAMES.length; column++) {
            int titleId = dayTitleId(column);
            boolean visible = column < dayCount;
            views.setViewVisibility(titleId, visible ? View.VISIBLE : View.GONE);
            if (!visible) {
                continue;
            }
            views.setTextViewText(titleId, ScheduleWidgetWeekData.DAY_CHARS[column] + "\n"
                    + weekDates[column].get(Calendar.DAY_OF_MONTH));
            views.setTextColor(titleId, column == todayColumn
                    ? Color.parseColor("#2563EB")
                    : context.getColor(R.color.widget_text_muted));
        }
        views.setTextViewText(R.id.widget_week_date, bigDateTitle(today));
        views.setTextViewText(R.id.widget_week_info, infoTitle(context, appWidgetId, today));

        Intent serviceIntent = new Intent(context, ScheduleWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.putExtra(WeekScheduleWidgetProvider.EXTRA_FORM, FORM_WEEK);
        serviceIntent.putExtra(EXTRA_DAY_COUNT, dayCount);
        serviceIntent.setData(Uri.parse("polaris://widget/week/" + appWidgetId));
        views.setRemoteAdapter(R.id.widget_week_list, serviceIntent);
        views.setEmptyView(R.id.widget_week_list, R.id.widget_week_empty);
        views.setPendingIntentTemplate(R.id.widget_week_list, openApp);
        return views;
    }

    /** 大号日期行：如「2026/9/9」。 */
    private static String bigDateTitle(Calendar today) {
        return today.get(Calendar.YEAR) + "/" + (today.get(Calendar.MONTH) + 1)
                + "/" + today.get(Calendar.DAY_OF_MONTH);
    }

    /** 信息行：如「大二上 | 第4周 周日」；假期外仅「课表名 | 周日」，无课表名则省略前缀。 */
    private static String infoTitle(Context context, int appWidgetId, Calendar today) {
        ScheduleRepository repository = new ScheduleRepository(context);
        ScheduleRepository.Config config =
                repository.loadConfig(ScheduleWidgetConfigActivity.boundScheduleId(
                        context, appWidgetId));
        int week = ScheduleWidgetData.weekForDate(config.firstWeekDay, today);
        String day = ScheduleWidgetWeekData.DAY_NAMES[CourseTimeResolver.mondayBasedDay(today)];
        String weekPart = week >= 1 && week <= Math.max(1, config.semesterWeeks)
                ? "第" + week + "周 " + day
                : day;
        String name = boundScheduleName(context, appWidgetId);
        return name.length() == 0 ? weekPart : name + " | " + weekPart;
    }

    /** 绑定课表名（对照 WakeUp 信息行始终带课表名；找不到时返回空串）。 */
    private static String boundScheduleName(Context context, int appWidgetId) {
        String boundId = ScheduleWidgetConfigActivity.boundScheduleId(context, appWidgetId);
        for (ScheduleRepository.ScheduleEntry entry : new ScheduleRepository(context).loadSchedules()) {
            if (entry.id.equals(boundId)) {
                return entry.name == null ? "" : entry.name.trim();
            }
        }
        return "";
    }

    private static int dayTitleId(int column) {
        switch (column) {
            case 0: return R.id.widget_week_day_0;
            case 1: return R.id.widget_week_day_1;
            case 2: return R.id.widget_week_day_2;
            case 3: return R.id.widget_week_day_3;
            case 4: return R.id.widget_week_day_4;
            case 5: return R.id.widget_week_day_5;
            default: return R.id.widget_week_day_6;
        }
    }
}
