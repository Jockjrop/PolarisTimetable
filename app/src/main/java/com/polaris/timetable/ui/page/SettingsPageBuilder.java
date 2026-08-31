package com.polaris.timetable.ui.page;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.polaris.timetable.R;

/**
 * 设置页内容构建器：课表/全局/安全/更多四页与行组件。
 * 纯搬代码自 MainActivity，视觉零变化；主题与状态经 {@link Host} 读取，
 * 点击与切换经 Host 回调回 MainActivity 处理，Builder 自身不持有可变状态。
 */
public class SettingsPageBuilder {

    public interface Host {
        // 主题/样式
        boolean isMinimalVisualTheme();
        boolean isDarkModeActive();
        String visualTheme();
        int inkColor();
        int mutedColor();
        String groupColorHex();
        String cardColorHex();
        String pressColorHex();
        Drawable roundedBg(String hex, int radius);
        int color(String hex);
        void applyThemeElevation(View view, int elevationDp);

        // 布局度量
        int contentColumnWidth();
        int statusBarHeight();
        int bottomContentInset();

        // 课表设置数据（raw，供 Builder 格式化）
        String scheduleName();
        String currentWeekValue();
        boolean showSaturday();
        boolean showSunday();
        boolean showOutOfWeek();
        boolean remindersEnabled();
        int reminderMinutesBefore();
        String reminderStatusText();
        String classTimeSummary();
        String firstWeekDay();
        int semesterWeeks();
        int coursesSize();
        String parseDiagnosticsSummary();

        // 课表设置动作
        void onShowSaturdayChanged(boolean value);
        void onShowSundayChanged(boolean value);
        void onShowOutOfWeekChanged(boolean value);
        void onReminderEnabledChanged(boolean value);
        void onReminderLeadClicked(View anchor);
        void onReminderStatusClicked();
        void onClassTimeClicked();
        void onFirstWeekDayClicked(View anchor);
        void onSemesterWeeksClicked(View anchor);
        void onViewAllCoursesClicked();
        void onParseDiagnosticsClicked();

        // 全局设置数据
        String darkMode();
        String backgroundDisplayValue();
        boolean showPracticeBanner();
        boolean collapseLunchBreak();
        String appearancePresetName();
        boolean shellBlurEnabled();
        int headerOpacity();
        int navOpacity();
        int navHeight();
        int navRadius();
        int cellHeight();
        int cellRadius();
        int cellOpacity();

        // 全局设置动作
        void onScheduleSwitchClicked(View anchor);
        void onVisualThemeClicked(View anchor);
        void onDarkModeClicked(View anchor);
        void onBoardBackgroundClicked(View anchor);
        void onShowPracticeChanged(boolean value);
        void onCollapseLunchChanged(boolean value);
        void onAppearancePresetClicked(View anchor);
        void onShellBlurChanged(boolean value);
        void onHeaderOpacityClicked(View anchor);
        void onNavOpacityClicked(View anchor);
        void onNavHeightClicked(View anchor);
        void onNavRadiusClicked(View anchor);
        void onCellHeightClicked(View anchor);
        void onCellRadiusClicked(View anchor);
        void onCellOpacityClicked(View anchor);

        // 安全/更多数据
        String semesterNameDisplay();
        String schoolNameDisplay();
        String accountName();
        String versionText();
        String contactEmail();
        String githubDisplay();

        // 安全/更多动作
        void onSemesterNameClicked();
        void onSchoolClicked();
        void onExportBackupClicked();
        void onRestoreBackupClicked();
        void onVersionClicked();
        void onContactClicked();
        void onGithubClicked();
    }

    private final Host host;

    public SettingsPageBuilder(Host host) {
        this.host = host;
    }

    // ===== 行/组/面板容器：纯搬 MainActivity 私有方法 =====

    public TextView sectionHeader(Context context, String text) {
        TextView header = new TextView(context);
        header.setTag("settings_header");
        header.setText(text);
        header.setTextColor(host.mutedColor());
        header.setTextSize(15);
        header.setPadding(dp(context, 10), dp(context, 22), dp(context, 10), dp(context, 8));
        return header;
    }

    public LinearLayout settingsGroup(Context context) {
        LinearLayout group = new LinearLayout(context);
        group.setTag("settings_group");
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        group.setBackground(host.roundedBg(host.groupColorHex(), host.isMinimalVisualTheme() ? 18 : 22));
        if (!host.isMinimalVisualTheme()) {
            host.applyThemeElevation(group, 2);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(context, 8));
        group.setLayoutParams(params);
        return group;
    }

    public View settingValueRow(Context context, String label, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        row.setOnClickListener(listener);
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setMinimumHeight(dp(context, 58));
        attachRowPressFeedback(context, row);

        TextView labelView = new TextView(context);
        labelView.setTag("setting_label");
        labelView.setText(label);
        labelView.setTextColor(host.inkColor());
        labelView.setTextSize(16);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(context);
        valueView.setTag("setting_value");
        valueView.setText(value);
        valueView.setTextColor(host.mutedColor());
        valueView.setTextSize(15);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        valueView.setSingleLine(false);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(context, 150), LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = dp(context, 10);
        row.addView(valueView, valueParams);
        return row;
    }

    public View settingSwitchRow(Context context, String label, boolean checked, BooleanSetter setter) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        row.setMinimumHeight(dp(context, 58));
        attachRowPressFeedback(context, row);

        TextView labelView = new TextView(context);
        labelView.setTag("setting_label");
        labelView.setText(label);
        labelView.setTextColor(host.inkColor());
        labelView.setTextSize(16);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        SwitchThumbView toggle = switchView(context, checked);
        row.addView(toggle);
        final boolean[] state = {checked};
        View.OnClickListener listener = v -> {
            state[0] = !state[0];
            setter.set(state[0]);
            toggle.setChecked(state[0]);
        };
        row.setOnClickListener(listener);
        toggle.setOnClickListener(listener);
        return row;
    }

    private SwitchThumbView switchView(Context context, boolean checked) {
        SwitchThumbView view = new SwitchThumbView(context, checked, host.isDarkModeActive());
        view.setTag("switch_thumb");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, 52), dp(context, 30));
        params.leftMargin = dp(context, 14);
        view.setLayoutParams(params);
        return view;
    }

    private void attachRowPressFeedback(Context context, View view) {
        view.setOnTouchListener((target, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                target.setBackground(host.roundedBg(host.pressColorHex(), 14));
                target.postDelayed(() -> target.setBackgroundColor(Color.TRANSPARENT), 180);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                target.setBackgroundColor(Color.TRANSPARENT);
            }
            return false;
        });
    }

    public LinearLayout settingsPagePanel(Context context, String headingText) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        int columnWidth = host.contentColumnWidth();
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        if (columnWidth < screenWidth) {
            panel.setLayoutParams(new ScrollView.LayoutParams(columnWidth,
                    LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            panel.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), host.bottomContentInset() + dp(context, 48));
        } else {
            panel.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), host.bottomContentInset() + dp(context, 48));
        }
        return panel;
    }

    // ===== 四页内容工厂 =====

    public LinearLayout createScheduleSettingsPanel(Context context) {
        LinearLayout panel = settingsPagePanel(context, "课表设置");
        panel.addView(settingValueRow(context, context.getString(R.string.settings_row_schedule_name), host.scheduleName(), v -> {}));
        panel.addView(settingValueRow(context, context.getString(R.string.settings_row_current_week), host.currentWeekValue(), v -> {}));
        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_schedule_appearance)));
        LinearLayout displayCard = settingsGroup(context);
        displayCard.addView(settingSwitchRow(context, context.getString(R.string.settings_row_show_saturday), host.showSaturday(), host::onShowSaturdayChanged));
        displayCard.addView(settingSwitchRow(context, context.getString(R.string.settings_row_show_sunday), host.showSunday(), host::onShowSundayChanged));
        displayCard.addView(settingSwitchRow(context, context.getString(R.string.settings_row_show_out_of_week), host.showOutOfWeek(), host::onShowOutOfWeekChanged));
        panel.addView(displayCard);
        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_reminder)));
        LinearLayout reminderCard = settingsGroup(context);
        reminderCard.addView(settingSwitchRow(context, context.getString(R.string.settings_row_reminder), host.remindersEnabled(), host::onReminderEnabledChanged));
        reminderCard.addView(settingValueRow(context, context.getString(R.string.settings_row_reminder_lead),
                context.getString(R.string.settings_minutes_value, host.reminderMinutesBefore()),
                host::onReminderLeadClicked));
        reminderCard.addView(settingValueRow(context, context.getString(R.string.settings_row_reminder_status), host.reminderStatusText(),
                v -> host.onReminderStatusClicked()));
        panel.addView(reminderCard);
        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_class_time)));
        LinearLayout timeCard = settingsGroup(context);
        timeCard.addView(settingValueRow(context, context.getString(R.string.settings_row_class_time_table), host.classTimeSummary(),
                v -> host.onClassTimeClicked()));
        panel.addView(timeCard);
        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_schedule_data)));
        LinearLayout dataCard = settingsGroup(context);
        dataCard.addView(settingValueRow(context, context.getString(R.string.settings_row_first_week_day), host.firstWeekDay(),
                host::onFirstWeekDayClicked));
        dataCard.addView(settingValueRow(context, context.getString(R.string.settings_row_semester_weeks),
                context.getString(R.string.settings_weeks_value, host.semesterWeeks()),
                host::onSemesterWeeksClicked));
        dataCard.addView(settingValueRow(context, context.getString(R.string.settings_row_view_all_courses),
                context.getString(R.string.settings_courses_value, host.coursesSize()), v -> host.onViewAllCoursesClicked()));
        dataCard.addView(settingValueRow(context, context.getString(R.string.settings_row_parse_diagnostics), host.parseDiagnosticsSummary(),
                v -> host.onParseDiagnosticsClicked()));
        panel.addView(dataCard);
        return panel;
    }

    public LinearLayout createGlobalSettingsPanel(Context context) {
        LinearLayout panel = settingsPagePanel(context, "全局设置");
        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_display)));
        LinearLayout displayCard = settingsGroup(context);
        displayCard.addView(settingValueRow(context, context.getString(R.string.settings_row_timetable), host.scheduleName(), host::onScheduleSwitchClicked));
        displayCard.addView(settingValueRow(context, context.getString(R.string.settings_row_visual_theme), host.visualTheme(),
                host::onVisualThemeClicked));
        displayCard.addView(settingValueRow(context, context.getString(R.string.settings_row_dark_mode), host.darkMode(),
                host::onDarkModeClicked));
        displayCard.addView(settingValueRow(context, context.getString(R.string.settings_row_board_background), host.backgroundDisplayValue(),
                host::onBoardBackgroundClicked));
        displayCard.addView(settingSwitchRow(context, context.getString(R.string.settings_row_show_practice), host.showPracticeBanner(), host::onShowPracticeChanged));
        displayCard.addView(settingSwitchRow(context, context.getString(R.string.settings_row_collapse_lunch), host.collapseLunchBreak(), host::onCollapseLunchChanged));
        panel.addView(displayCard);

        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_appearance)));
        LinearLayout presetCard = settingsGroup(context);
        presetCard.addView(settingValueRow(context, context.getString(R.string.settings_row_appearance_preset), host.appearancePresetName(),
                host::onAppearancePresetClicked));

        LinearLayout advancedContainer = new LinearLayout(context);
        advancedContainer.setOrientation(LinearLayout.VERTICAL);
        advancedContainer.setVisibility(View.GONE);
        final boolean[] advancedExpanded = {false};
        final View[] advancedToggle = new View[1];
        advancedToggle[0] = settingValueRow(context, context.getString(R.string.settings_row_advanced),
                context.getString(R.string.settings_advanced_collapsed), v -> {
                    advancedExpanded[0] = !advancedExpanded[0];
                    advancedContainer.setVisibility(advancedExpanded[0] ? View.VISIBLE : View.GONE);
                    updateSettingValueRow(advancedToggle[0], advancedExpanded[0]
                            ? context.getString(R.string.settings_advanced_expanded)
                            : context.getString(R.string.settings_advanced_collapsed));
                });
        presetCard.addView(advancedToggle[0]);
        panel.addView(presetCard);

        advancedContainer.addView(sectionHeader(context, context.getString(R.string.settings_section_ui_detail)));
        advancedContainer.addView(buildAdvancedShellSettings(context));
        advancedContainer.addView(sectionHeader(context, context.getString(R.string.settings_section_board_frame)));
        advancedContainer.addView(buildAdvancedScheduleFrameSettings(context));
        panel.addView(advancedContainer);
        return panel;
    }

    public LinearLayout buildAdvancedShellSettings(Context context) {
        LinearLayout shellCard = settingsGroup(context);
        shellCard.addView(settingSwitchRow(context, context.getString(R.string.settings_row_shell_blur), host.shellBlurEnabled(), host::onShellBlurChanged));
        shellCard.addView(settingValueRow(context, context.getString(R.string.settings_row_header_opacity),
                context.getString(R.string.settings_percent_value, host.headerOpacity()),
                host::onHeaderOpacityClicked));
        shellCard.addView(settingValueRow(context, context.getString(R.string.settings_row_nav_opacity),
                context.getString(R.string.settings_percent_value, host.navOpacity()),
                host::onNavOpacityClicked));
        shellCard.addView(settingValueRow(context, context.getString(R.string.settings_row_nav_height),
                context.getString(R.string.settings_dp_value, host.navHeight()),
                host::onNavHeightClicked));
        shellCard.addView(settingValueRow(context, context.getString(R.string.settings_row_nav_radius),
                context.getString(R.string.settings_dp_value, host.navRadius()),
                host::onNavRadiusClicked));
        return shellCard;
    }

    public LinearLayout buildAdvancedScheduleFrameSettings(Context context) {
        LinearLayout frameCard = settingsGroup(context);
        frameCard.addView(settingValueRow(context, context.getString(R.string.settings_row_cell_height),
                context.getString(R.string.settings_dp_value, host.cellHeight()),
                host::onCellHeightClicked));
        frameCard.addView(settingValueRow(context, context.getString(R.string.settings_row_cell_radius),
                context.getString(R.string.settings_dp_value, host.cellRadius()),
                host::onCellRadiusClicked));
        frameCard.addView(settingValueRow(context, context.getString(R.string.settings_row_cell_opacity),
                context.getString(R.string.settings_percent_value, host.cellOpacity()),
                host::onCellOpacityClicked));
        return frameCard;
    }

    public LinearLayout createSecuritySettingsPanel(Context context) {
        LinearLayout panel = settingsPagePanel(context, "安全设置");
        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_schedule_info)));
        LinearLayout scheduleInfoCard = settingsGroup(context);
        scheduleInfoCard.addView(settingValueRow(context, context.getString(R.string.settings_row_semester), host.semesterNameDisplay(),
                v -> host.onSemesterNameClicked()));
        scheduleInfoCard.addView(settingValueRow(context, context.getString(R.string.settings_row_school), host.schoolNameDisplay(),
                v -> host.onSchoolClicked()));
        panel.addView(scheduleInfoCard);

        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_account)));
        LinearLayout accountCard = settingsGroup(context);
        accountCard.addView(settingValueRow(context, context.getString(R.string.settings_row_login),
                context.getString(R.string.settings_account_value, host.accountName()), v -> {}));
        accountCard.addView(settingValueRow(context, context.getString(R.string.settings_row_local_data),
                context.getString(R.string.settings_local_data_value), v -> {}));
        panel.addView(accountCard);

        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_backup)));
        LinearLayout backupCard = settingsGroup(context);
        backupCard.addView(settingValueRow(context, context.getString(R.string.settings_row_export_backup),
                context.getString(R.string.settings_export_backup_desc),
                v -> host.onExportBackupClicked()));
        backupCard.addView(settingValueRow(context, context.getString(R.string.settings_row_restore_backup),
                context.getString(R.string.settings_restore_backup_desc),
                v -> host.onRestoreBackupClicked()));
        panel.addView(backupCard);
        return panel;
    }

    public LinearLayout createMoreSettingsPanel(Context context) {
        LinearLayout panel = settingsPagePanel(context, "更多");
        panel.addView(sectionHeader(context, context.getString(R.string.settings_section_about)));
        LinearLayout aboutCard = settingsGroup(context);
        aboutCard.addView(settingValueRow(context, context.getString(R.string.settings_row_version), host.versionText(), v -> host.onVersionClicked()));
        aboutCard.addView(settingValueRow(context, context.getString(R.string.settings_row_contact), host.contactEmail(), v -> host.onContactClicked()));
        aboutCard.addView(settingValueRow(context, context.getString(R.string.settings_row_github),
                host.githubDisplay(), v -> host.onGithubClicked()));
        panel.addView(aboutCard);
        return panel;
    }

    // 供 Host 在对话框回调后局部刷新单行显示
    public static void updateSettingValueRow(View row, String value) {
        if (!(row instanceof LinearLayout)) return;
        LinearLayout layout = (LinearLayout) row;
        if (layout.getChildCount() > 1 && layout.getChildAt(1) instanceof TextView) {
            ((TextView) layout.getChildAt(1)).setText(value);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public interface BooleanSetter {
        void set(boolean value);
    }

    // 自绘制开关_thumb，纯搬 MainActivity.SwitchThumbView，改为静态与 Context 解耦
    private static class SwitchThumbView extends View {
        private boolean checked;
        private boolean dark;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwitchThumbView(Context context, boolean checked, boolean dark) {
            super(context);
            this.checked = checked;
            this.dark = dark;
        }

        void setChecked(boolean checked) {
            if (this.checked == checked) return;
            this.checked = checked;
            invalidate();
        }

        void setDark(boolean dark) {
            if (this.dark == dark) return;
            this.dark = dark;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float radius = height / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(checked ? Color.parseColor("#3E8BFF") : Color.parseColor(dark ? "#252B36" : "#D8DDE6"));
            canvas.drawRoundRect(0f, 0f, width, height, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(getResources().getDisplayMetrics().density * 1f);
            paint.setColor(checked ? Color.parseColor("#63A4FF") : Color.parseColor(dark ? "#3B424D" : "#C9D0DA"));
            float inset = getResources().getDisplayMetrics().density * 0.5f;
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            float thumbRadius = height / 2f - Math.round(4 * getResources().getDisplayMetrics().density);
            float centerX = checked ? width - radius : radius;
            canvas.drawCircle(centerX, height / 2f, thumbRadius, paint);
        }
    }
}
