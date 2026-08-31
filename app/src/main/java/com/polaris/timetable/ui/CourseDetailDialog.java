package com.polaris.timetable.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.polaris.timetable.Course;
import com.polaris.timetable.R;
import com.polaris.timetable.time.CourseTimeResolver;

public class CourseDetailDialog {
    public interface OnEditListener {
        void onEdit(Course course);
    }

    private final Context context;
    private final Boolean darkOverride;
    private final View blurSource;
    private final CourseTimeResolver.Settings timeSettings;

    public CourseDetailDialog(Context context) {
        this(context, null, null, null);
    }

    public CourseDetailDialog(Context context, Boolean darkOverride) {
        this(context, darkOverride, null, null);
    }

    public CourseDetailDialog(Context context, Boolean darkOverride, View blurSource) {
        this(context, darkOverride, blurSource, null);
    }

    public CourseDetailDialog(Context context, Boolean darkOverride, View blurSource,
                              CourseTimeResolver.Settings timeSettings) {
        this.context = context;
        this.darkOverride = darkOverride;
        this.blurSource = blurSource;
        this.timeSettings = timeSettings == null
                ? CourseTimeResolver.defaultSettings() : timeSettings;
    }

    public void show(Course course, OnEditListener editListener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(18));
        content.setBackground(panelBg());
        scrollView.addView(content);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.TOP);
        content.addView(header);

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        header.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setText(course.name);
        title.setTextColor(inkColor());
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(false);
        heading.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText(context.getString(R.string.detail_title));
        subtitle.setTextColor(mutedColor());
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(5);
        heading.addView(subtitle, subtitleParams);

        Button close = new Button(context);
        close.setText("×");
        close.setTextSize(20);
        close.setTextColor(inkColor());
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, 0, 0, 0);
        close.setMinWidth(0);
        close.setMinimumWidth(0);
        close.setBackground(chipBg());
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        closeParams.leftMargin = dp(12);
        header.addView(close, closeParams);

        addRow(content, context.getString(R.string.detail_row_time), course.isBannerOnlyCourse()
                ? context.getString(R.string.detail_value_banner_only)
                : dayText(course.day) + " · " + CourseTimeResolver.format(course, timeSettings));
        addRow(content, context.getString(R.string.detail_row_weeks),
                emptyText(course.weeks, context.getString(R.string.detail_weeks_empty)));
        addRow(content, context.getString(R.string.detail_row_type), course.courseType.displayName);
        addRow(content, context.getString(R.string.detail_row_credit), creditText(course.credit));
        addRow(content, context.getString(R.string.detail_row_location),
                emptyText(course.location, context.getString(R.string.detail_location_unrecognized)));
        addRow(content, context.getString(R.string.detail_row_teacher),
                emptyText(course.teacher, context.getString(R.string.detail_teacher_unrecognized)));

        Button edit = new Button(context);
        edit.setText(context.getString(R.string.editor_title_edit));
        edit.setTextColor(Color.WHITE);
        edit.setTypeface(Typeface.DEFAULT_BOLD);
        edit.setTextSize(16);
        edit.setBackground(actionBg());
        edit.setOnClickListener(v -> {
            dialog.dismiss();
            if (editListener != null) {
                editListener.onEdit(course);
            }
        });
        LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        editParams.topMargin = dp(12);
        content.addView(edit, editParams);

        dialog.setContentView(glassContent(scrollView, content));
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(dp(340), LinearLayout.LayoutParams.WRAP_CONTENT);
        }
    }

    private View glassContent(ScrollView scrollView, LinearLayout content) {
        if (blurSource == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return scrollView;
        }
        content.setBackgroundColor(Color.TRANSPARENT);
        BackdropBlurView glass = new BackdropBlurView(context);
        glass.setSourceView(blurSource);
        glass.setGlassBackground(glassPanelBg(), dp(24));
        glass.setBlurEnabled(true, dp(22));
        glass.addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return glass;
    }

    private void addRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(cellBg());
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(9);
        parent.addView(row, rowParams);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(mutedColor());
        labelView.setTextSize(14);
        labelView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(64), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(context);
        valueView.setText(value);
        valueView.setTextColor(inkColor());
        valueView.setTextSize(15);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        valueView.setSingleLine(false);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        valueParams.leftMargin = dp(12);
        row.addView(valueView, valueParams);
    }

    private String dayText(int day) {
        if (day >= 0 && day < WeekdayLabels.count()) {
            return WeekdayLabels.label(context, day);
        }
        return context.getString(R.string.detail_weekday_unknown);
    }

    private String emptyText(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private String creditText(String value) {
        return value == null || value.length() == 0
                ? context.getString(R.string.detail_credit_unset)
                : context.getString(R.string.detail_credit_value, value);
    }

    private GradientDrawable panelBg() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(isDarkMode() ? color("#182235") : color("#F8FBFF"));
        drawable.setCornerRadius(dp(24));
        return drawable;
    }

    private GradientDrawable glassPanelBg() {
        GradientDrawable drawable = new GradientDrawable();
        if (isDarkMode()) {
            drawable.setColor(Color.argb(138, 24, 34, 53));
            drawable.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        } else {
            drawable.setColor(Color.argb(168, 248, 251, 255));
            drawable.setStroke(dp(1), Color.argb(150, 255, 255, 255));
        }
        drawable.setCornerRadius(dp(24));
        return drawable;
    }

    private GradientDrawable cellBg() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(isDarkMode() ? color("#141E30") : color("#EEF6FF"));
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private GradientDrawable chipBg() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(isDarkMode() ? color("#22304A") : color("#E9F1FB"));
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private GradientDrawable actionBg() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(isDarkMode() ? color("#1F73E0") : color("#172033"));
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private int color(String hex) {
        return Color.parseColor(hex);
    }

    private boolean isDarkMode() {
        if (darkOverride != null) {
            return darkOverride;
        }
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private int inkColor() {
        return isDarkMode() ? color("#EEF4FF") : color("#172033");
    }

    private int mutedColor() {
        return isDarkMode() ? color("#9AA8BE") : color("#667085");
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
