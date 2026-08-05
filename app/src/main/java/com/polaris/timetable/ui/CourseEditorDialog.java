package com.polaris.timetable.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.polaris.timetable.Course;

import java.util.ArrayList;
import java.util.List;

public final class CourseEditorDialog {
    public interface Listener {
        void onCourseSaved(Course original, Course edited);

        void onEditorDismissed();
    }

    private static final String[] COLOR_LABELS = {
            "自动配色", "海盐蓝", "薄荷绿", "向日黄", "薰衣紫", "珊瑚红", "天空蓝", "青柠绿"
    };
    private static final String[] COLOR_VALUES = {
            "", "#4FA4F3", "#36B889", "#E5A52D", "#956BD6", "#E86464", "#2E9EDB", "#73AD45"
    };

    private final Activity activity;
    private final Course original;
    private final List<Course> courses;
    private final int sectionCount;
    private final int backgroundColor;
    private final int inkColor;
    private final int mutedColor;
    private final boolean darkMode;
    private final Listener listener;

    public CourseEditorDialog(
            Activity activity,
            Course original,
            List<Course> courses,
            int sectionCount,
            int backgroundColor,
            int inkColor,
            int mutedColor,
            boolean darkMode,
            Listener listener) {
        this.activity = activity;
        this.original = original;
        this.courses = courses == null ? new ArrayList<>() : new ArrayList<>(courses);
        this.sectionCount = Math.max(1, sectionCount);
        this.backgroundColor = backgroundColor;
        this.inkColor = inkColor;
        this.mutedColor = mutedColor;
        this.darkMode = darkMode;
        this.listener = listener;
    }

    public void show() {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setSystemBarColors(backgroundColor);

        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(backgroundColor);
        page.setFitsSystemWindows(false);
        page.setPadding(0, statusBarHeight() + dp(10), 0, 0);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), 0, dp(22), 0);
        header.setBackgroundColor(backgroundColor);
        page.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        TextView back = text("‹", inkColor, 34, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> dialog.dismiss());
        header.addView(back, new LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.MATCH_PARENT));

        TextView heading = text(original.name.isEmpty() ? "添加课程" : "编辑课程", inkColor, 24, true);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView save = text("保存", inkColor, 16, true);
        save.setGravity(Gravity.CENTER);
        header.addView(save, new LinearLayout.LayoutParams(dp(58), LinearLayout.LayoutParams.MATCH_PARENT));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        scrollView.setBackgroundColor(backgroundColor);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(32));
        panel.setBackgroundColor(backgroundColor);
        scrollView.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        page.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        EditText name = input("课程名称", original.name);
        EditText weeks = input("第1-20周", original.weeks.isEmpty() ? defaultWeeks() : original.weeks);
        EditText day = input("周几 1-7", String.valueOf(original.day + 1));
        EditText start = input("开始节次", String.valueOf(original.startSection));
        EditText end = input("结束节次", String.valueOf(original.endSection));
        EditText teacher = input("授课老师（可不填）", original.teacher);
        EditText location = input("上课地点（可不填）", original.location);
        day.setInputType(InputType.TYPE_CLASS_NUMBER);
        start.setInputType(InputType.TYPE_CLASS_NUMBER);
        end.setInputType(InputType.TYPE_CLASS_NUMBER);

        final String[] selectedColor = {normalizeColor(original.color)};
        View colorAction = actionRow("●", editorColor(selectedColor[0]), colorLabel(selectedColor[0]));
        colorAction.setOnClickListener(v -> showColorDialog(selectedColor[0], value -> {
            selectedColor[0] = value;
            updateColorRow(colorAction, value);
        }));

        panel.addView(sectionTitle("课程信息"));
        panel.addView(group(
                editorRow("▣", "#10BFAE", name),
                chipRow(courseNameQuickValues(), name),
                colorAction));
        panel.addView(sectionTitle("时间段"));
        panel.addView(group(
                editorRow("▣", "#12B8A6", weeks),
                timeRow(day, start, end),
                chipRow(new String[]{"1-20周", "单周", "双周"}, weeks)));
        panel.addView(group(
                editorRow("♙", "#168FE4", teacher),
                editorRow("▯", "#F0334B", location)));

        save.setOnClickListener(v -> {
            String nextName = name.getText().toString().trim();
            int nextDay = parseBounded(day.getText().toString(), 1, 7, -1) - 1;
            int nextStart = parseBounded(start.getText().toString(), 1, sectionCount, -1);
            int nextEnd = parseBounded(end.getText().toString(), nextStart, sectionCount, -1);
            if (nextName.isEmpty() || nextDay < 0 || nextStart < 1 || nextEnd < nextStart) {
                Toast.makeText(activity, "请填写课程名称和有效时间", Toast.LENGTH_SHORT).show();
                return;
            }
            Course edited = new Course(
                    nextDay,
                    nextStart,
                    nextEnd,
                    nextName,
                    normalizeWeeks(weeks.getText().toString()),
                    location.getText().toString().trim(),
                    teacher.getText().toString().trim(),
                    "",
                    original.credit,
                    selectedColor[0]);
            listener.onCourseSaved(original, edited);
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> listener.onEditorDismissed());
        dialog.setContentView(page);
        dialog.show();
        configureFullScreenWindow(dialog.getWindow());
    }

    private EditText input(String hint, String value) {
        EditText editText = new EditText(activity);
        editText.setHint(hint);
        editText.setText(value);
        editText.setTextColor(inkColor);
        editText.setHintTextColor(darkMode ? color("#768398") : color("#A4A9B3"));
        editText.setTextSize(16);
        editText.setSingleLine(true);
        editText.setEllipsize(TextUtils.TruncateAt.END);
        editText.setGravity(Gravity.CENTER_VERTICAL);
        editText.setIncludeFontPadding(false);
        editText.setPadding(0, 0, 0, 0);
        editText.setBackgroundColor(Color.TRANSPARENT);
        return editText;
    }

    private LinearLayout group(View... children) {
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(14), dp(12), dp(14), dp(14));
        group.setBackground(roundedBg(darkMode ? "#182235" : "#F8FBFF", 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        group.setLayoutParams(params);
        group.setClipChildren(true);
        group.setClipToPadding(true);
        for (View child : children) {
            group.addView(child);
        }
        return group;
    }

    private View sectionTitle(String value) {
        TextView title = text(value, inkColor, 15, true);
        title.setPadding(dp(2), dp(16), 0, dp(2));
        return title;
    }

    private View editorRow(String icon, String iconColor, EditText input) {
        LinearLayout row = row();
        TextView iconView = text(icon, color(iconColor), 24, true);
        iconView.setGravity(Gravity.CENTER);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(42), dp(46)));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        inputParams.leftMargin = dp(4);
        row.addView(input, inputParams);
        return row;
    }

    private View actionRow(String icon, String iconColor, String labelText) {
        LinearLayout row = row();
        TextView iconView = text(icon, color(iconColor), 24, true);
        iconView.setGravity(Gravity.CENTER);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView label = text(labelText, color("#10CFAE"), 16, true);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.leftMargin = dp(6);
        row.addView(label, params);
        return row;
    }

    private View timeRow(EditText day, EditText start, EditText end) {
        day.setGravity(Gravity.CENTER);
        start.setGravity(Gravity.CENTER);
        end.setGravity(Gravity.CENTER);
        LinearLayout row = row();
        addFixedText(row, "◷", "#FFB000", 42, 24, true);
        addFixedText(row, "周", null, 32, 17, false);
        row.addView(day, new LinearLayout.LayoutParams(dp(36), dp(46)));
        addFixedText(row, "第", null, 28, 17, false);
        row.addView(start, new LinearLayout.LayoutParams(dp(40), dp(46)));
        addFixedText(row, "-", null, 16, 17, false).setTextColor(mutedColor);
        row.addView(end, new LinearLayout.LayoutParams(dp(40), dp(46)));
        TextView suffix = text("节", inkColor, 17, false);
        suffix.setGravity(Gravity.CENTER);
        suffix.setIncludeFontPadding(false);
        row.addView(suffix, new LinearLayout.LayoutParams(0, dp(46), 1f));
        return row;
    }

    private TextView addFixedText(
            LinearLayout row, String value, String colorHex, int widthDp, int sizeSp, boolean bold) {
        TextView text = text(value, colorHex == null ? inkColor : color(colorHex), sizeSp, bold);
        text.setGravity(Gravity.CENTER);
        text.setIncludeFontPadding(false);
        row.addView(text, new LinearLayout.LayoutParams(dp(widthDp), dp(46)));
        return text;
    }

    private View chipRow(String[] values, EditText target) {
        if (values.length == 0) {
            Space empty = new Space(activity);
            empty.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0));
            return empty;
        }
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(45));
        scrollParams.topMargin = dp(2);
        scrollParams.bottomMargin = dp(2);
        scroll.setLayoutParams(scrollParams);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(48), dp(2), dp(2), dp(5));
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.MATCH_PARENT));
        for (String value : values) {
            TextView chip = text(value, inkColor, 13, true);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setBackground(roundedStrokeBg("transparent", darkMode ? "#66758C" : "#8B8E96", 18));
            chip.setOnClickListener(v -> target.setText(value));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Math.min(dp(170), Math.max(dp(88), dp(28) + value.length() * dp(13))), dp(36));
            params.rightMargin = dp(8);
            row.addView(chip, params);
        }
        return scroll;
    }

    private void showColorDialog(String currentColor, ColorSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        panel.setBackground(roundedBg(darkMode ? "#182235" : "#F8FBFF", 22));
        panel.addView(text("课程颜色", mutedColor, 13, false));
        String normalized = normalizeColor(currentColor);
        for (int i = 0; i < COLOR_LABELS.length; i++) {
            String value = COLOR_VALUES[i];
            LinearLayout item = new LinearLayout(activity);
            item.setGravity(Gravity.CENTER_VERTICAL);
            TextView dot = text("●", color(editorColor(value)), 22, true);
            dot.setGravity(Gravity.CENTER);
            item.addView(dot, new LinearLayout.LayoutParams(dp(38), dp(48)));
            TextView label = text(COLOR_LABELS[i], inkColor, 16, value.equals(normalized));
            item.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1f));
            TextView check = text(value.equals(normalized) ? "✓" : "", inkColor, 22, true);
            check.setGravity(Gravity.CENTER);
            item.addView(check, new LinearLayout.LayoutParams(dp(32), dp(48)));
            item.setOnClickListener(v -> {
                setter.set(value);
                dialog.dismiss();
            });
            panel.addView(item, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        }
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(darkMode ? 0.68f : 0.42f);
            window.setLayout(Math.min(dp(320), activity.getResources().getDisplayMetrics().widthPixels - dp(48)),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            disableWindowAnimations(window);
        }
    }

    private String[] courseNameQuickValues() {
        List<String> names = new ArrayList<>();
        for (Course course : courses) {
            if (course == null || course.name == null) {
                continue;
            }
            String name = course.name.trim();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        String current = original.name == null ? "" : original.name.trim();
        if (!current.isEmpty() && !names.contains(current)) {
            names.add(0, current);
        }
        return names.toArray(new String[0]);
    }

    private void updateColorRow(View row, String value) {
        if (!(row instanceof LinearLayout)) {
            return;
        }
        LinearLayout layout = (LinearLayout) row;
        ((TextView) layout.getChildAt(0)).setTextColor(color(editorColor(value)));
        ((TextView) layout.getChildAt(1)).setText(colorLabel(value));
    }

    private void configureFullScreenWindow(Window window) {
        if (window == null) {
            return;
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setBackgroundDrawable(new ColorDrawable(backgroundColor));
        window.getDecorView().setBackgroundColor(backgroundColor);
        window.setStatusBarColor(backgroundColor);
        window.setNavigationBarColor(backgroundColor);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = LinearLayout.LayoutParams.MATCH_PARENT;
        attributes.height = LinearLayout.LayoutParams.MATCH_PARENT;
        window.setAttributes(attributes);
        disableWindowAnimations(window);
    }

    private void setSystemBarColors(int value) {
        Window window = activity.getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(value);
            window.setNavigationBarColor(value);
        }
    }

    private void disableWindowAnimations(Window window) {
        window.setWindowAnimations(0);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.windowAnimations = 0;
        window.setAttributes(attributes);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));
        row.setClipChildren(true);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView text(String value, int valueColor, int size, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(valueColor);
        view.setTextSize(size);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return view;
    }

    private GradientDrawable roundedBg(String fill, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fill));
        drawable.setStroke(dp(1), Color.argb(130, 255, 255, 255));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable roundedStrokeBg(String fill, String stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor("transparent".equals(fill) ? Color.TRANSPARENT : color(fill));
        drawable.setStroke(dp(1), color(stroke));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int statusBarHeight() {
        int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? activity.getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private int color(String value) {
        return Color.parseColor(value);
    }

    private int parseBounded(String value, int min, int max, int fallback) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String normalizeWeeks(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? defaultWeeks() : normalized;
    }

    private String defaultWeeks() {
        return "1-20周";
    }

    private String normalizeColor(String value) {
        if (value != null) {
            for (String candidate : COLOR_VALUES) {
                if (candidate.equalsIgnoreCase(value)) {
                    return candidate;
                }
            }
        }
        return "";
    }

    private String colorLabel(String value) {
        String normalized = normalizeColor(value);
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i].equals(normalized)) {
                return COLOR_LABELS[i];
            }
        }
        return COLOR_LABELS[0];
    }

    private String editorColor(String value) {
        String normalized = normalizeColor(value);
        return normalized.isEmpty() ? "#10CFAE" : normalized;
    }

    private interface ColorSetter {
        void set(String value);
    }
}
