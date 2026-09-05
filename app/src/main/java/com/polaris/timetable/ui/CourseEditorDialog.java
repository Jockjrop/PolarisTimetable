package com.polaris.timetable.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Rect;
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

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.polaris.timetable.Course;
import com.polaris.timetable.CourseDeletionScope;
import com.polaris.timetable.R;
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

    private static final String[] COLOR_VALUES = {
            "", "#4FA4F3", "#36B889", "#E5A52D", "#956BD6", "#E86464", "#2E9EDB", "#73AD45"
    };

    // 周次存储格式:写入 Course.weeks 并由 WeekRuleParser 回读解析,必须保持语言无关,不得改为资源。
    private static final String COURSE_TEXT_PROJECT = "项目周";
    private static final String COURSE_TEXT_UNKNOWN = "周次见PDF";
    private static final String COURSE_TEXT_ALL_WEEKS = "1-20周";
    private static final String COURSE_TEXT_ODD_WEEKS = "1-20周(单)";
    private static final String COURSE_TEXT_EVEN_WEEKS = "1-20周(双)";

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

        TextView heading = text(original.name.isEmpty()
                ? activity.getString(R.string.editor_title_add)
                : activity.getString(R.string.editor_title_edit), inkColor, 24, true);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView save = text(activity.getString(R.string.editor_action_save), inkColor, 16, true);
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

        EditText name = input(activity.getString(R.string.editor_hint_name), original.name);
        EditText teacher = input(activity.getString(R.string.editor_hint_teacher), original.teacher);
        EditText location = input(activity.getString(R.string.editor_hint_location), original.location);
        keepInputVisible(scrollView, name);
        keepInputVisible(scrollView, teacher);
        keepInputVisible(scrollView, location);

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

        View weeksAction = actionRow(activity.getString(R.string.editor_marker_week),
                "#12B8A6", weekDisplayText(selectedWeeks[0]));
        weeksAction.setContentDescription(activity.getString(
                R.string.editor_cd_week_current, weekDisplayText(selectedWeeks[0])));
        weeksAction.setOnClickListener(v -> showWeekSelectionDialog(selectedWeeks[0], value -> {
            selectedWeeks[0] = value;
            updateActionRow(weeksAction, weekDisplayText(value));
            weeksAction.setContentDescription(activity.getString(
                    R.string.editor_cd_week_current, weekDisplayText(value)));
        }));
        View dayAction = actionRow(activity.getString(R.string.editor_marker_day),
                "#168FE4", dayLabel(selectedDay[0]));
        dayAction.setContentDescription(activity.getString(
                R.string.editor_cd_day_current, dayLabel(selectedDay[0])));
        dayAction.setOnClickListener(v -> showDayDialog(selectedDay[0], value -> {
            selectedDay[0] = value;
            updateActionRow(dayAction, dayLabel(value));
            dayAction.setContentDescription(activity.getString(
                    R.string.editor_cd_day_current, dayLabel(value)));
        }));
        View sectionAction = actionRow(activity.getString(R.string.editor_marker_section),
                "#FFB000", sectionLabel(selectedStart[0], selectedEnd[0]));
        sectionAction.setContentDescription(activity.getString(R.string.editor_cd_section_current,
                sectionLabel(selectedStart[0], selectedEnd[0])));
        sectionAction.setOnClickListener(v -> showSectionDialog(
                selectedStart[0], selectedEnd[0], (start, end) -> {
                    selectedStart[0] = start;
                    selectedEnd[0] = end;
                    updateActionRow(sectionAction, sectionLabel(start, end));
                    sectionAction.setContentDescription(activity.getString(
                            R.string.editor_cd_section_current, sectionLabel(start, end)));
                }));

        View startTimeAction = actionRow(activity.getString(R.string.editor_marker_start),
                "#FFB000", activity.getString(
                        R.string.editor_label_start_time, minuteText(selectedStartMinute[0])));
        View endTimeAction = actionRow(activity.getString(R.string.editor_marker_end),
                "#FFB000", activity.getString(
                        R.string.editor_label_end_time, minuteText(selectedEndMinute[0])));
        startTimeAction.setContentDescription(activity.getString(
                R.string.editor_cd_start_time_current, minuteText(selectedStartMinute[0])));
        endTimeAction.setContentDescription(activity.getString(
                R.string.editor_cd_end_time_current, minuteText(selectedEndMinute[0])));
        startTimeAction.setOnClickListener(v -> showTimeDialog(
                activity.getString(R.string.editor_title_start_time),
                selectedStartMinute[0], value -> {
                    int previousDuration = Math.max(15,
                            selectedEndMinute[0] - selectedStartMinute[0]);
                    selectedStartMinute[0] = value;
                    if (selectedEndMinute[0] <= value) {
                        selectedEndMinute[0] = Math.min(23 * 60 + 59,
                                value + previousDuration);
                    }
                    updateActionRow(startTimeAction, activity.getString(
                            R.string.editor_label_start_time, minuteText(selectedStartMinute[0])));
                    updateActionRow(endTimeAction, activity.getString(
                            R.string.editor_label_end_time, minuteText(selectedEndMinute[0])));
                    startTimeAction.setContentDescription(activity.getString(
                            R.string.editor_cd_start_time_current, minuteText(selectedStartMinute[0])));
                    endTimeAction.setContentDescription(activity.getString(
                            R.string.editor_cd_end_time_current, minuteText(selectedEndMinute[0])));
                }));
        endTimeAction.setOnClickListener(v -> showTimeDialog(
                activity.getString(R.string.editor_title_end_time),
                selectedEndMinute[0], value -> {
                    if (value <= selectedStartMinute[0]) {
                        Toast.makeText(activity,
                                activity.getString(R.string.editor_error_end_before_start),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedEndMinute[0] = value;
                    updateActionRow(endTimeAction, activity.getString(
                            R.string.editor_label_end_time, minuteText(selectedEndMinute[0])));
                    endTimeAction.setContentDescription(activity.getString(
                            R.string.editor_cd_end_time_current, minuteText(selectedEndMinute[0])));
                }));

        LinearLayout clockTimeRows = new LinearLayout(activity);
        clockTimeRows.setOrientation(LinearLayout.VERTICAL);
        clockTimeRows.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        clockTimeRows.addView(startTimeAction);
        clockTimeRows.addView(endTimeAction);
        View timeModeAction = actionRow(activity.getString(R.string.editor_marker_time_mode),
                "#FFB000", timeModeLabel(selectedTimeMode[0]));
        timeModeAction.setContentDescription(activity.getString(
                R.string.editor_cd_time_mode_current, timeModeLabel(selectedTimeMode[0])));
        timeModeAction.setOnClickListener(v -> showTimeModeDialog(selectedTimeMode[0], value -> {
            selectedTimeMode[0] = value;
            updateActionRow(timeModeAction, timeModeLabel(value));
            timeModeAction.setContentDescription(activity.getString(
                    R.string.editor_cd_time_mode_current, timeModeLabel(value)));
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
        View placementAction = actionRow(activity.getString(R.string.editor_marker_placement),
                "#FFB000", placementLabel(bannerOnly[0]));
        View typeAction = actionRow(activity.getString(R.string.editor_marker_type),
                "#168FE4", selectedType[0].displayName);
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

        panel.addView(sectionTitle(activity.getString(R.string.editor_section_info)));
        panel.addView(group(
                editorRow("▣", "#10BFAE", name),
                chipRow(courseNameQuickValues(), name),
                typeAction,
                colorAction));
        panel.addView(sectionTitle(activity.getString(R.string.editor_section_time)));
        panel.addView(group(
                weeksAction,
                placementAction,
                timeSelectionRows));
        panel.addView(group(
                editorRow("♙", "#168FE4", teacher),
                editorRow("▯", "#F0334B", location)));
        if (courses.contains(original)) {
            TextView delete = text(activity.getString(R.string.editor_action_delete),
                    color("#E5484D"), 16, true);
            delete.setGravity(Gravity.CENTER);
            delete.setMinHeight(dp(50));
            delete.setBackground(roundedStrokeBg("transparent", darkMode ? "#A94A55" : "#D96A70", 16));
            delete.setContentDescription(activity.getString(R.string.editor_action_delete));
            delete.setOnClickListener(v -> showDeleteScopeDialog(dialog));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
                Toast.makeText(activity,
                        activity.getString(R.string.editor_error_name_required),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            boolean validSections = nextTimeMode == CourseTimeMode.SECTION
                    && nextDay >= 0 && nextStart >= 1 && nextEnd >= nextStart;
            boolean validClock = nextTimeMode == CourseTimeMode.CLOCK
                    && nextDay >= 0 && nextStartMinute >= 0
                    && nextStartMinute < nextEndMinute && nextEndMinute <= 24 * 60;
            if (!bannerOnly[0] && !validSections && !validClock) {
                Toast.makeText(activity,
                        activity.getString(R.string.editor_error_name_and_time),
                        Toast.LENGTH_SHORT).show();
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
        installImeAvoidance(dialog, page, scrollView);
        dialog.show();
        configureFullScreenWindow(dialog.getWindow());
        ViewCompat.requestApplyInsets(page);
    }

    private void showDeleteScopeDialog(Dialog editorDialog) {
        Dialog scopeDialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(14));
        panel.setBackground(roundedBg(darkMode ? "#182235" : "#F8FBFF", 22));

        TextView heading = text(activity.getString(R.string.editor_title_delete), inkColor, 20, true);
        panel.addView(heading);
        TextView message = text(activity.getString(R.string.editor_delete_message),
                mutedColor, 14, false);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(5);
        messageParams.bottomMargin = dp(10);
        panel.addView(message, messageParams);

        panel.addView(deleteScopeChoice(
                activity.getString(R.string.editor_delete_scope_week_title),
                activity.getString(R.string.editor_delete_scope_week_desc),
                () -> dispatchDelete(scopeDialog, editorDialog, CourseDeletionScope.CURRENT_WEEK)));
        panel.addView(deleteScopeChoice(
                activity.getString(R.string.editor_delete_scope_meeting_title),
                activity.getString(R.string.editor_delete_scope_meeting_desc),
                () -> dispatchDelete(scopeDialog, editorDialog, CourseDeletionScope.CURRENT_MEETING)));
        panel.addView(deleteScopeChoice(
                activity.getString(R.string.editor_delete_scope_course_title),
                activity.getString(R.string.editor_delete_scope_course_desc),
                () -> dispatchDelete(scopeDialog, editorDialog, CourseDeletionScope.ALL_MEETINGS)));

        TextView cancel = text(activity.getString(R.string.editor_action_cancel), inkColor, 16, true);
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
        editText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        editText.setIncludeFontPadding(false);
        editText.setPadding(0, 0, 0, 0);
        editText.setBackgroundColor(Color.TRANSPARENT);
        return editText;
    }

    /** 输入框获得焦点后，在键盘布局稳定时再次请求滚动到可见区域。 */
    private void keepInputVisible(ScrollView scrollView, EditText input) {
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                requestInputVisible(scrollView, input, 220L);
            }
        });
    }

    private void requestInputVisible(ScrollView scrollView, View input, long delayMillis) {
        scrollView.postDelayed(() -> {
            if (!input.isFocused() || input.getWidth() <= 0 || input.getHeight() <= 0) {
                return;
            }
            Rect target = new Rect(0, -dp(16), input.getWidth(), input.getHeight() + dp(24));
            input.requestRectangleOnScreen(target, true);
        }, delayMillis);
    }

    /**
     * 全屏课程编辑页在 edge-to-edge 与部分输入法组合下不会可靠触发系统 resize，
     * 因此用 IME inset 缩小页面实际视口，并通过输入框的父链重新定位当前焦点。
     */
    private void installImeAvoidance(Dialog dialog, View page, ScrollView scrollView) {
        // 缩小实际内容视口，确保 ScrollView 的自动焦点滚动以键盘上沿为边界。
        ViewCompat.setOnApplyWindowInsetsListener(page, (view, insets) -> {
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navigationBottom = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()).bottom;
            int bottomPadding = Math.max(imeBottom, navigationBottom);
            if (page.getPaddingBottom() != bottomPadding) {
                page.setPadding(page.getPaddingLeft(), page.getPaddingTop(),
                        page.getPaddingRight(), bottomPadding);
            }
            if (imeVisible) {
                View focused = dialog.getCurrentFocus();
                if (focused instanceof EditText) {
                    requestInputVisible(scrollView, focused, 40L);
                }
            }
            return insets;
        });
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
        String[] labels = {
                activity.getString(R.string.weekday_mon),
                activity.getString(R.string.weekday_tue),
                activity.getString(R.string.weekday_wed),
                activity.getString(R.string.weekday_thu),
                activity.getString(R.string.weekday_fri),
                activity.getString(R.string.weekday_sat),
                activity.getString(R.string.weekday_sun)};
        return labels[Math.max(0, Math.min(labels.length - 1, day))];
    }

    private String sectionLabel(int start, int end) {
        return activity.getString(R.string.editor_label_sections, start, end);
    }

    private String timeModeLabel(CourseTimeMode mode) {
        return activity.getString(mode == CourseTimeMode.CLOCK
                ? R.string.editor_mode_clock : R.string.editor_mode_section);
    }

    private String placementLabel(boolean bannerOnly) {
        return activity.getString(bannerOnly
                ? R.string.editor_placement_banner : R.string.editor_placement_fixed);
    }

    private String minuteText(int minuteOfDay) {
        return CourseTimeResolver.formatMinuteOfDay(minuteOfDay);
    }

    private void showTimeModeDialog(CourseTimeMode current, TimeModeSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = compactDialogPanel(
                activity.getString(R.string.editor_title_time_mode));
        TextView section = dialogChoice(activity.getString(R.string.editor_mode_section),
                current == CourseTimeMode.SECTION);
        section.setContentDescription(activity.getString(R.string.editor_cd_mode_section));
        section.setOnClickListener(v -> {
            setter.set(CourseTimeMode.SECTION);
            dialog.dismiss();
        });
        TextView clock = dialogChoice(activity.getString(R.string.editor_mode_clock),
                current == CourseTimeMode.CLOCK);
        clock.setContentDescription(activity.getString(R.string.editor_cd_mode_clock));
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

        TextView confirm = dialogChoice(activity.getString(R.string.editor_action_confirm), true);
        confirm.setContentDescription(activity.getString(
                R.string.editor_cd_confirm_with_title, title));
        confirm.setOnClickListener(v -> {
            setter.set(picker.getHour() * 60 + picker.getMinute());
            dialog.dismiss();
        });
        panel.addView(confirm);
        showCompactDialog(dialog, panel, 430);
    }

    private void showDayDialog(int currentDay, IntSetter setter) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = compactDialogPanel(
                activity.getString(R.string.editor_title_day));
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
        LinearLayout panel = compactDialogPanel(
                activity.getString(R.string.editor_title_section));

        LinearLayout pickers = new LinearLayout(activity);
        pickers.setOrientation(LinearLayout.HORIZONTAL);
        pickers.setGravity(Gravity.CENTER);
        pickers.setPadding(0, dp(6), 0, dp(8));
        NumberPicker startPicker = sectionPicker(currentStart);
        NumberPicker endPicker = sectionPicker(currentEnd);
        pickers.addView(labeledPicker(activity.getString(R.string.editor_picker_start), startPicker),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        pickers.addView(labeledPicker(activity.getString(R.string.editor_picker_end), endPicker),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        startPicker.setOnValueChangedListener((picker, oldValue, newValue) -> {
            if (endPicker.getValue() < newValue) {
                endPicker.setValue(newValue);
            }
        });
        panel.addView(pickers);

        TextView confirm = dialogChoice(activity.getString(R.string.editor_action_confirm), true);
        confirm.setContentDescription(activity.getString(R.string.editor_cd_confirm_section));
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
        picker.setContentDescription(activity.getString(R.string.editor_cd_section_picker));
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
        LinearLayout panel = compactDialogPanel(
                activity.getString(R.string.editor_title_week));
        TextView help = text(activity.getString(R.string.editor_week_help), mutedColor, 13, false);
        help.setPadding(0, 0, 0, dp(8));
        panel.addView(help);

        WeekSelection working = current.copy();
        List<TextView> weekChips = new ArrayList<>();
        final Runnable[] refresh = new Runnable[1];

        LinearLayout quickPrimary = weightedRow();
        TextView all = weekSelectChip(activity.getString(R.string.editor_week_all));
        TextView odd = weekSelectChip(activity.getString(R.string.editor_week_odd));
        TextView even = weekSelectChip(activity.getString(R.string.editor_week_even));
        quickPrimary.addView(all, weightedChipParams());
        quickPrimary.addView(odd, weightedChipParams());
        quickPrimary.addView(even, weightedChipParams());
        panel.addView(quickPrimary);

        LinearLayout quickSpecial = weightedRow();
        TextView project = weekSelectChip(activity.getString(R.string.editor_week_project));
        TextView unknown = weekSelectChip(activity.getString(R.string.editor_week_unknown));
        quickSpecial.addView(project, weightedChipParams());
        quickSpecial.addView(unknown, weightedChipParams());
        panel.addView(quickSpecial);

        for (int rowIndex = 0; rowIndex < 4; rowIndex++) {
            LinearLayout weekRow = weightedRow();
            for (int column = 0; column < 5; column++) {
                int week = rowIndex * 5 + column + 1;
                TextView chip = weekSelectChip(String.valueOf(week));
                chip.setContentDescription(activity.getString(R.string.editor_cd_week_n, week));
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

        TextView confirm = dialogChoice(activity.getString(R.string.editor_action_confirm), true);
        confirm.setOnClickListener(v -> {
            setter.set(working.copy());
            dialog.dismiss();
        });
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
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
        panel.addView(text(activity.getString(R.string.editor_title_color), mutedColor, 13, false));
        String normalized = normalizeColor(currentColor);
        String[] colorLabels = colorLabels();
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            String value = COLOR_VALUES[i];
            LinearLayout item = new LinearLayout(activity);
            item.setGravity(Gravity.CENTER_VERTICAL);
            TextView dot = text("●", color(editorColor(value)), 22, true);
            dot.setGravity(Gravity.CENTER);
            item.addView(dot, new LinearLayout.LayoutParams(dp(38), dp(48)));
            TextView label = text(colorLabels[i], inkColor, 16, value.equals(normalized));
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
        panel.addView(text(activity.getString(R.string.editor_title_type), mutedColor, 13, false));
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
        panel.addView(text(activity.getString(R.string.editor_title_placement), mutedColor, 13, false));
        String[] labels = {
                activity.getString(R.string.editor_placement_fixed),
                activity.getString(R.string.editor_placement_banner_no_section)};
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
            ((TextView) ((LinearLayout) row).getChildAt(1)).setText(placementLabel(bannerOnly));
        }
    }

    private void configureFullScreenWindow(Window window) {
        if (window == null) {
            return;
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false);
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
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
        // 经 ViewCompat 跨版本读取真实状态栏 inset，避免反射 status_bar_height。
        androidx.core.view.WindowInsetsCompat insets =
                androidx.core.view.ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
        if (insets != null) {
            int top = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.statusBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout()).top;
            if (top > 0) {
                return top;
            }
        }
        return dp(24);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private int color(String value) {
        return Color.parseColor(value);
    }

    private String defaultWeeks() {
        return COURSE_TEXT_ALL_WEEKS;
    }

    /** 周次操作行的展示文案:特殊周次用资源,连续周次沿用存储格式。 */
    private String weekDisplayText(WeekSelection selection) {
        return selection.displayText(
                activity.getString(R.string.editor_week_project),
                activity.getString(R.string.editor_week_unknown_full));
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

    private String[] colorLabels() {
        return new String[]{
                activity.getString(R.string.editor_color_auto),
                activity.getString(R.string.editor_color_salt_blue),
                activity.getString(R.string.editor_color_mint_green),
                activity.getString(R.string.editor_color_sunflower_yellow),
                activity.getString(R.string.editor_color_lavender_purple),
                activity.getString(R.string.editor_color_coral_red),
                activity.getString(R.string.editor_color_sky_blue),
                activity.getString(R.string.editor_color_lime_green)};
    }

    private String colorLabel(String value) {
        String normalized = normalizeColor(value);
        String[] labels = colorLabels();
        for (int i = 0; i < COLOR_VALUES.length; i++) {
            if (COLOR_VALUES[i].equals(normalized)) {
                return labels[i];
            }
        }
        return labels[0];
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

        String displayText(String projectLabel, String unknownLabel) {
            if (SPECIAL_PROJECT.equals(special)) {
                return projectLabel;
            }
            if (SPECIAL_UNKNOWN.equals(special)) {
                return unknownLabel;
            }
            return numberedWeeksText();
        }

        String toCourseText() {
            if (SPECIAL_PROJECT.equals(special)) {
                return COURSE_TEXT_PROJECT;
            }
            if (SPECIAL_UNKNOWN.equals(special)) {
                return COURSE_TEXT_UNKNOWN;
            }
            return numberedWeeksText();
        }

        /** 连续周次文本同时充当展示与存储格式(由 WeekRuleParser 解析),文案改动需同步解析器。 */
        private String numberedWeeksText() {
            if (isAll()) {
                return COURSE_TEXT_ALL_WEEKS;
            }
            if (isOdd()) {
                return COURSE_TEXT_ODD_WEEKS;
            }
            if (isEven()) {
                return COURSE_TEXT_EVEN_WEEKS;
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
