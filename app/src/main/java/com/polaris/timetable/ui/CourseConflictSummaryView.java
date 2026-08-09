package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/** Non-blocking, accessible summary shown only when the displayed week has conflicts. */
public final class CourseConflictSummaryView extends TextView {
    public CourseConflictSummaryView(Context context) {
        super(context);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinHeight(dp(48));
        setPadding(dp(12), 0, dp(12), 0);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        setTypeface(Typeface.DEFAULT_BOLD);
        setSingleLine(true);
        setEllipsize(TextUtils.TruncateAt.END);
        setClickable(true);
        setFocusable(true);
        setForeground(selectableForeground());
        setVisibility(View.GONE);
    }

    public void setConflictCount(int count, int week, boolean darkMode) {
        if (count <= 0) {
            setVisibility(View.GONE);
            setText("");
            setContentDescription(null);
            return;
        }
        String text = "本周有 " + count + " 组课程时间冲突";
        setText(text);
        setContentDescription("第" + week + "周有" + count
                + "组课程时间冲突，点击查看冲突详情");
        setTextColor(darkMode ? Color.rgb(255, 184, 191) : Color.rgb(164, 31, 49));
        setBackground(conflictBackground(darkMode));
        setVisibility(View.VISIBLE);
    }

    private GradientDrawable conflictBackground(boolean darkMode) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(darkMode
                ? Color.rgb(75, 38, 48)
                : Color.rgb(255, 234, 237));
        background.setStroke(dp(1), darkMode
                ? Color.rgb(230, 112, 127)
                : Color.rgb(205, 66, 84));
        background.setCornerRadius(dp(13));
        return background;
    }

    private android.graphics.drawable.Drawable selectableForeground() {
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, value, true);
        return value.resourceId == 0 ? null : getContext().getDrawable(value.resourceId);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
