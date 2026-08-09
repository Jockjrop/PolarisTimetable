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
                return "上课中";
            case NEXT:
                return "下一节";
            case FINISHED:
                return "已完成";
            case OUTSIDE_SEMESTER:
                return "学期外";
            case NO_COURSES:
            default:
                return "今日无课";
        }
    }

    private String titleText(CourseTimeResolver.TodayOverview value,
                             CourseTimeResolver.TodayStatus status) {
        if (value != null && value.course != null) {
            String name = safe(value.course.name);
            return name.length() == 0 ? "未命名课程" : name;
        }
        switch (status) {
            case FINISHED:
                return "今天的课程已结束";
            case OUTSIDE_SEMESTER:
                return "当前不在学期范围";
            case NO_COURSES:
            default:
                return "今天没有课程";
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
                detail.append(" · 距下课")
                        .append(CourseTimeResolver.countdownText(value.minutesToBoundary));
                if (value.simultaneousCourseCount > 1) {
                    detail.append(" · 时间冲突");
                }
            } else if (status == CourseTimeResolver.TodayStatus.NEXT) {
                detail.append(" · ")
                        .append(CourseTimeResolver.countdownText(value.minutesToBoundary))
                        .append("后开始");
            }
            return detail.toString();
        }
        switch (status) {
            case FINISHED:
                return "今天辛苦了，可以提前看看明天的安排";
            case OUTSIDE_SEMESTER:
                return "请检查第一周日期和学期周数";
            case NO_COURSES:
            default:
                return "享受今天的空闲时间";
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
            text.append("，点击查看课程详情");
        }
        return text.toString();
    }

    private void applyColors(CourseTimeResolver.TodayStatus status) {
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
        return value.resourceId == 0 ? null : getContext().getDrawable(value.resourceId);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
