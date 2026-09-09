package com.polaris.timetable.widget;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.polaris.timetable.R;
import com.polaris.timetable.storage.ScheduleRepository;

import java.util.List;

/**
 * 桌面小组件的课表绑定配置页：每个 widget 实例可独立选择数据来源课表，
 * 缺省跟随当前激活课表。由系统 APPWIDGET_CONFIGURE 流程调起（添加时与
 * 长按重配置），结果按 widgetId 存入 polaris_widget_config 偏好。
 */
public class ScheduleWidgetConfigActivity extends Activity {

    public static final String PREFS_NAME = "polaris_widget_config";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_DAY_COUNT_PREFIX = "days_";
    /** 整周小组件默认展示周一到周五；6/7 天由用户在配置页追加。 */
    public static final int DEFAULT_DAY_COUNT = 5;

    /**
     * 桌面小组件总开关（全局设置）。小组件的添加与移除始终由用户在桌面完成，
     * 开关只控制已放置小组件的渲染与刷新：关闭后显示「已关闭」占位。
     */
    public static boolean widgetEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true);
    }

    public static void setWidgetEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** 整周小组件展示的天数（5/6/7），按 widgetId 独立存储。 */
    public static int widgetDayCount(Context context, int appWidgetId) {
        int days = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_DAY_COUNT_PREFIX + appWidgetId, DEFAULT_DAY_COUNT);
        return days >= 5 && days <= 7 ? days : DEFAULT_DAY_COUNT;
    }

    public static void setWidgetDayCount(Context context, int appWidgetId, int dayCount) {
        int normalized = Math.max(5, Math.min(7, dayCount));
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_DAY_COUNT_PREFIX + appWidgetId, normalized).apply();
    }

    /** 读取某 widget 绑定的课表 id；未配置或指向已删除课表时回退激活课表。 */
    public static String boundScheduleId(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String bound = prefs.getString(scheduleKey(appWidgetId), "");
        ScheduleRepository repository = new ScheduleRepository(context);
        for (ScheduleRepository.ScheduleEntry entry : repository.loadSchedules()) {
            if (entry.id.equals(bound)) {
                return bound;
            }
        }
        return repository.activeScheduleId();
    }

    public static String scheduleKey(int appWidgetId) {
        return "schedule_" + appWidgetId;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int appWidgetId = getIntent().getIntExtra(
                android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        int ink = Color.parseColor("#172033");
        int muted = Color.parseColor("#667085");
        int card = Color.WHITE;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F6F7FB"));
        int pad = dp(20);
        root.setPadding(pad, dp(28), pad, pad);

        TextView title = new TextView(this);
        title.setText(getString(R.string.widget_config_title));
        title.setTextColor(ink);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(getString(R.string.widget_config_hint));
        hint.setTextColor(muted);
        hint.setTextSize(13);
        hint.setPadding(0, dp(6), 0, dp(14));
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (isWeekWidget(appWidgetId)) {
            list.addView(dayCountSection(ink, muted, card, appWidgetId));
        }

        ScheduleRepository repository = new ScheduleRepository(this);
        List<ScheduleRepository.ScheduleEntry> schedules = repository.loadSchedules();
        String activeId = repository.activeScheduleId();
        for (ScheduleRepository.ScheduleEntry entry : schedules) {
            list.addView(row(entry, entry.id.equals(activeId), ink, muted, card, () -> {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(scheduleKey(appWidgetId), entry.id).apply();
                ScheduleWidgetProvider.updateAll(this);
                WeekScheduleWidgetProvider.updateAll(this);
                setResult(RESULT_OK, new android.content.Intent().putExtra(
                        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));
                finish();
            }));
        }

        setContentView(root);
    }

    /** 当前配置的是否为整周课表小组件（决定配置页是否展示天数选择）。 */
    private boolean isWeekWidget(int appWidgetId) {
        android.appwidget.AppWidgetProviderInfo info =
                android.appwidget.AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId);
        return info != null && WeekScheduleWidgetProvider.class.getName()
                .equals(info.provider.getClassName());
    }

    /** 整周小组件的天数选择：5/6/7 三档，点选即保存并刷新小组件，不结束配置流程。 */
    private View dayCountSection(int ink, int muted, int card, int appWidgetId) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = dp(4);
        sectionParams.bottomMargin = dp(8);
        section.setLayoutParams(sectionParams);

        TextView label = new TextView(this);
        label.setText(getString(R.string.widget_config_days_label));
        label.setTextColor(ink);
        label.setTextSize(15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        section.addView(label);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        int current = widgetDayCount(this, appWidgetId);
        for (int days = 5; days <= 7; days++) {
            final int value = days;
            TextView chip = new TextView(this);
            chip.setText(getString(R.string.widget_config_days_value, days));
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(6), dp(14), dp(6));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipParams.topMargin = dp(8);
            chipParams.rightMargin = dp(8);
            chip.setLayoutParams(chipParams);
            android.graphics.drawable.GradientDrawable chipBg = new android.graphics.drawable.GradientDrawable();
            chipBg.setCornerRadius(dp(14));
            boolean selected = value == current;
            chipBg.setColor(selected ? Color.parseColor("#2563EB") : card);
            chip.setBackground(chipBg);
            chip.setTextColor(selected ? Color.WHITE : muted);
            chip.setOnClickListener(v -> {
                setWidgetDayCount(this, appWidgetId, value);
                ScheduleWidgetProvider.updateAll(this);
                WeekScheduleWidgetProvider.updateAll(this);
                // 简单重建选中态：重新 onCreate 代价小且逻辑收敛。
                recreate();
            });
            chips.addView(chip);
        }
        section.addView(chips);
        return section;
    }

    private View row(ScheduleRepository.ScheduleEntry entry, boolean active,
                     int ink, int muted, int card, Runnable onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        row.setLayoutParams(params);

        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(card);
        background.setCornerRadius(dp(12));
        row.setBackground(background);
        row.setElevation(dp(1));

        TextView name = new TextView(this);
        name.setText(entry.name);
        name.setTextColor(ink);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(name);

        TextView badge = new TextView(this);
        badge.setText(active
                ? getString(R.string.widget_config_active_badge)
                : getString(R.string.widget_config_bind_action));
        badge.setTextColor(active ? muted : Color.parseColor("#2563EB"));
        badge.setTextSize(12);
        badge.setPadding(0, dp(3), 0, 0);
        row.addView(badge);

        row.setOnClickListener(v -> onPick.run());
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
