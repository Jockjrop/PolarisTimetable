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

        ScheduleRepository repository = new ScheduleRepository(this);
        List<ScheduleRepository.ScheduleEntry> schedules = repository.loadSchedules();
        String activeId = repository.activeScheduleId();
        for (ScheduleRepository.ScheduleEntry entry : schedules) {
            list.addView(row(entry, entry.id.equals(activeId), ink, muted, card, () -> {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(scheduleKey(appWidgetId), entry.id).apply();
                ScheduleWidgetProvider.updateAll(this);
                setResult(RESULT_OK, new android.content.Intent().putExtra(
                        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));
                finish();
            }));
        }

        setContentView(root);
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
