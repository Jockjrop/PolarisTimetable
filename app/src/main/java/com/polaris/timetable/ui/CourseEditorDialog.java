package com.polaris.timetable.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.polaris.timetable.Course;
import com.polaris.timetable.CourseDeletionScope;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.WeekRule;
import com.polaris.timetable.parser.WeekRuleParser;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.ArrayList;
import java.util.List;

public final class CourseEditorDialog {
    private static final int MAX_SELECTABLE_WEEK = 20;
    private static final WeekRuleParser WEEK_RULE_PARSER = new WeekRuleParser();
    public interface Listener {
        void onCourseSaved(Course original, Course edited);

        void onCourseDeleteRequested(Course original, CourseDeletionScope scope);

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
    private final CourseTimeResolver.Settings timeSettings;
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
            CourseTimeResolver.Settings timeSettings,
            int backgroundColor,
            int inkColor,
            int mutedColor,
            boolean darkMode,
            Listener listener) {
        this.activity = activity;
        this.original = original;
        this.courses = courses == null ? new ArrayList<>() : new ArrayList<>(courses);
        this.sectionCount = Math.max(1, sectionCount);
        this.timeSettings = timeSettings == null
                ? CourseTimeResolver.defaultSettings() : timeSettings;
        this.backgroundColor = backgroundColor;
        this.inkColor = inkColor;
        this.mutedColor = mutedColor;
        this.darkMode = darkMode;
        this.listener = listener;
    }

    public void show() {
        int dialogTheme = darkMode
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar;
        Dialog dialog = new Dialog(activity, dialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

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
        EditText teacher = input("授课老师（可不填）", original.teacher);
        EditText location = input("上课地点（可不填）", original.location);

        final String[] selectedColor = {normalizeColor(original.color)};
        final CourseType[] selectedType = {original.courseType};
        final boolean[] bannerOnly = {original.isBannerOnlyCourse()};
        final int[] selectedDay = {
                original.hasScheduledTime() ? Math.max(0, Math.min(6, original.day)) : 0};
        final int[] selectedStart = {
                original.hasSectionTime() ? Math.max(1, original.startSection) : 1};
        final int[] selectedEnd = {
                original.hasSectionTime()
                        ? Math.max(selectedStart[0], original.endSection)
                        : Math.min(2, sectionCount)};
        CourseTimeResolver.TimeRange initialRange = CourseTimeResolver.timeRange(original, timeSettings);
        final int[] selectedStartMinute = {
                original.hasExactTime() ? original.startMinuteOfDay
                        : initialRange == null ? 8 * 60 : initialRange.startMinutes};
        final int[] selectedEndMinute = {
                original.hasExactTime() ? original.endMinuteOfDay
                        : initialRange == null ? 9 * 60 : initialRange.endMinutes};
        final CourseTimeMode[] selectedTimeMode = {
                original.hasExactTime() ? CourseTimeMode.CLOCK : CourseTimeMode.SECTION};
        final WeekSelection[] selectedWeeks = {
                WeekSelection.from(original.weeks.isEmpty() ? defaultWeeks() : original.weeks)};

        View weeksAction = actionRow("周", "#12B8A6", selectedWeeks[0].displayText());
        weeksAction.setContentDescription("选择周次，当前" + selectedWeeks[0].displayText());
        weeksAction.setOnClickListener(v -> showWeekSelectionDialog(selectedWeeks[0], value -> {
            selectedWeeks[0] = value;
            updateActionRow(weeksAction, value.displayText());
            weeksAction.setContentDescription("选择周次，当前" + value.displayText());
        }));
        View dayAction = actionRow("日", "#168FE4", dayLabel(selectedDay[0]));
        dayAction.setContentDescription("选择星期，当前" + dayLabel(selectedDay[0]));
        dayAction.setOnClickListener(v -> showDayDialog(selectedDay[0], value -> {
            selectedDay[0] = value;
            updateActionRow(dayAction, dayLabel(value));
            dayAction.setContentDescription("选择星期，当前" + dayLabel(value));
        }));
        View sectionAction = actionRow(
                "节", "#FFB000", sectionLabel(selectedStart[0], selectedEnd[0]));
        sectionAction.setContentDescription(
                "选择节次，当前" + sectionLabel(selectedStart[0], selectedEnd[0]));
        sectionAction.setOnClickListener(v -> showSectionDialog(
                selectedStart[0], selectedEnd[0], (start, end) -> {
                    selectedStart[0] = start;
                    selectedEnd[0] = end;
                    updateActionRow(sectionAction, sectionLabel(start, end));
                    sectionAction.setContentDescription(
                            "选择节次，当前" + sectionLabel(start, end));
                }));

        View startTimeAction = actionRow(
                "起", "#FFB000", "开始 " + minuteText(selectedStartMinute[0]));
        View endTimeAction = actionRow(
                "止", "#FFB000", "结束 " + minuteText(selectedEndMinute[0]));
        startTimeAction.setContentDescription(
                "选择开始时间，当前" + minuteText(selectedStartMinute[0]));
        endTimeAction.setContentDescription(
                "选择结束时间，当前" + minuteText(selectedEndMinute[0]));
        startTimeAction.setOnClickListener(v -> showTimeDialog(
                "选择开始时间", selectedStartMinute[0], value -> {
                    int previousDuration = Math.max(15,
                            selectedEndMinute[0] - selectedStartMinute[0]);
                    selectedStartMinute[0] = value;
                    if (selectedEndMinute[0] <= value) {
                        selectedEndMinute[0] = Math.min(23 * 60 + 59,
                                value + previousDuration);
                    }
                    updateActionRow(startTimeAction,
                            "开始 " + minuteText(selectedStartMinute[0]));
                    updateActionRow(endTimeAction,
                            "结束 " + minuteText(selectedEndMinute[0]));
                    startTimeAction.setContentDescription(
                            "选择开始时间，当前" + minuteText(selectedStartMinute[0]));
                    endTimeAction.setContentDescription(
                            "选择结束时间，当前" + minuteText(selectedEndMinute[0]));
                }));
        endTimeAction.setOnClickListener(v -> showTimeDialog(
                "选择结束时间", selectedEndMinute[0], value -> {
                    if (value <= selectedStartMinute[0]) {
                        Toast.makeText(activity, "结束时间必须晚于开始时间", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedEndMinute[0] = value;
                    updateActionRow(endTimeAction,
                            "结束 " + minuteText(selectedEndMinute[0]));
                    endTimeAction.setContentDescription(
                            "选择结束时间，当前" + minuteText(selectedEndMinute[0]));
                }));

        LinearLayout clockTimeRows = new LinearLayout(activity);
        clockTimeRows.setOrientation(LinearLayout.VERTICAL);
        clockTimeRows.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        clockTimeRows.addView(startTimeAction);
        clockTimeRows.addView(endTimeAction);
        View timeModeAction = actionRow(
                "时", "#FFB000", timeModeLabel(selectedTimeMode[0]));
        timeModeAction.setContentDescription(
                "选择时间方式，当前" + timeModeLabel(selectedTimeMode[0]));
        timeModeAction.setOnClickListener(v -> showTimeModeDialog(selectedTimeMode[0], value -> {
            selectedTimeMode[0] = value;
            updateActionRow(timeModeAction, timeModeLabel(value));
            timeModeAction.setContentDescription("选择时间方式，当前" + timeModeLabel(value));
            sectionAction.setVisibility(value == CourseTimeMode.SECTION ? View.VISIBLE : View.GONE);
            clockTimeRows.setVisibility(value == CourseTimeMode.CLOCK ? View.VISIBLE : View.GONE);
        }));
        LinearLayout timeSelectionRows = new LinearLayout(activity);
        timeSelectionRows.setOrientation(LinearLayout.VERTICAL);
        timeSelectionRows.addView(dayAction);
        timeSelectionRows.addView(timeModeAction);
        timeSelectionRows.addView(sectionAction);
        timeSelectionRows.addView(clockTimeRows);
        sectionAction.setVisibility(
                selectedTimeMode[0] == CourseTimeMode.SECTION ? View.VISIBLE : View.GONE);
        clockTimeRows.setVisibility(
                selectedTimeMode[0] == CourseTimeMode.CLOCK ? View.VISIBLE : View.GONE);
        View placementAction = actionRow("位", "#FFB000", bannerOnly[0] ? "顶部横幅" : "固定节次");
        View typeAction = actionRow("类", "#168FE4", selectedType[0].displayName);
        typeAction.setOnClickListener(v -> showCourseTypeDialog(selectedType[0], value -> {
            selectedType[0] = value;
            if (!value.supportsBannerOnly() && bannerOnly[0]) {
                bannerOnly[0] = false;
                updatePlacementRow(placementAction, false);
                timeSelectionRows.setVisibility(View.VISIBLE);
            }
            updateTypeRow(typeAction, value);
        }));
        placementAction.setOnClickListener(v -> showCoursePlacementDialog(bannerOnly[0], value -> {
            bannerOnly[0] = value;
            if (value && !selectedType[0].supportsBannerOnly()) {
                selectedType[0] = CourseType.PRACTICE;
                updateTypeRow(typeAction, selectedType[0]);
            }
            updatePlacementRow(placementAction, value);
            timeSelectionRows.setVisibility(value ? View.GONE : View.VISIBLE);
        }));
        timeSelectionRows.setVisibility(bannerOnly[0] ? View.GONE : View.VISIBLE);
        View colorAction = actionRow("●", editorColor(selectedColor[0]), colorLabel(selectedColor[0]));
        colorAction.setOnClickListener(v -> showColorDialog(selectedColor[0], value -> {
            selectedColor[0] = value;
            updateColorRow(colorAction, value);
        }));

        panel.addView(sectionTitle("课程信息"));
        panel.addView(group(
                editorRow("▣", "#10BFAE", name),
                chipRow(courseNameQuickValues(), name),
                typeAction,
                colorAction));
        panel.addView(sectionTitle("时间段"));
        panel.addView(group(
                weeksAction,
                placementAction,
                timeSelectionRows));
        panel.addView(group(
                editorRow("♙", "#168FE4", teacher),
                editorRow("▯", "#F0334B", location)));
        if (courses.contains(original)) {
            TextView delete = text("删除此课程", color("#E5484D"), 16, true);
            delete.setGravity(Gravity.CENTER);
            delete.setMinHeight(dp(50));
            delete.setBackground(roundedStrokeBg("transparent", darkMode ? "#A94A55" : "#D96A70", 16));
            delete.setContentDescription("删除此课程");
            delete.setOnClickListener(v -> showDeleteScopeDialog(dialog));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
            deleteParams.topMargin = dp(24);
            panel.addView(delete, deleteParams);
        }

        save.setOnClickListener(v -> {
            String nextName = name.getText().toString().trim();
            int nextDay = bannerOnly[0]
                    ? -1 : selectedDay[0];
            CourseTimeMode nextTimeMode = bannerOnly[0]
                    ? CourseTimeMode.NONE : selectedTimeMode[0];
            int nextStart = nextTimeMode == CourseTimeMode.SECTION ? selectedStart[0] : 0;
            int nextEnd = nextTimeMode == CourseTimeMode.SECTION ? selectedEnd[0] : 0;
            int nextStartMinute = nextTimeMode == CourseTimeMode.CLOCK
                    ? selectedStartMinute[0] : -1;
            int nextEndMinute = nextTimeMode == CourseTimeMode.CLOCK
                    ? selectedEndMinute[0] : -1;
            if (nextName.isEmpty()) {
                Toast.makeText(activity, "请填写课程名称", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean validSections = nextTimeMode == CourseTimeMode.SECTION
                    && nextDay >= 0 && nextStart >= 1 && nextEnd >= nextStart;
            boolean validClock = nextTimeMode == CourseTimeMode.CLOCK
                    && nextDay >= 0 && nextStartMinute >= 0
                    && nextStartMinute < nextEndMinute && nextEndMinute <= 24 * 60;
            if (!bannerOnly[0] && !validSections && !validClock) {
                Toast.makeText(activity, "请填写课程名称和有效时间", Toast.LENGTH_SHORT).show();
                return;
            }
            Course edited = new Course(
                    nextDay,
                    nextStart,
                    nextEnd,
                    nextName,
                    selectedWeeks[0].toCourseText(),
                    location.getText().toString().trim(),
                    teacher.getText().toString().trim(),
                    "",
                    original.credit,
                    selectedColor[0],
                    bannerOnly[0] && !selectedType[0].supportsBannerOnly()
                            ? CourseType.PRACTICE : selectedType[0],
                    StableCourseId.isValid(original.structuredCourseId)
                            ? original.structuredCourseId : StableCourseId.create(),
                    original.meetingId,
                    nextTimeMode,
                    nextStartMinute,
                    nextEndMinute);
            listener.onCourseSaved(original, edited);
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> listener.onEditorDismissed());
        dialog.setContentView(page);
        dialog.show();
        configureFullScreenWindow(dialog.getWindow());
    }

    private void showDeleteScopeDialog(Dialog editorDialog) {
        Dialog scopeDialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        panel.setBackground(roundedBg(darkMode ? "#182235" : "#F8FBFF", 22));

        TextView heading = text("删除课程", inkColor, 20, true);
        panel.addView(heading);
        TextView message = text("请选择删除范围。删除后无法撤销。", mutedColor, 14, false);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(5);
        messageParams.bottomMargin = dp(10);
        panel.addView(message, messageParams);

        panel.addView(deleteScopeChoice(
                "仅删除本周此节",
                "保留其他周相同时间的课程",
                () -> dispatchDelete(scopeDialog, editorDialog, CourseDeletionScope.CURRENT_WEEK)));
        panel.addView(deleteScopeChoice(
                "删除每周此节",
                "删除这个星期与节次的全部周次",
                () -> dispatchDelete(scopeDialog, editorDialog, CourseDeletionScope.CURRENT_MEETING)));
        panel.addView(deleteScopeChoice(
                "删除该课程全部节次",
                "删除该课程在课表中的所有时间安排",
                () -> dispatchDelete(scopeDialog, editorDialog, CourseDeletionScope.ALL_MEETINGS)));

        TextView cancel = text("取消", inkColor, 16, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> scopeDialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        cancelParams.topMargin = dp(6);
        panel.addView(cancel, cancelParams);

        scopeDialog.setContentView(panel);
        scopeDialog.show();
        Window window = scopeDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(darkMode ? 0.68f : 0.42f);
            window.setLayout(
                    Math.min(dp(360), activity.getResources().getDisplayMetrics().widthPixels - dp(40)),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            disableWindowAnimations(window);
        }
    }

    private View deleteScopeChoice(String title, String description, Runnable action) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(12), dp(8), dp(12), dp(8));
        item.setMinimumHeight(dp(64));
        item.setBackground(roundedBg(darkMode ? "#202D44" : "#FFFFFF", 14));
        item.setOnClickListener(v -> action.run());

        TextView titleView = text(title, color(darkMode ? "#FF8A91" : "#C9343C"), 16, true);
        titleView.setSingleLine(false);
        item.addView(titleView);
        TextView descriptionView = text(description, mutedColor, 13, false);
        descriptionView.setSingleLine(false);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(2);
        item.addView(descriptionView, descriptionParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(7);
        item.setLayoutParams(params);
        return item;
    }

    private void dispatchDelete(
            Dialog scopeDialog, Dialog editorDialog, CourseDeletionScope scope) {
        scopeDialog.dismiss();
        listener.onCourseDeleteRequested(original, scope);
        editorDialog.dismiss();
    }

    private EditText input(String hint, String value) {
        EditText editText = new EditText(activity);
        editText.setHint(hint);
        editText.setText(value);
        editText.setTextColor(inkColor);
        editText.setHintTextColor(darkMode ? color("#7F8DA3") : color("#667085"));
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

    private void updateActionRow(View row, String value) {
        if (row instanceof LinearLayout && ((LinearLayout) row).getChildCount() > 1) {
            ((TextView) ((LinearLayout) row).getChildAt(1)).setText(value);
        }
    }

    private String dayLabel(int day) {
        String[] labels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return labels[Math.max(0, Math.min(labels.length - 1, day))];
    }

    private String sectionLabel(int start, int end) {
        return "第 " + start + "–" + end + " 节";
    }

    private String timeModeLabel(CourseTimeMode mode) {
        return mode == CourseTimeMode.CLOCK ? "具体时间" : "按节次";
    }

    private String minuteText(int minuteOfDay) {
        return CourseTimeResolver.formatMinuteOfDay(minuteOfDay);
    }

    private void showTimeModeDialog(CourseTimeMode current, TimeModeSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = compactDialogPanel("选择时间方式");
        TextView section = dialogChoice("按节次", current == CourseTimeMode.SECTION);
        section.setContentDescription("按节次，根据学校上课时间显示");
        section.setOnClickListener(v -> {
            setter.set(CourseTimeMode.SECTION);
            dialog.dismiss();
        });
        TextView clock = dialogChoice("具体时间", current == CourseTimeMode.CLOCK);
        clock.setContentDescription("具体时间，自定义当天开始和结束时间");
        clock.setOnClickListener(v -> {
            setter.set(CourseTimeMode.CLOCK);
            dialog.dismiss();
        });
        panel.addView(section);
        panel.addView(clock);
        showCompactDialog(dialog, panel, 260);
    }

    private void showTimeDialog(String title, int currentMinute, IntSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = compactDialogPanel(title);
        int pickerTheme = darkMode
                ? android.R.style.Theme_Material
                : android.R.style.Theme_Material_Light;
        TimePicker picker = new TimePicker(new ContextThemeWrapper(activity, pickerTheme));
        picker.setIs24HourView(true);
        int bounded = Math.max(0, Math.min(23 * 60 + 59, currentMinute));
        picker.setHour(bounded / 60);
        picker.setMinute(bounded % 60);
        picker.setContentDescription(title);
        panel.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView confirm = dialogChoice("确定", true);
        confirm.setContentDescription("确定" + title);
        confirm.setOnClickListener(v -> {
            setter.set(picker.getHour() * 60 + picker.getMinute());
            dialog.dismiss();
        });
        panel.addView(confirm);
        showCompactDialog(dialog, panel, 430);
    }

    private void showDayDialog(int currentDay, IntSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = compactDialogPanel("选择星期");
        for (int day = 0; day < 7; day++) {
            final int value = day;
            TextView item = dialogChoice(dayLabel(day), day == currentDay);
            item.setOnClickListener(v -> {
                setter.set(value);
                dialog.dismiss();
            });
            panel.addView(item);
        }
        showCompactDialog(dialog, panel, 320);
    }

    private void showSectionDialog(int currentStart, int currentEnd, SectionSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = compactDialogPanel("选择节次");

        LinearLayout pickers = new LinearLayout(activity);
        pickers.setOrientation(LinearLayout.HORIZONTAL);
        pickers.setGravity(Gravity.CENTER);
        pickers.setPadding(0, dp(6), 0, dp(8));
        NumberPicker startPicker = sectionPicker(currentStart);
        NumberPicker endPicker = sectionPicker(currentEnd);
        pickers.addView(labeledPicker("开始", startPicker),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        pickers.addView(labeledPicker("结束", endPicker),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        startPicker.setOnValueChangedListener((picker, oldValue, newValue) -> {
            if (endPicker.getValue() < newValue) {
                endPicker.setValue(newValue);
            }
        });
        panel.addView(pickers);

        TextView confirm = dialogChoice("确定", true);
        confirm.setContentDescription("确定节次范围");
        confirm.setOnClickListener(v -> {
            int start = startPicker.getValue();
            int end = Math.max(start, endPicker.getValue());
            setter.set(start, end);
            dialog.dismiss();
        });
        panel.addView(confirm);
        showCompactDialog(dialog, panel, 340);
    }

    private NumberPicker sectionPicker(int value) {
        int pickerTheme = darkMode
                ? android.R.style.Theme_Material
                : android.R.style.Theme_Material_Light;
        NumberPicker picker = new NumberPicker(new ContextThemeWrapper(activity, pickerTheme));
        picker.setMinValue(1);
        picker.setMaxValue(sectionCount);
        picker.setValue(Math.max(1, Math.min(sectionCount, value)));
        picker.setWrapSelectorWheel(false);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setTextColor(inkColor);
        }
        for (int index = 0; index < picker.getChildCount(); index++) {
            View child = picker.getChildAt(index);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(inkColor);
            }
        }
        picker.setContentDescription("节次");
        return picker;
    }

    private LinearLayout labeledPicker(String label, NumberPicker picker) {
        LinearLayout group = new LinearLayout(activity);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setGravity(Gravity.CENTER);
        TextView title = text(label, mutedColor, 13, true);
        title.setGravity(Gravity.CENTER);
        group.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));
        group.addView(picker, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(124)));
        return group;
    }

    private void showWeekSelectionDialog(WeekSelection current, WeekSelectionSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = compactDialogPanel("选择周次");
        TextView help = text("选择实际上课周，可使用快捷方式后继续微调。", mutedColor, 13, false);
        help.setPadding(0, 0, 0, dp(8));
        panel.addView(help);

        WeekSelection working = current.copy();
        List<TextView> weekChips = new ArrayList<>();
        final Runnable[] refresh = new Runnable[1];

        LinearLayout quickPrimary = weightedRow();
        TextView all = weekSelectChip("全周");
        TextView odd = weekSelectChip("单周");
        TextView even = weekSelectChip("双周");
        quickPrimary.addView(all, weightedChipParams());
        quickPrimary.addView(odd, weightedChipParams());
        quickPrimary.addView(even, weightedChipParams());
        panel.addView(quickPrimary);

        LinearLayout quickSpecial = weightedRow();
        TextView project = weekSelectChip("项目周");
        TextView unknown = weekSelectChip("待确认");
        quickSpecial.addView(project, weightedChipParams());
        quickSpecial.addView(unknown, weightedChipParams());
        panel.addView(quickSpecial);

        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout weekRow = weightedRow();
            for (int column = 0; column < 5; column++) {
                int week = rowIndex * 5 + column + 1;
                TextView chip = weekSelectChip(String.valueOf(week));
                chip.setContentDescription("第" + week + "周");
                chip.setOnClickListener(v -> {
                    working.toggleWeek(week);
                    refresh[0].run();
                });
                weekChips.add(chip);
                weekRow.addView(chip, weightedChipParams());
            }
            panel.addView(weekRow);
        }

        all.setOnClickListener(v -> {
            working.selectAll();
            refresh[0].run();
        });
        odd.setOnClickListener(v -> {
            working.selectOdd();
            refresh[0].run();
        });
        even.setOnClickListener(v -> {
            working.selectEven();
            refresh[0].run();
        });
        project.setOnClickListener(v -> {
            working.selectSpecial(WeekSelection.SPECIAL_PROJECT);
            refresh[0].run();
        });
        unknown.setOnClickListener(v -> {
            working.selectSpecial(WeekSelection.SPECIAL_UNKNOWN);
            refresh[0].run();
        });
        refresh[0] = () -> {
            applyWeekChipState(all, working.isAll());
            applyWeekChipState(odd, working.isOdd());
            applyWeekChipState(even, working.isEven());
            applyWeekChipState(project, working.isSpecial(WeekSelection.SPECIAL_PROJECT));
            applyWeekChipState(unknown, working.isSpecial(WeekSelection.SPECIAL_UNKNOWN));
            for (int index = 0; index < weekChips.size(); index++) {
                applyWeekChipState(weekChips.get(index), working.contains(index + 1));
            }
        };
        refresh[0].run();

        TextView confirm = dialogChoice("确定", true);
        confirm.setOnClickListener(v -> {
            setter.set(working.copy());
            dialog.dismiss();
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        confirmParams.topMargin = dp(8);
        panel.addView(confirm, confirmParams);
        showCompactDialog(dialog, panel, 360);
    }

    private LinearLayout compactDialogPanel(String title) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        panel.setBackground(roundedBg(darkMode ? "#182235" : "#F8FBFF", 22));
        TextView heading = text(title, inkColor, 20, true);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headingParams.bottomMargin = dp(8);
        panel.addView(heading, headingParams);
        return panel;
    }

    private TextView dialogChoice(String label, boolean selected) {
        TextView item = text(label, selected ? color("#168FE4") : inkColor, 16, selected);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14), 0, dp(14), 0);
        item.setMinHeight(dp(48));
        item.setBackground(roundedBg(darkMode ? "#141E30" : "#F2F0FA", 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.bottomMargin = dp(6);
        item.setLayoutParams(params);
        return item;
    }

    private LinearLayout weightedRow() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams weightedChipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private TextView weekSelectChip(String label) {
        TextView chip = text(label, inkColor, 14, true);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(dp(48));
        return chip;
    }

    private void applyWeekChipState(TextView chip, boolean selected) {
        chip.setTextColor(selected ? Color.WHITE : inkColor);
        chip.setBackground(selected
                ? roundedBg(darkMode ? "#1F73E0" : "#172033", 14)
                : roundedStrokeBg("transparent", darkMode ? "#66758C" : "#B4BDCA", 14));
        chip.setSelected(selected);
    }

    private void showCompactDialog(Dialog dialog, View content, int maxWidthDp) {
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(darkMode ? 0.68f : 0.42f);
            window.setLayout(
                    Math.min(dp(maxWidthDp), activity.getResources().getDisplayMetrics().widthPixels - dp(32)),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            disableWindowAnimations(window);
        }
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

    private void showCourseTypeDialog(CourseType currentType, CourseTypeSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        panel.setBackground(roundedBg(darkMode ? "#182235" : "#F8FBFF", 22));
        panel.addView(text("课程类型", mutedColor, 13, false));
        for (CourseType type : CourseType.values()) {
            LinearLayout item = new LinearLayout(activity);
            item.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text(type.displayName, inkColor, 16, type == currentType);
            item.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1f));
            TextView check = text(type == currentType ? "✓" : "", inkColor, 22, true);
            check.setGravity(Gravity.CENTER);
            item.addView(check, new LinearLayout.LayoutParams(dp(32), dp(48)));
            item.setOnClickListener(v -> {
                setter.set(type);
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

    private void showCoursePlacementDialog(boolean currentBannerOnly, PlacementSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        panel.setBackground(roundedBg(darkMode ? "#182235" : "#F8FBFF", 22));
        panel.addView(text("展示位置", mutedColor, 13, false));
        String[] labels = {"固定节次", "顶部横幅（无固定节次）"};
        boolean[] values = {false, true};
        for (int index = 0; index < labels.length; index++) {
            boolean value = values[index];
            LinearLayout item = new LinearLayout(activity);
            item.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = text(labels[index], inkColor, 16, value == currentBannerOnly);
            item.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1f));
            TextView check = text(value == currentBannerOnly ? "✓" : "", inkColor, 22, true);
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

    private void updateTypeRow(View row, CourseType value) {
        if (row instanceof LinearLayout) {
            ((TextView) ((LinearLayout) row).getChildAt(1)).setText(value.displayName);
        }
    }

    private void updatePlacementRow(View row, boolean bannerOnly) {
        if (row instanceof LinearLayout) {
            ((TextView) ((LinearLayout) row).getChildAt(1))
                    .setText(bannerOnly ? "顶部横幅" : "固定节次");
        }
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
        updateSystemBarAppearance(window);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = LinearLayout.LayoutParams.MATCH_PARENT;
        attributes.height = LinearLayout.LayoutParams.MATCH_PARENT;
        window.setAttributes(attributes);
        disableWindowAnimations(window);
    }

    private void updateSystemBarAppearance(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(darkMode ? 0 : mask, mask);
            }
            return;
        }
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags = darkMode
                ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                : flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = darkMode
                    ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
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
        drawable.setStroke(dp(1), darkMode
                ? Color.argb(42, 255, 255, 255)
                : Color.argb(130, 255, 255, 255));
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

    private static final class WeekSelection {
        static final String SPECIAL_PROJECT = "project";
        static final String SPECIAL_UNKNOWN = "unknown";

        private final boolean[] selected = new boolean[MAX_SELECTABLE_WEEK + 1];
        private String special = "";

        static WeekSelection from(String text) {
            WeekSelection selection = new WeekSelection();
            WeekRule rule = WEEK_RULE_PARSER.parse(text);
            if (rule.type == WeekRule.Type.PROJECT) {
                selection.special = SPECIAL_PROJECT;
                return selection;
            }
            if (rule.type == WeekRule.Type.UNKNOWN) {
                selection.special = SPECIAL_UNKNOWN;
                return selection;
            }
            for (int week = 1; week <= MAX_SELECTABLE_WEEK; week++) {
                selection.selected[week] = rule.containsWeek(week);
            }
            if (selection.countSelected() == 0) {
                selection.special = SPECIAL_UNKNOWN;
            }
            return selection;
        }

        WeekSelection copy() {
            WeekSelection copy = new WeekSelection();
            System.arraycopy(selected, 0, copy.selected, 0, selected.length);
            copy.special = special;
            return copy;
        }

        void toggleWeek(int week) {
            if (week < 1 || week > MAX_SELECTABLE_WEEK) {
                return;
            }
            special = "";
            selected[week] = !selected[week];
            if (countSelected() == 0) {
                special = SPECIAL_UNKNOWN;
            }
        }

        void selectAll() {
            special = "";
            for (int week = 1; week <= MAX_SELECTABLE_WEEK; week++) {
                selected[week] = true;
            }
        }

        void selectOdd() {
            selectParity(true);
        }

        void selectEven() {
            selectParity(false);
        }

        private void selectParity(boolean odd) {
            special = "";
            for (int week = 1; week <= MAX_SELECTABLE_WEEK; week++) {
                selected[week] = odd ? week % 2 == 1 : week % 2 == 0;
            }
        }

        void selectSpecial(String value) {
            special = value == null ? SPECIAL_UNKNOWN : value;
            for (int week = 1; week <= MAX_SELECTABLE_WEEK; week++) {
                selected[week] = false;
            }
        }

        boolean contains(int week) {
            return week >= 1 && week <= MAX_SELECTABLE_WEEK && selected[week];
        }

        boolean isSpecial(String value) {
            return value != null && value.equals(special);
        }

        boolean isAll() {
            if (!special.isEmpty()) {
                return false;
            }
            for (int week = 1; week <= MAX_SELECTABLE_WEEK; week++) {
                if (!selected[week]) {
                    return false;
                }
            }
            return true;
        }

        boolean isOdd() {
            return matchesParity(true);
        }

        boolean isEven() {
            return matchesParity(false);
        }

        private boolean matchesParity(boolean odd) {
            if (!special.isEmpty()) {
                return false;
            }
            for (int week = 1; week <= MAX_SELECTABLE_WEEK; week++) {
                if (selected[week] != (odd ? week % 2 == 1 : week % 2 == 0)) {
                    return false;
                }
            }
            return true;
        }

        String displayText() {
            if (SPECIAL_PROJECT.equals(special)) {
                return "项目周";
            }
            if (SPECIAL_UNKNOWN.equals(special)) {
                return "周次待确认";
            }
            return numberedWeeksText();
        }

        String toCourseText() {
            if (SPECIAL_PROJECT.equals(special)) {
                return "项目周";
            }
            if (SPECIAL_UNKNOWN.equals(special)) {
                return "周次见PDF";
            }
            return numberedWeeksText();
        }

        private String numberedWeeksText() {
            if (isAll()) {
                return "1-20周";
            }
            if (isOdd()) {
                return "1-20周(单)";
            }
            if (isEven()) {
                return "1-20周(双)";
            }
            int first = firstSelected();
            int last = lastSelected();
            if (first > 0 && countSelected() == last - first + 1) {
                return first == last ? "第" + first + "周" : first + "-" + last + "周";
            }
            StringBuilder text = new StringBuilder("第");
            for (int week = 1; week <= MAX_SELECTABLE_WEEK; week++) {
                if (!selected[week]) {
                    continue;
                }
                if (text.length() > 1) {
                    text.append('、');
                }
                text.append(week);
            }
            return text.length() == 1 ? "周次待确认" : text.append('周').toString();
        }

        private int countSelected() {
            int count = 0;
            for (int week = 1; week <= MAX_SELECTABLE_WEEK; week++) {
                if (selected[week]) {
                    count++;
                }
            }
            return count;
        }

        private int firstSelected() {
            for (int week = 1; week <= MAX_SELECTABLE_WEEK; week++) {
                if (selected[week]) {
                    return week;
                }
            }
            return 0;
        }

        private int lastSelected() {
            for (int week = MAX_SELECTABLE_WEEK; week >= 1; week--) {
                if (selected[week]) {
                    return week;
                }
            }
            return 0;
        }
    }

    private interface IntSetter {
        void set(int value);
    }

    private interface SectionSetter {
        void set(int start, int end);
    }

    private interface TimeModeSetter {
        void set(CourseTimeMode value);
    }

    private interface WeekSelectionSetter {
        void set(WeekSelection value);
    }

    private interface ColorSetter {
        void set(String value);
    }

    private interface CourseTypeSetter {
        void set(CourseType value);
    }

    private interface PlacementSetter {
        void set(boolean value);
    }
}
