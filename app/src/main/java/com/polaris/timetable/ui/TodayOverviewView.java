package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.polaris.timetable.Course;
import com.polaris.timetable.R;
import com.polaris.timetable.time.CourseTimeResolver;

/** Compact, glanceable state for the current or next course. */
public final class TodayOverviewView extends LinearLayout {
    public interface OnCourseClickListener {
        void onCourseClick(Course course);
    }

    private final TextView statusView;
    private final TextView titleView;
    private final TextView detailView;
    private CourseTimeResolver.TodayOverview overview;
    private OnCourseClickListener courseClickListener;
    private boolean darkMode;
    private boolean large;
    private String visualTheme = PolarisVisualTheme.MINIMAL;

    public TodayOverviewView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(dp(60));
        setPadding(dp(2), dp(7), dp(2), dp(2));

        statusView = new TextView(context);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusView.setTypeface(Typeface.DEFAULT_BOLD);
        LayoutParams statusParams = new LayoutParams(dp(60), dp(30));
        statusParams.rightMargin = dp(10);
        addView(statusView, statusParams);

        LinearLayout textGroup = new LinearLayout(context);
        textGroup.setOrientation(VERTICAL);
        textGroup.setGravity(Gravity.CENTER_VERTICAL);
        addView(textGroup, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textGroup.addView(titleView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        detailView = new TextView(context);
        detailView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        detailView.setSingleLine(true);
        detailView.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams detailParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(2);
        textGroup.addView(detailView, detailParams);

        setOnClickListener(v -> {
            if (overview != null && overview.course != null && courseClickListener != null) {
                courseClickListener.onCourseClick(overview.course);
            }
        });
        setOverview(null, false);
    }

    public void setOnCourseClickListener(OnCourseClickListener listener) {
        courseClickListener = listener;
    }

    /**
     * 大号模式：用于平板右侧独立面板——更大字号、状态胶囊加大、
     * 详情允许两行，完整展示信息。
     */
    public void setLarge(boolean large) {
        if (this.large == large) {
            return;
        }
        this.large = large;
        setMinimumHeight(dp(large ? 92 : 60));
        setPadding(dp(2), dp(large ? 12 : 7), dp(2), dp(large ? 8 : 2));
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, large ? 14 : 12);
        LayoutParams statusParams = new LayoutParams(dp(large ? 76 : 60), dp(large ? 38 : 30));
        statusParams.rightMargin = dp(10);
        statusView.setLayoutParams(statusParams);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, large ? 21 : 16);
        detailView.setTextSize(TypedValue.COMPLEX_UNIT_SP, large ? 16 : 13);
        if (large) {
            detailView.setSingleLine(false);
            detailView.setMaxLines(2);
            detailView.setEllipsize(TextUtils.TruncateAt.END);
        } else {
            detailView.setSingleLine(true);
            detailView.setMaxLines(1);
        }
        requestLayout();
    }

    public void setVisualTheme(String theme) {
        String nextTheme = PolarisVisualTheme.normalize(theme);
        if (nextTheme.equals(visualTheme)) {
            return;
        }
        visualTheme = nextTheme;
        CourseTimeResolver.TodayStatus status = overview == null
                ? CourseTimeResolver.TodayStatus.NO_COURSES : overview.status;
        applyColors(status);
    }

    public void setOverview(CourseTimeResolver.TodayOverview value, boolean useDarkMode) {
        overview = value;
        darkMode = useDarkMode;
        CourseTimeResolver.TodayStatus status = value == null
                ? CourseTimeResolver.TodayStatus.NO_COURSES : value.status;
        statusView.setText(statusText(status));
        titleView.setText(titleText(value, status));
        detailView.setText(detailText(value, status));
        applyColors(status);

        boolean actionable = value != null && value.hasCourse();
        setClickable(actionable);
        setFocusable(actionable);
        if (actionable) {
            setForeground(selectableForeground());
        } else {
            setForeground(null);
        }
        setContentDescription(accessibilityText(value, status));
    }

    private String statusText(CourseTimeResolver.TodayStatus status) {
        switch (status) {
            case ONGOING:
                return getContext().getString(R.string.today_status_ongoing);
            case NEXT:
                return getContext().getString(R.string.today_status_next);
            case FINISHED:
                return getContext().getString(R.string.today_status_finished);
            case OUTSIDE_SEMESTER:
                return getContext().getString(R.string.today_status_outside);
            case NO_COURSES:
            default:
                return getContext().getString(R.string.today_status_no_courses);
        }
    }

    private String titleText(CourseTimeResolver.TodayOverview value,
                             CourseTimeResolver.TodayStatus status) {
        if (value != null && value.course != null) {
            String name = safe(value.course.name);
            return name.length() == 0 ? getContext().getString(R.string.course_unnamed) : name;
        }
        switch (status) {
            case FINISHED:
                return getContext().getString(R.string.today_title_finished);
            case OUTSIDE_SEMESTER:
                return getContext().getString(R.string.today_title_outside);
            case NO_COURSES:
            default:
                return getContext().getString(R.string.today_title_no_courses);
        }
    }

    private String detailText(CourseTimeResolver.TodayOverview value,
                              CourseTimeResolver.TodayStatus status) {
        if (value != null && value.course != null) {
            StringBuilder detail = new StringBuilder(value.timeText());
            String location = safe(value.course.location);
            if (location.length() > 0) {
                detail.append(" · ").append(location);
            }
            if (status == CourseTimeResolver.TodayStatus.ONGOING) {
                detail.append(getContext().getString(R.string.today_detail_ongoing_countdown,
                        CourseTimeResolver.countdownText(value.minutesToBoundary)));
                if (value.simultaneousCourseCount > 1) {
                    detail.append(getContext().getString(R.string.today_detail_conflict));
                }
            } else if (status == CourseTimeResolver.TodayStatus.NEXT) {
                detail.append(getContext().getString(R.string.today_detail_next_countdown,
                        CourseTimeResolver.countdownText(value.minutesToBoundary)));
            }
            return detail.toString();
        }
        switch (status) {
            case FINISHED:
                return getContext().getString(R.string.today_detail_finished);
            case OUTSIDE_SEMESTER:
                return getContext().getString(R.string.today_detail_outside);
            case NO_COURSES:
            default:
                return getContext().getString(R.string.today_detail_no_courses);
        }
    }

    private String accessibilityText(CourseTimeResolver.TodayOverview value,
                                     CourseTimeResolver.TodayStatus status) {
        StringBuilder text = new StringBuilder(statusText(status))
                .append("，").append(titleText(value, status));
        String detail = detailText(value, status);
        if (detail.length() > 0) {
            text.append("，").append(detail);
        }
        if (value != null && value.hasCourse()) {
            text.append(getContext().getString(R.string.today_cd_hint));
        }
        return text.toString();
    }

    private void applyColors(CourseTimeResolver.TodayStatus status) {
        if (!PolarisVisualTheme.MINIMAL.equals(visualTheme)) {
            applyThemeColors(status);
            return;
        }
        int title = darkMode ? Color.rgb(242, 247, 255) : Color.rgb(16, 35, 62);
        int detail = darkMode ? Color.rgb(178, 194, 217) : Color.rgb(83, 105, 137);
        int accent;
        int accentSurface;
        switch (status) {
            case ONGOING:
                accent = darkMode ? Color.rgb(115, 222, 176) : Color.rgb(20, 122, 82);
                accentSurface = darkMode ? Color.rgb(30, 73, 60) : Color.rgb(220, 246, 235);
                break;
            case NEXT:
                accent = darkMode ? Color.rgb(132, 190, 255) : Color.rgb(31, 100, 196);
                accentSurface = darkMode ? Color.rgb(31, 60, 99) : Color.rgb(224, 238, 255);
                break;
            default:
                accent = darkMode ? Color.rgb(190, 201, 219) : Color.rgb(78, 96, 121);
                accentSurface = darkMode ? Color.rgb(48, 61, 80) : Color.rgb(232, 238, 247);
                break;
        }
        titleView.setTextColor(title);
        detailView.setTextColor(detail);
        statusView.setTextColor(accent);
        statusView.setBackground(pillBackground(accentSurface));
    }

    private void applyThemeColors(CourseTimeResolver.TodayStatus status) {
        int title = PolarisVisualTheme.inkColor(visualTheme, darkMode);
        int detail = PolarisVisualTheme.mutedColor(visualTheme, darkMode);
        int accent = PolarisVisualTheme.accentColor(visualTheme, darkMode);
        int accentSurface = PolarisVisualTheme.accentSurfaceColor(visualTheme, darkMode);
        if (status == CourseTimeResolver.TodayStatus.ONGOING) {
            accent = darkMode ? Color.rgb(126, 226, 180) : Color.rgb(35, 130, 86);
            accentSurface = darkMode ? Color.argb(112, 32, 92, 66)
                    : Color.argb(160, 216, 246, 232);
        }
        titleView.setTextColor(title);
        detailView.setTextColor(detail);
        statusView.setTextColor(accent);
        statusView.setBackground(pillBackground(accentSurface));
    }

    private GradientDrawable pillBackground(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(15));
        return background;
    }

    private android.graphics.drawable.Drawable selectableForeground() {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, value, true);
        return value.resourceId == 0
                ? null : androidx.appcompat.content.res.AppCompatResources.getDrawable(
                        getContext(), value.resourceId);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
