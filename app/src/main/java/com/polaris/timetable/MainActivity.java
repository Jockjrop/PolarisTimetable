package com.polaris.timetable;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.polaris.timetable.importer.PdfImportCoordinator;
import com.polaris.timetable.model.ParseError;
import com.polaris.timetable.model.ParseResult;
import com.polaris.timetable.parser.SchoolParserModel;
import com.polaris.timetable.sharing.ScheduleShareCodec;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.ui.BackdropBlurView;
import com.polaris.timetable.ui.BackgroundImageLoader;
import com.polaris.timetable.ui.CourseDetailDialog;
import com.polaris.timetable.ui.CourseEditorDialog;
import com.polaris.timetable.ui.ScheduleBoardView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private interface BooleanSetter {
        void set(boolean value);
    }

    private interface StringSetter {
        void set(String value);
    }

    private static final int PICK_PDF = 1001;
    private static final int PICK_BACKGROUND_IMAGE = 1002;
    private static final String TAG_SETTINGS_GROUP = "settings_group";
    private static final String TAG_SECTION_HEADER = "section_header";
    private static final String TAG_SETTING_LABEL = "setting_label";
    private static final String TAG_SETTING_VALUE = "setting_value";
    private static final String TAG_SETTINGS_HEADER = "settings_header";
    private static final String TAG_SETTINGS_SCROLL = "settings_scroll";
    private final List<Course> courses = new ArrayList<>();
    private ScheduleRepository scheduleRepository;
    private PdfImportCoordinator importCoordinator;
    private ScheduleBoardView scheduleBoard;
    private FrameLayout contentHost;
    private FrameLayout rootView;
    private FrameLayout topPanelContainer;
    private LinearLayout topPanel;
    private View topPanelGlassLayer;
    private LinearLayout bottomNavView;
    private ScrollView myPage;
    private FrameLayout settingsPage;
    private View emptyScheduleView;
    private TextView scheduleNav;
    private TextView myNav;
    private TextView title;
    private TextView subtitle;
    private int currentWeek = 18;
    private int visibleDayCount = 7;
    private int activeTab = 0;
    private String gradeAndSchool = "2025级 · Polaris大学";
    private String scheduleName = "默认课表";
    private String classTimeConfig = "08:00 开始";
    private String firstClassStartTime = "08:00";
    private int classDurationMinutes = 50;
    private int classBreakMinutes = 10;
    private int classBigBreakMinutes = 30;
    private String afternoonStartTime = "14:30";
    private String lateAfternoonStartTime = "16:35";
    private String firstWeekDay = "2026/3/3";
    private int courseSectionCount = 11;
    private int semesterWeeks = 20;
    private boolean showSaturday = false;
    private boolean showSunday = false;
    private boolean showOutOfWeekCourses = true;
    private int courseCellHeight = 76;
    private int courseCornerRadius = 9;
    private int courseBlockOpacity = 100;
    private int timetableHeaderOpacity = 78;
    private int bottomNavOpacity = 86;
    private String bottomNavShape = "矩形";
    private int bottomNavHeight = 72;
    private int bottomNavRectCornerRadius = 58;
    private int bottomNavSplitCornerRadius = 58;
    private int bottomNavSideCornerRadius = 58;
    private int bottomNavSideMargin = 10;
    private boolean shellBarsBlurEnabled = true;
    private String timetableBackground = "清爽蓝";
    private String backgroundImageUri = "";
    private String darkMode = "跟随系统";
    private String currentTitle = "Polaris课程表";
    private String currentSubtitle = "2026/7/1";
    private String activeSettingsTitle = "";
    private String previousSettingsTitle = "";
    private String activeScheduleId = "default";
    private String courseManageSort = "time";
    private boolean courseManageDescending = false;
    private boolean suppressSettingsPageAnimation = false;
    private SchoolParserModel selectedParserModel;
    private boolean collapseXautMiddleSections = false;
    private TextView parserModelValue;
    private LinearLayout courseManageListContainer;
    private LinearLayout courseManageSortContainer;
    private View backgroundSettingRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scheduleRepository = new ScheduleRepository(this);
        importCoordinator = new PdfImportCoordinator(this);
        activeScheduleId = scheduleRepository.activeScheduleId();
        darkMode = scheduleRepository.loadGlobalDarkMode();
        applyConfig(scheduleRepository.loadConfig(activeScheduleId));
        currentWeek = currentWeekFromDate();
        courses.addAll(scheduleRepository.loadCourses(activeScheduleId));
        buildLayout();
        renderSchedule();
        Uri launchUri = getIntent() == null ? null : getIntent().getData();
        if (ScheduleShareCodec.isImportLink(launchUri)) {
            startSharedScheduleImportFlow(launchUri);
        } else if (launchUri != null) {
            startPdfImportFlow(launchUri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderSchedule();
        if (scheduleBoard != null) {
            scheduleBoard.post(this::renderSchedule);
        }
    }

    @Override
    public void onBackPressed() {
        if (settingsPage != null && settingsPage.getVisibility() == View.VISIBLE) {
            closeSettingsPage();
            return;
        }
        super.onBackPressed();
    }

    private void buildLayout() {
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;

        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(backgroundColor());

        topPanel = new LinearLayout(this);
        topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setPadding(dp(12), dp(10), dp(12), dp(10));
        topPanel.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout headline = new LinearLayout(this);
        headline.setOrientation(LinearLayout.HORIZONTAL);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        topPanel.addView(headline);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        headline.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        title = new TextView(this);
        title.setText(headerTitle());
        title.setTextColor(inkColor());
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(tablet ? 28 : 25);
        title.setSingleLine(true);
        heading.addView(title);

        subtitle = new TextView(this);
        subtitle.setText(headerSubtitle());
        subtitle.setTextColor(mutedColor());
        subtitle.setTextSize(15);
        subtitle.setSingleLine(false);
        heading.addView(subtitle);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        headline.addView(actions);
        actions.addView(transparentTopButton("⋯", v -> {
            v.animate().rotationBy(90f).scaleX(0.88f).scaleY(0.88f).setDuration(90)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                    .start();
            showActionPanel(v);
        }));

        scheduleBoard = new ScheduleBoardView(this);
        scheduleBoard.setOnCourseClickListener(course -> new CourseDetailDialog(this, isDarkModeActive(), dialogBlurSource())
                .show(course, this::showCourseEditor));
        scheduleBoard.setOnCourseLongClickListener(this::showCourseEditor);
        scheduleBoard.setOnSlotLongClickListener((day, section) ->
                showCourseEditor(new Course(day, section, section, "", defaultWeeks(), "", "", "")));
        scheduleBoard.setOnWeekSwipeListener(this::changeWeek);
        scheduleBoard.setCurrentWeek(currentWeek);
        scheduleBoard.setVisibleDays(showSaturday, showSunday);
        scheduleBoard.setSectionCount(courseSectionCount);
        scheduleBoard.setFirstWeekStartMillis(firstWeekStartMillis());
        scheduleBoard.setClassTimeSettings(firstClassStartTime, classDurationMinutes, classBreakMinutes,
                classBigBreakMinutes, afternoonStartTime, lateAfternoonStartTime, classTimeConfig);
        scheduleBoard.setCollapseMiddleSections(isXautCollapseEnabled());
        scheduleBoard.setDarkMode(isDarkModeActive());
        scheduleBoard.setShowOutOfWeekCourses(showOutOfWeekCourses);
        scheduleBoard.setCourseMetrics(courseCellHeight, courseCornerRadius);
        scheduleBoard.setCourseBlockOpacity(courseBlockOpacity);
        scheduleBoard.setOverlayInsets(statusBarHeight() + dp(tablet ? 100 : 96), bottomContentInset());
        scheduleBoard.setBackgroundImageUri(backgroundImageUri);
        scheduleBoard.setCourses(courses);
        myPage = buildMyPage();
        settingsPage = new FrameLayout(this);
        settingsPage.setVisibility(View.GONE);
        contentHost = new FrameLayout(this);
        contentHost.addView(scheduleBoard, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        emptyScheduleView = buildEmptyScheduleView();
        contentHost.addView(emptyScheduleView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        contentHost.addView(myPage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        contentHost.addView(settingsPage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        rootView.addView(contentHost, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        topParams.setMargins(dp(tablet ? 16 : 10), statusBarHeight() + dp(8),
                dp(tablet ? 16 : 10), 0);
        topPanelContainer = new FrameLayout(this);
        topPanelGlassLayer = glassLayer(liquidGlassBg(timetableHeaderOpacity), 24);
        topPanelContainer.addView(topPanelGlassLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0));
        topPanelContainer.addView(topPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        topPanel.addOnLayoutChangeListener((view, left, top, right, bottom,
                                            oldLeft, oldTop, oldRight, oldBottom) ->
                syncTopGlassHeight(bottom - top));
        rootView.addView(topPanelContainer, topParams);

        bottomNavView = bottomNav();
        rootView.addView(bottomNavView, bottomNavLayoutParams());
        setContentView(rootView);
        switchTab(activeTab);
        scheduleBoard.post(this::renderSchedule);
    }

    private void openPdfPicker() {
        if (courses.isEmpty() && selectedParserModel == null) {
            Toast.makeText(this, "请先选择学校解析模型", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, PICK_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == PICK_PDF) {
            startPdfImportFlow(data.getData());
        } else if (requestCode == PICK_BACKGROUND_IMAGE) {
            previewBackgroundImage(data.getData());
        }
    }

    private void startPdfImportFlow(Uri uri) {
        if (courses.isEmpty()) {
            showImportNameDialog(uri);
            return;
        }
        showImportOverwriteDialog(uri);
    }

    private void showImportOverwriteDialog(Uri uri) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("覆盖当前课表？");
        final SchoolParserModel[] importParserModel = {selectedParserModel};
        TextView message = new TextView(this);
        message.setText("当前课表已有课程，继续导入会用 PDF 识别结果覆盖当前课程。请先选择本次导入使用的学校解析模型。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, dp(8));
        panel.addView(message, messageParams);
        final TextView[] parserChoice = new TextView[1];
        parserChoice[0] = dialogAction(importParserModel[0] == null
                ? "选择学校解析模型"
                : "学校：" + importParserModel[0].label, v -> {
            Dialog chooser = new Dialog(this);
            LinearLayout chooserPanel = dialogPanel("选择学校");
            for (SchoolParserModel model : SchoolParserModel.values()) {
                chooserPanel.addView(dialogAction(model.label, item -> {
                    importParserModel[0] = model;
                    selectedParserModel = model;
                    applySchoolTimeDefaults(model);
                    parserChoice[0].setText("学校：" + model.label);
                    chooser.dismiss();
                }));
            }
            chooser.setContentView(glassDialogContent(chooserPanel, 22));
            chooser.show();
            transparentDialog(chooser);
        });
        panel.addView(parserChoice[0]);
        TextView cover = dialogAction("确认覆盖", v -> {
            if (importParserModel[0] == null) {
                Toast.makeText(this, "请先选择学校解析模型", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            loadPdf(uri, scheduleName, importParserModel[0]);
        });
        TextView create = dialogAction("新建课表导入", v -> {
            if (importParserModel[0] == null) {
                Toast.makeText(this, "新课表必须先选择学校解析模型", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showImportNameDialog(uri, true, importParserModel[0]);
        });
        TextView cancel = dialogAction("取消", v -> {
            dialog.dismiss();
        });
        panel.addView(cover);
        panel.addView(create);
        panel.addView(cancel);
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private TextView dialogAction(String text, View.OnClickListener listener) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setGravity(Gravity.CENTER);
        item.setTextSize(17);
        item.setTypeface(Typeface.DEFAULT_BOLD);
        item.setTextColor(inkColor());
        item.setBackground(roundedBg(cardColorHex(), 14));
        item.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(6), 0, dp(6));
        item.setLayoutParams(params);
        return item;
    }

    private void showImportNameDialog(Uri uri) {
        showImportNameDialog(uri, false, selectedParserModel);
    }

    private void showImportNameDialog(Uri uri, boolean createNewSchedule, SchoolParserModel parserModel) {
        if (parserModel == null) {
            Toast.makeText(this, "新课表必须先选择学校解析模型", Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("设置课表名称");
        EditText nameInput = input("课表名称", createNewSchedule ? nextScheduleName()
                : scheduleName.length() == 0 ? nextScheduleName() : scheduleName);
        panel.addView(nameInput);
        panel.addView(pageSaveButton(() -> {
            String name = nameInput.getText().toString().trim();
            if (name.length() == 0) {
                Toast.makeText(this, "课表名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            if (createNewSchedule) {
                ScheduleRepository.ScheduleEntry entry = scheduleRepository.createSchedule(name);
                copyGlobalAppearanceToSchedule(entry.id);
                switchSchedule(entry.id);
            } else {
                scheduleName = name;
                saveConfig();
                updateHeader();
                refreshMyPage();
            }
            loadPdf(uri, name, parserModel);
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private String nextScheduleName() {
        int index = scheduleRepository.loadSchedules().size() + 1;
        return "新课表" + index;
    }

    private void loadPdf(Uri uri, String targetScheduleName, SchoolParserModel parserModel) {
        importCoordinator.importPdf(uri, parserModel, new PdfImportCoordinator.Callback() {
            @Override
            public void onImportStarted(String displayName) {
                setHeader(displayName, "正在解析课表...");
            }

            @Override
            public void onImportParsed(ParseResult result) {
                if (!result.courses.isEmpty()) {
                    courses.clear();
                    courses.addAll(result.courses);
                    scheduleName = targetScheduleName;
                    selectedParserModel = parserModel;
                    applySchoolTimeDefaults(parserModel);
                    semesterWeeks = inferSemesterWeeks(result.courses);
                    updateVisibleDayCount();
                    scheduleRepository.saveCourses(
                            activeScheduleId, courses, result.structuredCourses);
                    saveConfig();
                    refreshMyPage();
                }
                updateHeader();
                renderSchedule();
                updateEmptyScheduleView();
                if (!result.success || !result.errors.isEmpty()) {
                    Toast.makeText(MainActivity.this, parseToast(result), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onImportFailed(Exception exception) {
                setHeader(currentTitle, "导入失败");
                Toast.makeText(MainActivity.this, "PDF解析失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private String parseSubtitle(ParseResult result) {
        if (result.courses.isEmpty()) {
            String reason = firstParseMessage(result);
            return reason.length() == 0 ? "未识别到课程，请确认PDF是教务系统课表" : reason;
        }
        if (result.errors.isEmpty()) {
            return "已导入 " + result.courses.size() + " 门课程 · " + result.pageCount + " 页";
        }
        return "已导入 " + result.courses.size() + " 门课程 · 有 " + result.errors.size() + " 条提示";
    }

    private String parseToast(ParseResult result) {
        String message = firstParseMessage(result);
        if (message.length() > 0) {
            return message;
        }
        if (!result.success) {
            return "解析失败，请确认PDF是文字型教务课表";
        }
        return "部分字段未识别完整，可继续查看课表";
    }

    private String firstParseMessage(ParseResult result) {
        if (result.errors.isEmpty()) {
            return "";
        }
        ParseError error = result.errors.get(0);
        return error.message.length() == 0 ? error.code.name() : error.message;
    }

    private void setHeader(String titleText, String subtitleText) {
        currentTitle = titleText;
        currentSubtitle = subtitleText;
        if (title != null) {
            title.setText(titleText);
        }
        if (subtitle != null) {
            subtitle.setText(subtitleText);
        }
    }

    private void renderSchedule() {
        if (scheduleBoard != null) {
            boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
            scheduleBoard.setCurrentWeek(currentWeek);
            scheduleBoard.setVisibleDays(showSaturday, showSunday);
            scheduleBoard.setSectionCount(courseSectionCount);
            scheduleBoard.setFirstWeekStartMillis(firstWeekStartMillis());
            scheduleBoard.setClassTimeSettings(firstClassStartTime, classDurationMinutes, classBreakMinutes,
                    classBigBreakMinutes, afternoonStartTime, lateAfternoonStartTime, classTimeConfig);
            scheduleBoard.setCollapseMiddleSections(isXautCollapseEnabled());
            scheduleBoard.setDarkMode(isDarkModeActive());
            scheduleBoard.setShowOutOfWeekCourses(showOutOfWeekCourses);
            scheduleBoard.setCourseMetrics(courseCellHeight, courseCornerRadius);
            scheduleBoard.setCourseBlockOpacity(courseBlockOpacity);
            scheduleBoard.setOverlayInsets(statusBarHeight() + dp(tablet ? 100 : 96), bottomContentInset());
            scheduleBoard.setBackgroundImageUri(backgroundImageUri);
            scheduleBoard.setCourses(courses);
        }
        updateEmptyScheduleView();
    }

    private View buildEmptyScheduleView() {
        FrameLayout layer = new FrameLayout(this);
        layer.setPadding(dp(20), statusBarHeight() + dp(70), dp(20), bottomContentInset() + dp(48));
        layer.setBackgroundColor(backgroundColor());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(24), dp(28), dp(24), dp(28));
        card.setBackground(roundedBg(isDarkModeActive() ? "#182235" : "#F8FBFF", 18));

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(color("#1F73E0"));
        plus.setTextSize(34);
        plus.setTypeface(Typeface.DEFAULT_BOLD);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(roundedBg(isDarkModeActive() ? "#22304A" : "#DDF4FB", 18));
        plus.setOnClickListener(v -> openPdfPicker());
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dp(80), dp(72));
        plusParams.bottomMargin = dp(18);
        card.addView(plus, plusParams);

        TextView titleView = new TextView(this);
        titleView.setText("还没有课程");
        titleView.setTextColor(inkColor());
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView);

        TextView message = new TextView(this);
        message.setText("点击导入 PDF，可以预览从教务系统课表解析到周课表的完整流程。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(10), 0, 0);
        card.addView(message, messageParams);

        parserModelValue = new TextView(this);
        parserModelValue.setText(parserModelText());
        parserModelValue.setTextColor(selectedParserModel == null ? mutedColor() : inkColor());
        parserModelValue.setTextSize(15);
        parserModelValue.setTypeface(Typeface.DEFAULT_BOLD);
        parserModelValue.setGravity(Gravity.CENTER);
        parserModelValue.setBackground(roundedBg(cardColorHex(), 14));
        parserModelValue.setOnClickListener(v -> showParserModelDialog());
        LinearLayout.LayoutParams parserParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        parserParams.setMargins(0, dp(16), 0, 0);
        card.addView(parserModelValue, parserParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(360), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cardParams.topMargin = dp(120);
        layer.addView(card, cardParams);
        return layer;
    }

    private void showParserModelDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("选择学校");
        TextView message = new TextView(this);
        message.setText("新课表导入前必须选择学校，解析器会按学校课表格式读取课程，并使用该学校固定上课时间。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(dp(4), 1f);
        panel.addView(message);
        for (SchoolParserModel model : SchoolParserModel.values()) {
            panel.addView(dialogAction(model.label, v -> {
                selectedParserModel = model;
                applySchoolTimeDefaults(model);
                saveConfig();
                renderSchedule();
                if (parserModelValue != null) {
                    parserModelValue.setText(parserModelText());
                    parserModelValue.setTextColor(inkColor());
                }
                dialog.dismiss();
            }));
        }
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private String parserModelText() {
        return selectedParserModel == null ? "选择学校解析模型" : selectedParserModel.label;
    }

    private void applySchoolTimeDefaults(SchoolParserModel model) {
        if (model == null) {
            return;
        }
        firstClassStartTime = normalizedTimeText(model.defaultFirstClassStartTime);
        classDurationMinutes = Math.max(20, Math.min(120, model.defaultClassDurationMinutes));
        classBreakMinutes = Math.max(0, Math.min(60, model.defaultClassBreakMinutes));
        classBigBreakMinutes = Math.max(0, Math.min(120, model.defaultClassBigBreakMinutes));
        afternoonStartTime = normalizedTimeText(model.defaultAfternoonStartTime);
        lateAfternoonStartTime = normalizedTimeText(model.defaultLateAfternoonStartTime);
        classTimeConfig = model.defaultClassTimeConfig;
        int fixedSectionCount = model.defaultSectionCount();
        if (fixedSectionCount > 0) {
            courseSectionCount = fixedSectionCount;
        }
        if (model == SchoolParserModel.XAUT) {
            collapseXautMiddleSections = true;
        }
    }

    private boolean isXautCollapseEnabled() {
        return selectedParserModel == SchoolParserModel.XAUT;
    }

    private void updateEmptyScheduleView() {
        boolean showEmpty = activeTab == 0 && courses.isEmpty();
        if (scheduleBoard != null) {
            scheduleBoard.setVisibility(activeTab == 0 && !courses.isEmpty() ? View.VISIBLE : View.GONE);
        }
        if (emptyScheduleView != null) {
            emptyScheduleView.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void openBackgroundPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_BACKGROUND_IMAGE);
    }

    private void applyBackgroundImage(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Background provider granted only temporary read access", exception);
        }
        String nextBackgroundUri = uri.toString();
        if (scheduleBoard != null && !scheduleBoard.setBackgroundImageUri(nextBackgroundUri)) {
            scheduleBoard.setBackgroundImageUri(backgroundImageUri);
            Toast.makeText(this, "无法读取所选图片，请重新选择", Toast.LENGTH_LONG).show();
            return;
        }
        backgroundImageUri = nextBackgroundUri;
        timetableBackground = "系统相册";
        saveGlobalAppearance();
        applyShellAppearance();
        renderSchedule();
        refreshMyPageBehindSettings();
        updateSettingValueRow(backgroundSettingRow, "系统相册");
        refreshVisibleSettingsTheme();
    }

    private void previewBackgroundImage(Uri uri) {
        ImageView preview = new ImageView(this);
        try {
            preview.setImageBitmap(BackgroundImageLoader.decode(
                    this,
                    uri,
                    Math.max(dp(240), getResources().getDisplayMetrics().widthPixels - dp(72)),
                    dp(320)));
        } catch (Exception exception) {
            Log.w(TAG, "Unable to preview selected background", exception);
            Toast.makeText(this, "无法预览所选图片，请重新选择", Toast.LENGTH_LONG).show();
            return;
        } catch (OutOfMemoryError error) {
            Log.e(TAG, "Insufficient memory for background preview", error);
            Toast.makeText(this, "图片尺寸过大，无法作为课表背景", Toast.LENGTH_LONG).show();
            return;
        }
        if (preview.getDrawable() == null) {
            Log.w(TAG, "Background preview decoder returned no drawable");
            Toast.makeText(this, "无法预览所选图片，请重新选择", Toast.LENGTH_LONG).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("预览背景");

        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(roundedBg(cardColorHex(), 18));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320));
        imageParams.setMargins(0, dp(4), 0, dp(10));
        panel.addView(preview, imageParams);

        Button use = new Button(this);
        use.setText("使用背景");
        use.setTextColor(Color.WHITE);
        use.setTypeface(Typeface.DEFAULT_BOLD);
        use.setBackground(roundedBg("#172033", 14));
        use.setOnClickListener(v -> {
            dialog.dismiss();
            applyBackgroundImage(uri);
        });
        panel.addView(use, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));

        Button chooseAgain = new Button(this);
        chooseAgain.setText("重新选择");
        chooseAgain.setTextColor(inkColor());
        chooseAgain.setBackground(roundedBg(cardColorHex(), 14));
        chooseAgain.setOnClickListener(v -> {
            dialog.dismiss();
            openBackgroundPicker();
        });
        LinearLayout.LayoutParams againParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        againParams.topMargin = dp(8);
        panel.addView(chooseAgain, againParams);

        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private Button iconButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(22);
        button.setTextColor(inkColor());
        button.setBackground(roundedBg(isDarkModeActive() ? "#22304A" : "#FFFFFF", 15));
        button.setOnClickListener(listener);
        button.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return false;
        });
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return button;
    }

    private Button transparentTopButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(22);
        button.setTextColor(inkColor());
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(listener);
        button.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.setBackground(roundedBg(pressColorHex(), 15));
                view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.setBackgroundColor(Color.TRANSPARENT);
                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return false;
        });
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return button;
    }

    private void showActionPanel(View anchor) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setPadding(dp(6), dp(6), dp(6), dp(6));
        panel.setBackground(roundedBg(cardColorHex(), 18));

        LinearLayout firstRow = actionRow();
        panel.addView(firstRow);

        PopupWindow popup = new PopupWindow(panel, dp(200), LinearLayout.LayoutParams.WRAP_CONTENT, true);
        firstRow.addView(popupAction("P", v -> {
            popup.dismiss();
            openPdfPicker();
        }));
        firstRow.addView(popupAction("链", v -> {
            popup.dismiss();
            showSharedLinkInputDialog();
        }));
        firstRow.addView(popupAction("+", v -> {
            popup.dismiss();
            showCourseEditor(new Course(0, 1, 2, "", defaultWeeks(), "", "", ""));
        }));
        firstRow.addView(popupAction("↗", v -> {
            popup.dismiss();
            shareScheduleLink();
        }));
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setAnimationStyle(android.R.style.Animation_Dialog);
        popup.showAtLocation(rootView == null ? anchor : rootView,
                Gravity.TOP | Gravity.RIGHT, dp(44), statusBarHeight() + dp(44));
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button popupAction(String text, View.OnClickListener listener) {
        Button button = iconButton(text, listener);
        button.setTextSize(22);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(roundedBg(isDarkModeActive() ? "#22304A" : "#FFFFFF", 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(42), dp(42));
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void shareScheduleLink() {
        if (courses.isEmpty()) {
            Toast.makeText(this, "还没有课程，先导入或添加课程后再分享", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String link = ScheduleShareCodec.encodeLink(courses);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "Polaris课程表分享");
            share.putExtra(Intent.EXTRA_TEXT, link);
            startActivity(Intent.createChooser(share, "分享课表链接"));
        } catch (Exception exception) {
            Toast.makeText(this, "生成分享链接失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSharedLinkInputDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("链接导入");
        TextView message = new TextView(this);
        message.setText("粘贴 Polaris 课程表分享链接，确认后可直接导入。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(dp(4), 1f);
        panel.addView(message);
        EditText linkInput = input("polaris://schedule/import?payload=...", "");
        panel.addView(linkInput);
        panel.addView(pageSaveButton(() -> {
            String link = extractSharedScheduleLink(linkInput.getText().toString());
            if (link.length() == 0) {
                Toast.makeText(this, "请先粘贴分享链接", Toast.LENGTH_SHORT).show();
                return;
            }
            Uri uri = Uri.parse(link);
            if (!ScheduleShareCodec.isImportLink(uri)) {
                Toast.makeText(this, "这不是有效的 Polaris 课表分享链接", Toast.LENGTH_LONG).show();
                return;
            }
            dialog.dismiss();
            startSharedScheduleImportFlow(uri);
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private String extractSharedScheduleLink(String text) {
        if (text == null) {
            return "";
        }
        int start = text.indexOf("polaris://s");
        if (start < 0) {
            start = text.indexOf("polaris://schedule/import");
        }
        if (start < 0) {
            return "";
        }
        int end = text.length();
        for (int i = start; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isWhitespace(value)) {
                end = i;
                break;
            }
        }
        return text.substring(start, end).trim();
    }

    private void startSharedScheduleImportFlow(Uri uri) {
        try {
            List<Course> sharedCourses = ScheduleShareCodec.decodeLink(uri);
            if (sharedCourses.isEmpty()) {
                Toast.makeText(this, "分享链接里没有可导入的课程", Toast.LENGTH_LONG).show();
                return;
            }
            if (courses.isEmpty()) {
                showSharedImportNameDialog(sharedCourses);
                return;
            }
            showSharedImportOverwriteDialog(sharedCourses);
        } catch (Exception exception) {
            Toast.makeText(this, "课表分享链接无效：" + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSharedImportOverwriteDialog(List<Course> sharedCourses) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("导入分享课表？");
        TextView message = new TextView(this);
        message.setText("分享链接包含 " + sharedCourses.size() + " 门课程，继续导入会覆盖当前课表。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, dp(8));
        panel.addView(message, messageParams);
        panel.addView(dialogAction("确认导入", v -> {
            dialog.dismiss();
            showSharedImportNameDialog(sharedCourses);
        }));
        panel.addView(dialogAction("取消", v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void showSharedImportNameDialog(List<Course> sharedCourses) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("设置课表名称");
        EditText nameInput = input("课表名称", scheduleName.length() == 0 ? "分享课表" : scheduleName);
        panel.addView(nameInput);
        panel.addView(pageSaveButton(() -> {
            String name = nameInput.getText().toString().trim();
            if (name.length() == 0) {
                Toast.makeText(this, "课表名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            applySharedCourses(sharedCourses, name);
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void applySharedCourses(List<Course> sharedCourses, String targetScheduleName) {
        courses.clear();
        courses.addAll(sharedCourses);
        scheduleName = targetScheduleName;
        semesterWeeks = inferSemesterWeeks(courses);
        courseSectionCount = inferSectionCount(courses);
        updateVisibleDayCount();
        scheduleRepository.saveCourses(activeScheduleId, courses);
        saveConfig();
        updateHeader();
        renderSchedule();
        updateEmptyScheduleView();
        refreshMyPage();
        Toast.makeText(this, "已导入分享课表：" + courses.size() + " 门课程", Toast.LENGTH_LONG).show();
    }

    private void cycleVisibleDayCount() {
        if (visibleDayCount == 7) {
            visibleDayCount = 5;
        } else if (visibleDayCount == 5) {
            visibleDayCount = 6;
        } else {
            visibleDayCount = 7;
        }
        renderSchedule();
        Toast.makeText(this, "已显示 " + visibleDayCount + " 天", Toast.LENGTH_SHORT).show();
    }

    private void showSettingsDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setBackgroundColor(backgroundColor());

        TextView heading = new TextView(this);
        heading.setText("设置");
        heading.setTextColor(inkColor());
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextSize(22);
        panel.addView(heading);

        TextView description = new TextView(this);
        description.setText("选择课表页显示几天");
        description.setTextColor(mutedColor());
        description.setTextSize(14);
        description.setPadding(0, dp(6), 0, dp(14));
        panel.addView(description);

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(choices);
        choices.addView(dayChoice("5天", 5, dialog));
        choices.addView(dayChoice("6天", 6, dialog));
        choices.addView(dayChoice("7天", 7, dialog));

        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void showCourseEditor(Course course) {
        new CourseEditorDialog(
                this,
                course,
                courses,
                courseSectionCount,
                backgroundColor(),
                inkColor(),
                mutedColor(),
                isDarkModeActive(),
                new CourseEditorDialog.Listener() {
                    @Override
                    public void onCourseSaved(Course original, Course edited) {
                        int index = courses.indexOf(original);
                        if (index >= 0) {
                            courses.set(index, edited);
                        } else {
                            courses.add(edited);
                        }
                        scheduleRepository.saveCourses(activeScheduleId, courses);
                        renderSchedule();
                        updateEmptyScheduleView();
                        switchTab(0);
                    }

                    @Override
                    public void onEditorDismissed() {
                        applyShellAppearance();
                    }
                })
                .show();
    }

    private EditText input(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
        editText.setTextColor(inkColor());
        editText.setHintTextColor(mutedColor());
        editText.setBackground(roundedBg(cardColorHex(), 12));
        editText.setTextSize(15);
        editText.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        params.topMargin = dp(8);
        editText.setLayoutParams(params);
        return editText;
    }

    private TextView quickWeek(String text, String value, EditText target) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(inkColor());
        view.setBackground(roundedBg(cardColorHex(), 12));
        view.setOnClickListener(v -> target.setText(value));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.setMargins(dp(3), dp(8), dp(3), 0);
        view.setLayoutParams(params);
        return view;
    }

    private TextView dayChoice(String text, int count, Dialog dialog) {
        TextView choice = new TextView(this);
        choice.setText(text);
        choice.setGravity(Gravity.CENTER);
        choice.setTextSize(16);
        choice.setTypeface(Typeface.DEFAULT_BOLD);
        boolean active = count == visibleDayCount;
        choice.setTextColor(active ? selectedTextColor() : inkColor());
        choice.setBackground(roundedBg(active ? selectedFillHex() : cardColorHex(), 12));
        choice.setOnClickListener(v -> {
            visibleDayCount = count;
            renderSchedule();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        choice.setLayoutParams(params);
        return choice;
    }

    private LinearLayout bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int horizontalPadding = "侧边".equals(bottomNavShape) || "分散".equals(bottomNavShape) ? 0 : dp(10);
        nav.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        nav.setBackground(null);
        boolean side = "侧边".equals(bottomNavShape);
        boolean showScheduleItem = !side || activeTab == 1;
        boolean showMyItem = !side || activeTab == 0;
        scheduleNav = navItem(navText("课表", true), activeTab == 0, 0);
        myNav = navItem(navText("我的", false), activeTab == 1, 1);
        if ("分散".equals(bottomNavShape) || "侧边".equals(bottomNavShape)) {
            scheduleNav.setBackground(floatingPanelBg(bottomNavOpacity, bottomNavRadius()));
            myNav.setBackground(floatingPanelBg(bottomNavOpacity, bottomNavRadius()));
            if ("侧边".equals(bottomNavShape)) {
                scheduleNav.setBackground(sidePanelBg(bottomNavOpacity, false));
                myNav.setBackground(sidePanelBg(bottomNavOpacity, true));
            }
        }
        if ("分散".equals(bottomNavShape)) {
            scheduleNav.setBackgroundColor(Color.TRANSPARENT);
            myNav.setBackgroundColor(Color.TRANSPARENT);
            nav.addView(navItemContainer(scheduleNav));
            View spacer = new View(this);
            nav.addView(spacer, new LinearLayout.LayoutParams(0, dp(navVisualHeight()), 1f));
            nav.addView(navItemContainer(myNav));
            return nav;
        }
        if ("矩形".equals(bottomNavShape)) {
            scheduleNav.setBackgroundColor(Color.TRANSPARENT);
            myNav.setBackgroundColor(Color.TRANSPARENT);
            FrameLayout container = new FrameLayout(this);
            container.addView(glassLayer(floatingPanelBg(bottomNavOpacity, bottomNavRadius()), bottomNavRadius()),
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            row.setPadding(dp(10), 0, dp(10), 0);
            row.addView(scheduleNav);
            row.addView(myNav);
            container.addView(row, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            nav.addView(container, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(navVisualHeight())));
            return nav;
        }
        if (showScheduleItem) {
            if ("侧边".equals(bottomNavShape)) {
                scheduleNav.setBackgroundColor(Color.TRANSPARENT);
                nav.addView(sideNavItemContainer(scheduleNav, false));
            } else {
                nav.addView(scheduleNav);
            }
        }
        if (showMyItem) {
            if ("侧边".equals(bottomNavShape)) {
                myNav.setBackgroundColor(Color.TRANSPARENT);
                nav.addView(sideNavItemContainer(myNav, true));
            } else {
                nav.addView(myNav);
            }
        }
        return nav;
    }

    private FrameLayout navItemContainer(TextView item) {
        FrameLayout container = new FrameLayout(this);
        container.addView(glassLayer(floatingPanelBg(bottomNavOpacity, bottomNavRadius()), bottomNavRadius()),
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        container.addView(item, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(132), dp(navVisualHeight()));
        container.setLayoutParams(params);
        return container;
    }

    private FrameLayout sideNavItemContainer(TextView item, boolean roundLeft) {
        FrameLayout container = new FrameLayout(this);
        container.addView(glassLayer(sidePanelBg(bottomNavOpacity, roundLeft), bottomNavRadius()),
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        container.addView(item, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        container.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(navVisualHeight())));
        return container;
    }

    private TextView navItem(String text, boolean active, int tab) {
        TextView item = new TextView(this);
        item.setText(styledNavText(text));
        item.setGravity(Gravity.CENTER);
        item.setTextSize(14);
        item.setLineSpacing(0f, 0.92f);
        item.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        item.setTextColor(active ? inkColor() : mutedColor());
        item.setOnClickListener(v -> switchTab(tab));
        attachPressFeedback(item);
        if ("侧边".equals(bottomNavShape)) {
            int outside = dp(24);
            if (tab == 1) {
                item.setPadding(0, 0, outside, 0);
            } else {
                item.setPadding(outside, 0, 0, 0);
            }
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        item.setLayoutParams(params);
        return item;
    }

    private FrameLayout.LayoutParams bottomNavLayoutParams() {
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        int bottomMargin = dp(18);
        if ("侧边".equals(bottomNavShape)) {
            int extra = dp(24);
            int width = dp(128) + extra;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, dp(navVisualHeight()),
                    activeTab == 0 ? Gravity.RIGHT | Gravity.BOTTOM : Gravity.LEFT | Gravity.BOTTOM);
            if (activeTab == 0) {
                params.setMargins(0, 0, -extra, bottomMargin + dp(bottomNavLiftOffset()));
            } else {
                params.setMargins(-extra, 0, 0, bottomMargin + dp(bottomNavLiftOffset()));
            }
            return params;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(navVisualHeight()), Gravity.BOTTOM);
        int side = "分散".equals(bottomNavShape)
                ? dp(Math.max(0, bottomNavSideMargin))
                : dp(tablet ? 16 : 10);
        params.setMargins(side, 0, side, bottomMargin + dp(bottomNavLiftOffset()));
        return params;
    }

    private int navVisualHeight() {
        return 72;
    }

    private int bottomNavLiftOffset() {
        return Math.max(0, bottomNavHeight - navVisualHeight());
    }

    private int bottomContentInset() {
        return dp(84 + bottomNavLiftOffset());
    }

    private void switchTab(int tab) {
        activeTab = tab;
        boolean schedule = tab == 0;
        if (scheduleBoard != null) {
            scheduleBoard.setVisibility(schedule ? View.VISIBLE : View.GONE);
        }
        if (myPage != null) {
            myPage.setVisibility(schedule ? View.GONE : View.VISIBLE);
        }
        if (settingsPage != null) {
            settingsPage.setVisibility(View.GONE);
        }
        updateEmptyScheduleView();
        if (topPanelContainer != null) {
            topPanelContainer.setVisibility(schedule ? View.VISIBLE : View.GONE);
        }
        if (bottomNavView != null) {
            bottomNavView.setVisibility(View.VISIBLE);
            bottomNavView.setLayoutParams(bottomNavLayoutParams());
        }
        if (scheduleNav != null) {
            scheduleNav.setText(styledNavText(navText("课表", schedule)));
            scheduleNav.setTypeface(schedule ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            scheduleNav.setTextColor(schedule ? inkColor() : mutedColor());
        }
        if (myNav != null) {
            myNav.setText(styledNavText(navText("我的", !schedule)));
            myNav.setTypeface(!schedule ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            myNav.setTextColor(!schedule ? inkColor() : mutedColor());
        }
        if (schedule) {
            updateHeader();
        }
        if ("侧边".equals(bottomNavShape) && rootView != null) {
            refreshBottomNavView();
        }
    }

    private void refreshBottomNavView() {
        if (rootView == null || bottomNavView == null) {
            return;
        }
        int visibility = bottomNavView.getVisibility();
        rootView.removeView(bottomNavView);
        bottomNavView = bottomNav();
        bottomNavView.setVisibility(visibility);
        rootView.addView(bottomNavView, bottomNavLayoutParams());
    }

    private ScrollView buildMyPage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(backgroundColor());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, statusBarHeight() + dp(34), 0, bottomContentInset() + dp(48));
        scrollView.addView(page);
        page.addView(profileHeader());
        page.addView(mySettingCard("课表设置", "schedule", v -> showScheduleSettings()));
        page.addView(mySettingCard("全局设置", "settings", v -> showGlobalSettings()));
        page.addView(mySettingCard("安全设置", "shield", v -> showSecuritySettings()));
        return scrollView;
    }

    private View profileHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(14), 0, dp(20));

        TextView avatar = new TextView(this);
        avatar.setText("管");
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(28);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(roundedBg("#172033", 28));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView name = new TextView(this);
        name.setText("管理员");
        name.setTextColor(inkColor());
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(10);
        header.addView(name, nameParams);

        TextView meta = new TextView(this);
        meta.setText(gradeAndSchool);
        meta.setTextColor(mutedColor());
        meta.setTextSize(13);
        meta.setGravity(Gravity.CENTER);
        header.addView(meta);
        header.setOnClickListener(v -> showSecuritySettings());
        return header;
    }

    private TextView settingCard(String titleText, String bodyText, View.OnClickListener listener) {
        TextView card = new TextView(this);
        String titleWithIcon = settingIcon(titleText) + "  " + titleText;
        card.setText(styledSettingTitle(bodyText == null || bodyText.length() == 0
                ? titleWithIcon : titleWithIcon + "\n" + bodyText));
        card.setTextColor(inkColor());
        card.setTextSize(16);
        card.setTypeface(Typeface.DEFAULT_BOLD);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(12), dp(18), dp(12));
        card.setBackground(roundedBg(cardColorHex(), 18));
        card.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.86f),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private View mySettingCard(String titleText, String iconType, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(10), dp(18), dp(10));
        card.setBackground(roundedBg(cardColorHex(), 18));
        card.setOnClickListener(listener);
        attachCardPressFeedback(card, 18);

        MySettingIconView icon = new MySettingIconView(this, iconType, inkColor(), mutedColor());
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        iconParams.leftMargin = dp(6);
        iconParams.rightMargin = dp(18);
        card.addView(icon, iconParams);

        TextView label = new TextView(this);
        label.setText(titleText);
        label.setTextColor(inkColor());
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        card.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.86f),
                dp(58));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);
        return card;
    }

    private String settingIcon(String titleText) {
        if (titleText.contains("课表")) {
            return "▣";
        }
        if (titleText.contains("全局")) {
            return "⚙";
        }
        if (titleText.contains("安全")) {
            return "盾";
        }
        return "•";
    }

    private String navText(String label, boolean active) {
        if ("课表".equals(label)) {
            return (active ? "▣" : "▦") + "\n" + label;
        }
        return (active ? "●" : "○") + "\n" + label;
    }

    private SpannableString styledNavText(String text) {
        SpannableString span = new SpannableString(text);
        int lineBreak = text.indexOf('\n');
        if (lineBreak > 0) {
            span.setSpan(new AbsoluteSizeSpan(25, true), 0, lineBreak, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new AbsoluteSizeSpan(13, true), lineBreak + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private SpannableString styledSettingTitle(String text) {
        SpannableString span = new SpannableString(text);
        int iconEnd = text.indexOf("  ");
        if (iconEnd > 0) {
            span.setSpan(new AbsoluteSizeSpan(22, true), 0, iconEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private void attachPressFeedback(View view) {
        view.setOnTouchListener((target, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                target.animate().scaleX(0.97f).scaleY(0.97f).alpha(0.82f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                target.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(130).start();
            }
            return false;
        });
    }

    private void attachCardPressFeedback(View view, int radius) {
        view.setOnTouchListener((target, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                target.setBackground(roundedBg(pressColorHex(), radius));
                target.animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                target.setBackground(roundedBg(cardColorHex(), radius));
                target.animate().scaleX(1f).scaleY(1f).setDuration(130).start();
            }
            return false;
        });
    }

    private void attachRowPressFeedback(View view) {
        view.setOnTouchListener((target, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                target.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                target.setBackground(roundedBg(pressColorHex(), 14));
                target.postDelayed(() -> target.setBackgroundColor(Color.TRANSPARENT), 180);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                target.setBackgroundColor(Color.TRANSPARENT);
            }
            return false;
        });
    }

    private String pressColorHex() {
        return isDarkModeActive() ? "#22304A" : "#EAF1FA";
    }

    private TextView sectionHeader(String text) {
        TextView header = new TextView(this);
        header.setTag(TAG_SECTION_HEADER);
        header.setText(text);
        header.setTextColor(mutedColor());
        header.setTextSize(15);
        header.setPadding(dp(10), dp(22), dp(10), dp(8));
        return header;
    }

    private LinearLayout settingsGroup() {
        LinearLayout group = new LinearLayout(this);
        group.setTag(TAG_SETTINGS_GROUP);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(10), dp(10), dp(10), dp(10));
        group.setBackground(roundedBg(groupColorHex(), 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        group.setLayoutParams(params);
        return group;
    }

    private View settingValueRow(String label, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setOnClickListener(listener);
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setMinimumHeight(dp(58));
        attachRowPressFeedback(row);

        TextView labelView = new TextView(this);
        labelView.setTag(TAG_SETTING_LABEL);
        labelView.setText(label);
        labelView.setTextColor(inkColor());
        labelView.setTextSize(16);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = new TextView(this);
        valueView.setTag(TAG_SETTING_VALUE);
        valueView.setText(value);
        valueView.setTextColor(mutedColor());
        valueView.setTextSize(15);
        valueView.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        valueView.setSingleLine(false);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = dp(10);
        row.addView(valueView, valueParams);
        return row;
    }

    private void updateSettingValueRow(View row, String value) {
        if (!(row instanceof LinearLayout)) {
            return;
        }
        LinearLayout layout = (LinearLayout) row;
        if (layout.getChildCount() > 1 && layout.getChildAt(1) instanceof TextView) {
            ((TextView) layout.getChildAt(1)).setText(value);
        }
    }

    private void showChoiceDialog(String titleText, String[] values, String current, StringSetter setter) {
        showChoiceDialog(null, titleText, values, current, setter);
    }

    private void showChoiceDialog(View anchor, String titleText, String[] values, String current, StringSetter setter) {
        Dialog dialog = new Dialog(this);
        final String[] pendingChoice = {null};
        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> dialog.dismiss());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        panel.setBackground(roundedBg(cardColorHex(), 22));
        panel.setOnClickListener(v -> {});

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(mutedColor());
        title.setTextSize(12);
        title.setSingleLine(true);
        title.setPadding(0, 0, 0, dp(4));
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (String value : values) {
            boolean active = value.equals(current);
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(0, 0, 0, 0);
            item.setBackgroundColor(Color.TRANSPARENT);

            TextView label = new TextView(this);
            label.setText(value);
            label.setTextColor(inkColor());
            label.setTextSize(16);
            label.setSingleLine(false);
            label.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView check = new TextView(this);
            check.setText(active ? "✓" : "");
            check.setTextColor(inkColor());
            check.setTextSize(25);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dp(30), dp(48));
            checkParams.leftMargin = dp(8);
            item.addView(check, checkParams);

            item.setOnClickListener(v -> {
                if (!value.equals(current)) {
                    pendingChoice[0] = value;
                }
                dialog.dismiss();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
            panel.addView(item, params);
        }

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.min(dp(214), screenWidth - dp(56));
        int panelHeight = dp(36) + values.length * dp(50) + dp(28);
        int left = screenWidth - panelWidth - dp(28);
        int top = statusBarHeight() + dp(88);
        if (anchor != null) {
            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            int belowTop = location[1] + anchor.getHeight() + dp(6);
            int aboveTop = location[1] - panelHeight - dp(6);
            int bottomLimit = choiceMenuBottomLimit(anchor, screenHeight, panelHeight);
            top = belowTop + panelHeight <= bottomLimit
                    ? belowTop
                    : Math.max(statusBarHeight() + dp(12), aboveTop);
            top = Math.min(top, screenHeight - panelHeight - dp(16));
            top = Math.max(statusBarHeight() + dp(12), top);
        }

        View choiceContent = glassDialogContent(panel, 22);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        panelParams.leftMargin = left;
        panelParams.topMargin = top;
        root.addView(choiceContent, panelParams);

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            String value = pendingChoice[0];
            if (value != null) {
                View postTarget = rootView != null ? rootView : root;
                postTarget.postDelayed(() -> setter.set(value), 80L);
            }
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(isDarkModeActive() ? 0.68f : 0.42f);
            makeDialogStill(window);
        }
    }

    private int choiceMenuBottomLimit(View anchor, int screenHeight, int panelHeight) {
        int screenLimit = screenHeight - bottomContentInset() - dp(16);
        View group = settingGroupAncestor(anchor);
        if (group == null) {
            return screenLimit;
        }
        int[] groupLocation = new int[2];
        group.getLocationOnScreen(groupLocation);
        int groupLimit = groupLocation[1] + group.getHeight() - dp(8);
        return group.getHeight() >= panelHeight + dp(72)
                ? Math.min(screenLimit, groupLimit)
                : screenLimit;
    }

    private View settingGroupAncestor(View anchor) {
        ViewParent parent = anchor.getParent();
        while (parent instanceof View) {
            View view = (View) parent;
            if (view instanceof LinearLayout && view.getBackground() != null && view.getHeight() > dp(100)) {
                return view;
            }
            parent = view.getParent();
        }
        return null;
    }

    private void showToggleDialog(String titleText, boolean current, BooleanSetter setter) {
        showChoiceDialog(titleText, new String[]{"开", "关"}, current ? "开" : "关",
                value -> setter.set("开".equals(value)));
    }

    private View settingSwitchRow(String label, boolean checked, BooleanSetter setter) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setMinimumHeight(dp(58));
        attachRowPressFeedback(row);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(inkColor());
        labelView.setTextSize(16);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(labelView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        SwitchThumbView toggle = switchView(checked);
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

    private SwitchThumbView switchView(boolean checked) {
        SwitchThumbView view = new SwitchThumbView(this, checked, isDarkModeActive());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(52), dp(30));
        params.leftMargin = dp(14);
        view.setLayoutParams(params);
        return view;
    }

    private void showNumberDialog(String titleText, int min, int max, int current, IntSetter setter) {
        showNumberDialog(titleText, min, max, current, 10, setter);
    }

    private void showNumberDialog(String titleText, int min, int max, int current, int step, IntSetter setter) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(titleText);
        final int[] value = {Math.max(min, Math.min(max, current))};
        final int safeStep = Math.max(1, step);
        EditText valueInput = stepInput(String.valueOf(value[0]));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(stepButton("−"));
        row.addView(valueInput, new LinearLayout.LayoutParams(0, dp(46), 1f));
        row.addView(stepButton("+"));
        ((TextView) row.getChildAt(0)).setOnClickListener(v -> {
            value[0] = Math.max(min, parseBounded(valueInput.getText().toString(), min, max, value[0]) - safeStep);
            valueInput.setText(String.valueOf(value[0]));
            valueInput.setSelection(valueInput.getText().length());
        });
        ((TextView) row.getChildAt(2)).setOnClickListener(v -> {
            value[0] = Math.min(max, parseBounded(valueInput.getText().toString(), min, max, value[0]) + safeStep);
            valueInput.setText(String.valueOf(value[0]));
            valueInput.setSelection(valueInput.getText().length());
        });
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panel.addView(row, rowParams);
        panel.addView(pageSaveButton(() -> {
            setter.set(parseBounded(valueInput.getText().toString(), min, max, value[0]));
            dialog.dismiss();
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void showDateDialog(String titleText, String current, StringSetter setter) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(titleText);
        Calendar date = calendarFromText(current);
        DatePicker picker = new DatePicker(this);
        picker.init(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), null);
        panel.addView(picker);
        panel.addView(pageSaveButton(() -> {
            String value = picker.getYear() + "/" + (picker.getMonth() + 1) + "/" + picker.getDayOfMonth();
            setter.set(value);
            dialog.dismiss();
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void showTimeDialog(String titleText, String current, StringSetter setter) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(titleText);
        int[] time = timeFromText(current);
        TimePicker picker = new TimePicker(this);
        picker.setIs24HourView(true);
        picker.setHour(time[0]);
        picker.setMinute(time[1]);
        panel.addView(picker);
        panel.addView(pageSaveButton(() -> {
            String value = twoDigits(picker.getHour()) + ":" + twoDigits(picker.getMinute()) + "开始";
            setter.set(value);
            dialog.dismiss();
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private LinearLayout dialogPanel(String titleText) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setMinimumWidth(dp(292));
        panel.setBackground(roundedBg(cardColorHex(), 22));
        TextView titleView = new TextView(this);
        titleView.setText(titleText);
        titleView.setTextColor(inkColor());
        titleView.setTextSize(20);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, dp(10));
        panel.addView(titleView);
        return panel;
    }

    private View glassDialogContent(LinearLayout panel, int radius) {
        if (!shellBarsBlurEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return panel;
        }
        panel.setBackgroundColor(Color.TRANSPARENT);
        BackdropBlurView glass = new BackdropBlurView(this);
        glass.setSourceView(dialogBlurSource());
        glass.setGlassBackground(dialogGlassBg(radius), dp(radius));
        glass.setBlurEnabled(true, dp(22));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        glass.addView(panel, params);
        return glass;
    }

    private View glassDialogContent(ScrollView scrollView, LinearLayout panel, int radius) {
        if (!shellBarsBlurEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return scrollView;
        }
        panel.setBackgroundColor(Color.TRANSPARENT);
        BackdropBlurView glass = new BackdropBlurView(this);
        glass.setSourceView(dialogBlurSource());
        glass.setGlassBackground(dialogGlassBg(radius), dp(radius));
        glass.setBlurEnabled(true, dp(22));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        glass.addView(scrollView, params);
        return glass;
    }

    private View dialogBlurSource() {
        if (!shellBarsBlurEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        return rootView != null ? rootView : getWindow().getDecorView();
    }

    private GradientDrawable dialogGlassBg(int radius) {
        GradientDrawable drawable = new GradientDrawable();
        if (isDarkModeActive()) {
            drawable.setColor(Color.argb(142, 24, 34, 53));
            drawable.setStroke(dp(1), Color.argb(85, 255, 255, 255));
        } else {
            drawable.setColor(Color.argb(172, 248, 251, 255));
            drawable.setStroke(dp(1), Color.argb(150, 255, 255, 255));
        }
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private void transparentDialog(Dialog dialog) {
        if (dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(isDarkModeActive() ? 0.68f : 0.42f);
            window.setLayout(dp(320), LinearLayout.LayoutParams.WRAP_CONTENT);
            makeDialogStill(window);
        }
    }

    private void makeDialogStill(Window window) {
        if (window == null) {
            return;
        }
        window.setWindowAnimations(0);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.windowAnimations = 0;
        window.setAttributes(attributes);
    }

    private void showScheduleSettings() {
        LinearLayout panel = settingsPagePanel("课表设置");
        panel.addView(settingValueRow("课表名称", scheduleName, v -> {}));
        panel.addView(settingValueRow("当前周", "第 " + currentWeekFromDate() + " 周", v -> {}));
        panel.addView(sectionHeader("课表外观"));
        LinearLayout displayCard = settingsGroup();
        displayCard.addView(settingSwitchRow("显示周六", showSaturday, value -> {
                    showSaturday = value;
                    updateVisibleDayCount();
                    saveConfig();
                    renderSchedule();
                }));
        displayCard.addView(settingSwitchRow("显示周日", showSunday, value -> {
                    showSunday = value;
                    updateVisibleDayCount();
                    saveConfig();
                    renderSchedule();
                }));
        displayCard.addView(settingSwitchRow("显示非本周课程", showOutOfWeekCourses, value -> {
                    showOutOfWeekCourses = value;
                    saveConfig();
                    renderSchedule();
                }));
        panel.addView(displayCard);
        panel.addView(sectionHeader("课表数据"));
        LinearLayout dataCard = settingsGroup();
        dataCard.addView(settingValueRow("第一周的第一天", firstWeekDay,
                v -> showDateDialog("第一周的第一天", firstWeekDay, value -> {
                    firstWeekDay = value;
                    currentWeek = currentWeekFromDate();
                    saveConfig();
                    renderSchedule();
                    refreshActiveSettingsPage();
                })));
        dataCard.addView(settingValueRow("学期周数", semesterWeeks + " 周",
                v -> showNumberDialog("学期周数", 1, 20, semesterWeeks, 1, value -> {
            semesterWeeks = value;
            currentWeek = Math.max(1, Math.min(semesterWeeks, currentWeekFromDate()));
            saveConfig();
            renderSchedule();
            updateHeader();
            refreshActiveSettingsPage();
        })));
        dataCard.addView(settingValueRow("查看所有课程", courses.size() + " 门", v -> showCourseManagePage()));
        panel.addView(dataCard);
        showSettingsPage("课表设置", panel);
    }

    private void showCourseManagePage() {
        LinearLayout panel = settingsPagePanel("查看所有课程");
        courseManageSortContainer = new LinearLayout(this);
        courseManageSortContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(courseManageSortContainer);
        refreshCourseManageSortControls();

        courseManageListContainer = new LinearLayout(this);
        courseManageListContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(courseManageListContainer);
        refreshCourseManageList();
        showSettingsPage("查看所有课程", panel, activeSettingsTitle);
    }

    private void refreshCourseManageSortControls() {
        if (courseManageSortContainer == null) {
            return;
        }
        courseManageSortContainer.removeAllViews();
        LinearLayout sortGroup = settingsGroup();
        sortGroup.addView(courseSortRow("排序方式", new String[]{"上课时间", "课程多少", "名称首字母"},
                new String[]{"time", "count", "name"}));
        sortGroup.addView(courseSortRow("排序方向", new String[]{"正序", "倒序"},
                new String[]{"asc", "desc"}));
        courseManageSortContainer.addView(sortGroup);
    }

    private void refreshCourseManageList() {
        if (courseManageListContainer == null) {
            return;
        }
        courseManageListContainer.removeAllViews();
        if (courses.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有课程");
            empty.setTextColor(mutedColor());
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(42), 0, dp(42));
            courseManageListContainer.addView(empty);
            return;
        }
        courseManageListContainer.addView(sectionHeader("全部课程"));
        LinearLayout list = settingsGroup();
        for (CourseGroup group : sortedCourseGroupsForManage()) {
            list.addView(courseManageRow(group));
        }
        courseManageListContainer.addView(list);
    }

    private View courseSortRow(String label, String[] labels, String[] values) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(4), dp(6), dp(4), dp(6));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(mutedColor());
        title.setTextSize(13);
        group.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        group.addView(row);
        for (int i = 0; i < labels.length; i++) {
            String text = labels[i];
            String value = values[i];
            TextView chip = new TextView(this);
            chip.setText(text);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setTextSize(14);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            boolean active = isActiveCourseSort(value);
            chip.setTextColor(active ? selectedTextColor() : inkColor());
            chip.setBackground(roundedBg(active ? selectedFillHex() : cardColorHex(), 14));
            chip.setOnClickListener(v -> {
                if ("asc".equals(value) || "desc".equals(value)) {
                    courseManageDescending = "desc".equals(value);
                } else {
                    courseManageSort = value;
                }
                refreshCourseManageSortControls();
                refreshCourseManageList();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
            params.setMargins(dp(3), dp(8), dp(3), 0);
            row.addView(chip, params);
        }
        return group;
    }

    private boolean isActiveCourseSort(String value) {
        if ("asc".equals(value)) {
            return !courseManageDescending;
        }
        if ("desc".equals(value)) {
            return courseManageDescending;
        }
        return value.equals(courseManageSort);
    }

    private List<CourseGroup> sortedCourseGroupsForManage() {
        List<CourseGroup> sorted = courseGroupsForManage();
        Comparator<CourseGroup> comparator;
        if ("count".equals(courseManageSort)) {
            comparator = (first, second) -> Integer.compare(courseGroupSectionCount(first), courseGroupSectionCount(second));
        } else if ("name".equals(courseManageSort)) {
            Collator collator = Collator.getInstance(Locale.CHINA);
            comparator = (first, second) -> collator.compare(first.name, second.name);
        } else {
            comparator = (first, second) -> {
                int firstTime = courseGroupTimeValue(first);
                int secondTime = courseGroupTimeValue(second);
                return Integer.compare(firstTime, secondTime);
            };
        }
        if (courseManageDescending) {
            final Comparator<CourseGroup> ascendingComparator = comparator;
            comparator = (first, second) -> ascendingComparator.compare(second, first);
        }
        Collections.sort(sorted, comparator);
        return sorted;
    }

    private List<CourseGroup> courseGroupsForManage() {
        Map<String, CourseGroup> groups = new LinkedHashMap<>();
        for (Course course : courses) {
            String name = course.name == null || course.name.trim().length() == 0
                    ? "未命名课程" : course.name.trim();
            CourseGroup group = groups.get(name);
            if (group == null) {
                group = new CourseGroup(name);
                groups.put(name, group);
            }
            group.courses.add(course);
        }
        return new ArrayList<>(groups.values());
    }

    private int courseTimeValue(Course course) {
        int day = course.day < 0 ? 99 : course.day;
        return day * 100 + Math.max(1, course.startSection);
    }

    private int courseGroupTimeValue(CourseGroup group) {
        int min = Integer.MAX_VALUE;
        for (Course course : group.courses) {
            min = Math.min(min, courseTimeValue(course));
        }
        return min == Integer.MAX_VALUE ? 9999 : min;
    }

    private int courseGroupSectionCount(CourseGroup group) {
        int total = 0;
        for (Course course : group.courses) {
            total += Math.max(1, course.endSection - course.startSection + 1);
        }
        return total;
    }

    private View courseManageRow(CourseGroup group) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        row.setMinimumHeight(dp(66));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(group.name);
        name.setTextColor(inkColor());
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(false);
        left.addView(name);

        row.addView(courseManageTimeGrid(group), courseManageTimeGridParams());
        return row;
    }

    private LinearLayout.LayoutParams courseManageTimeGridParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(206), LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(12);
        return params;
    }

    private LinearLayout courseManageTimeGrid(CourseGroup group) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        List<Course> sorted = new ArrayList<>(group.courses);
        Collections.sort(sorted, (first, second) -> Integer.compare(courseTimeValue(first), courseTimeValue(second)));
        for (int i = 0; i < sorted.size(); i += 2) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            line.addView(courseManageTimeCell(sorted.get(i)), courseManageTimeCellParams(0));
            if (i + 1 < sorted.size()) {
                line.addView(courseManageTimeCell(sorted.get(i + 1)), courseManageTimeCellParams(dp(8)));
            }
            grid.addView(line);
        }
        return grid;
    }

    private LinearLayout.LayoutParams courseManageTimeCellParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = leftMargin;
        params.topMargin = dp(5);
        params.bottomMargin = dp(5);
        return params;
    }

    private LinearLayout courseManageTimeCell(Course course) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.RIGHT);

        TextView weeks = new TextView(this);
        weeks.setText(normalizeWeeks(course.weeks));
        weeks.setTextColor(mutedColor());
        weeks.setTextSize(12);
        weeks.setGravity(Gravity.RIGHT);
        weeks.setSingleLine(false);
        cell.addView(weeks, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView time = new TextView(this);
        time.setText(courseTimeInlineText(course));
        time.setTextColor(mutedColor());
        time.setTextSize(13);
        time.setGravity(Gravity.RIGHT);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setSingleLine(true);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(2);
        cell.addView(time, timeParams);
        return cell;
    }

    private String courseTimeInlineText(Course course) {
        return dayName(course.day) + course.startSection + "-" + course.endSection + "节";
    }

    private String courseTimeText(Course course) {
        return dayName(course.day) + "\n" + course.startSection + "-" + course.endSection + "节";
    }

    private String groupTimeText(CourseGroup group) {
        StringBuilder builder = new StringBuilder();
        List<Course> sorted = new ArrayList<>(group.courses);
        Collections.sort(sorted, (first, second) -> Integer.compare(courseTimeValue(first), courseTimeValue(second)));
        for (Course course : sorted) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(dayName(course.day)).append(' ')
                    .append(course.startSection).append('-').append(course.endSection).append("节");
        }
        return builder.toString();
    }

    private String groupWeeksText(CourseGroup group) {
        List<String> values = new ArrayList<>();
        for (Course course : group.courses) {
            String weeks = normalizeWeeks(course.weeks);
            if (!values.contains(weeks)) {
                values.add(weeks);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private String dayName(int day) {
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        if (day >= 0 && day < days.length) {
            return days[day];
        }
        return "未知";
    }

    private static class CourseGroup {
        final String name;
        final List<Course> courses = new ArrayList<>();

        CourseGroup(String name) {
            this.name = name;
        }

        Course firstCourse() {
            return courses.isEmpty() ? new Course(0, 1, 1, name, "", "", "", "") : courses.get(0);
        }
    }

    private Dialog settingsDialog(String headingText) {
        Dialog dialog = new Dialog(this);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setId(1001);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), statusBarHeight() + dp(18), dp(18), dp(24));
        panel.setBackgroundColor(backgroundColor());
        scrollView.addView(panel);

        TextView heading = new TextView(this);
        heading.setText(headingText);
        heading.setTextColor(inkColor());
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setTextSize(22);
        panel.addView(heading);

        Button close = new Button(this);
        close.setText("取消");
        close.setTextColor(mutedColor());
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> dialog.dismiss());
        panel.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        dialog.setContentView(glassDialogContent(scrollView, panel, 22));
        return dialog;
    }

    private View toggleRow(String label, boolean initial, BooleanSetter setter) {
        final boolean[] value = {initial};
        TextView row = settingCard(label, initial ? "开" : "关", v -> {
            value[0] = !value[0];
            setter.set(value[0]);
            ((TextView) v).setText(label + "\n" + (value[0] ? "开" : "关"));
        });
        return row;
    }

    private View choiceRow(String label, String[] values, String current, StringSetter setter) {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(mutedColor());
        title.setTextSize(13);
        group.addView(title);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        group.addView(row);
        for (String value : values) {
            TextView choice = new TextView(this);
            choice.setText(value);
            choice.setGravity(Gravity.CENTER);
            choice.setTypeface(Typeface.DEFAULT_BOLD);
            boolean selected = value.equals(current);
            choice.setTextColor(selected ? selectedTextColor() : inkColor());
            choice.setBackground(roundedBg(selected ? selectedFillHex() : cardColorHex(), 14));
            choice.setOnClickListener(v -> {
                setter.set(value);
                for (int i = 0; i < row.getChildCount(); i++) {
                    TextView item = (TextView) row.getChildAt(i);
                    boolean active = item.getText().toString().equals(value);
                    item.setTextColor(active ? selectedTextColor() : inkColor());
                    item.setBackground(roundedBg(active ? selectedFillHex() : cardColorHex(), 14));
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
            params.setMargins(dp(3), dp(8), dp(3), dp(8));
            row.addView(choice, params);
        }
        return group;
    }

    private Button saveSettingsButton(Dialog dialog, Runnable onSave) {
        Button save = new Button(this);
        save.setText("保存");
        save.setTextColor(Color.WHITE);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setTextSize(16);
        save.setBackground(roundedBg("#172033", 14));
        save.setOnClickListener(v -> {
            onSave.run();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        params.topMargin = dp(10);
        save.setLayoutParams(params);
        return save;
    }

    private void refreshMyPage() {
        if (contentHost == null || myPage == null) {
            return;
        }
        contentHost.removeView(myPage);
        myPage = buildMyPage();
        int settingsIndex = settingsPage == null ? -1 : contentHost.indexOfChild(settingsPage);
        if (settingsIndex >= 0) {
            contentHost.addView(myPage, settingsIndex, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            contentHost.addView(myPage, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        switchTab(activeTab);
    }

    private void refreshMyPageBehindSettings() {
        if (contentHost == null || myPage == null) {
            return;
        }
        boolean settingsVisible = settingsPage != null && settingsPage.getVisibility() == View.VISIBLE;
        contentHost.removeView(myPage);
        myPage = buildMyPage();
        int settingsIndex = settingsPage == null ? -1 : contentHost.indexOfChild(settingsPage);
        if (settingsIndex >= 0) {
            contentHost.addView(myPage, settingsIndex, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            contentHost.addView(myPage, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        myPage.setVisibility(settingsVisible || activeTab == 0 ? View.GONE : View.VISIBLE);
    }

    private LinearLayout settingsPagePanel(String headingText) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), bottomContentInset() + dp(48));
        return panel;
    }

    private void showSettingsPage(String headingText, LinearLayout panel) {
        showSettingsPage(headingText, panel, "");
    }

    private void showSettingsPage(String headingText, LinearLayout panel, String backTarget) {
        previousSettingsTitle = backTarget == null ? "" : backTarget;
        activeSettingsTitle = headingText;
        boolean settingsAlreadyVisible = settingsPage.getVisibility() == View.VISIBLE;
        settingsPage.clearAnimation();
        settingsPage.removeAllViews();
        settingsPage.setBackgroundColor(backgroundColor());
        settingsPage.setAlpha(1f);
        if (suppressSettingsPageAnimation || settingsAlreadyVisible) {
            settingsPage.setTranslationX(0f);
        }

        LinearLayout fixedHeader = new LinearLayout(this);
        fixedHeader.setTag(TAG_SETTINGS_HEADER);
        fixedHeader.setOrientation(LinearLayout.HORIZONTAL);
        fixedHeader.setGravity(Gravity.CENTER_VERTICAL);
        fixedHeader.setPadding(0, statusBarHeight() + dp(8), 0, dp(8));
        fixedHeader.setBackgroundColor(backgroundColor());

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(inkColor());
        back.setTextSize(28);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> closeSettingsPage());
        fixedHeader.addView(back, new LinearLayout.LayoutParams(dp(48), dp(54)));

        TextView heading = new TextView(this);
        heading.setText(headingText);
        heading.setTextColor(inkColor());
        heading.setTextSize(21);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setSingleLine(true);
        fixedHeader.addView(heading, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ScrollView contentScroll = new ScrollView(this);
        contentScroll.setTag(TAG_SETTINGS_SCROLL);
        contentScroll.setFillViewport(true);
        contentScroll.setBackgroundColor(backgroundColor());
        contentScroll.addView(panel);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        int headerHeight = statusBarHeight() + dp(70);
        scrollParams.topMargin = headerHeight;
        settingsPage.addView(contentScroll, scrollParams);
        settingsPage.addView(fixedHeader, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, headerHeight));
        if (scheduleBoard != null) {
            scheduleBoard.setVisibility(View.GONE);
        }
        if (myPage != null) {
            myPage.setVisibility(View.GONE);
            myPage.setTranslationX(0f);
            myPage.setAlpha(1f);
        }
        settingsPage.setVisibility(View.VISIBLE);
        if (topPanelContainer != null) {
            topPanelContainer.setVisibility(View.GONE);
        }
        if (bottomNavView != null) {
            bottomNavView.setVisibility(View.GONE);
        }
        if (suppressSettingsPageAnimation || settingsAlreadyVisible) {
            settingsPage.setTranslationX(0f);
            settingsPage.setAlpha(1f);
            if (myPage != null) {
                myPage.setVisibility(View.GONE);
                myPage.setTranslationX(0f);
                myPage.setAlpha(1f);
            }
        } else {
            int width = settingsPage.getWidth() > 0
                    ? settingsPage.getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            settingsPage.setAlpha(1f);
            settingsPage.setTranslationX(width);
            settingsPage.animate()
                    .translationX(0f)
                    .setDuration(180)
                    .withEndAction(() -> {
                        if (myPage != null) {
                            myPage.setVisibility(View.GONE);
                            myPage.setTranslationX(0f);
                            myPage.setAlpha(1f);
                        }
                    })
                    .start();
        }
    }

    private void refreshActiveSettingsPage() {
        if (settingsPage == null || settingsPage.getVisibility() != View.VISIBLE) {
            return;
        }
        String title = activeSettingsTitle;
        int scrollY = currentSettingsScrollY();
        suppressSettingsPageAnimation = true;
        try {
            if ("全局设置".equals(title)) {
                showGlobalSettings();
            } else if ("课表设置".equals(title)) {
                showScheduleSettings();
            } else if ("查看所有课程".equals(title) || "管理已添加课程".equals(title)) {
                showCourseManagePage();
            } else if ("安全设置".equals(title)) {
                showSecuritySettings();
            }
        } finally {
            suppressSettingsPageAnimation = false;
        }
        if (settingsPage != null) {
            settingsPage.clearAnimation();
            settingsPage.setTranslationX(0f);
            settingsPage.setAlpha(1f);
            settingsPage.setVisibility(View.VISIBLE);
            if (myPage != null) {
                myPage.setVisibility(View.GONE);
                myPage.setTranslationX(0f);
                myPage.setAlpha(1f);
            }
            settingsPage.post(() -> {
                ScrollView scrollView = currentSettingsScrollView();
                if (scrollView != null) {
                    scrollView.scrollTo(0, scrollY);
                }
            });
        }
    }

    private void refreshVisibleSettingsTheme() {
        if (settingsPage == null || settingsPage.getVisibility() != View.VISIBLE) {
            return;
        }
        settingsPage.setBackgroundColor(backgroundColor());
        refreshSettingsThemeRecursive(settingsPage);
    }

    private void refreshSettingsThemeRecursive(View view) {
        Object tag = view.getTag();
        if (TAG_SETTINGS_GROUP.equals(tag)) {
            view.setBackground(roundedBg(groupColorHex(), 18));
        } else if (TAG_SECTION_HEADER.equals(tag) && view instanceof TextView) {
            ((TextView) view).setTextColor(mutedColor());
        } else if (TAG_SETTING_LABEL.equals(tag) && view instanceof TextView) {
            ((TextView) view).setTextColor(inkColor());
        } else if (TAG_SETTING_VALUE.equals(tag) && view instanceof TextView) {
            ((TextView) view).setTextColor(mutedColor());
        } else if (TAG_SETTINGS_HEADER.equals(tag)) {
            view.setBackgroundColor(backgroundColor());
        } else if (TAG_SETTINGS_SCROLL.equals(tag)) {
            view.setBackgroundColor(backgroundColor());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                refreshSettingsThemeRecursive(group.getChildAt(i));
            }
        }
    }

    private int currentSettingsScrollY() {
        ScrollView scrollView = currentSettingsScrollView();
        return scrollView == null ? 0 : scrollView.getScrollY();
    }

    private ScrollView currentSettingsScrollView() {
        if (settingsPage == null) {
            return null;
        }
        for (int i = 0; i < settingsPage.getChildCount(); i++) {
            View child = settingsPage.getChildAt(i);
            if (child instanceof ScrollView) {
                return (ScrollView) child;
            }
        }
        return null;
    }

    private void closeSettingsPage() {
        if (settingsPage == null || settingsPage.getVisibility() != View.VISIBLE) {
            switchTab(1);
            return;
        }
        if (previousSettingsTitle != null && previousSettingsTitle.length() > 0) {
            String target = previousSettingsTitle;
            previousSettingsTitle = "";
            suppressSettingsPageAnimation = true;
            if ("课表设置".equals(target)) {
                showScheduleSettings();
            } else if ("全局设置".equals(target)) {
                showGlobalSettings();
            } else if ("安全设置".equals(target)) {
                showSecuritySettings();
            } else {
                switchTab(1);
            }
            suppressSettingsPageAnimation = false;
            if (settingsPage != null) {
                settingsPage.setTranslationX(0f);
                settingsPage.setAlpha(1f);
            }
            return;
        }
        if (myPage != null) {
            myPage.setVisibility(View.VISIBLE);
            myPage.setTranslationX(0f);
            myPage.setAlpha(1f);
        }
        int width = settingsPage.getWidth() > 0
                ? settingsPage.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        settingsPage.animate()
                .translationX(width)
                .setDuration(170)
                .withEndAction(() -> {
                    settingsPage.setAlpha(1f);
                    settingsPage.setTranslationX(0f);
                    switchTab(1);
                    if (myPage != null) {
                        myPage.setVisibility(View.VISIBLE);
                        myPage.setTranslationX(0f);
                        myPage.setAlpha(1f);
                    }
                })
                .start();
    }

    private View numberRow(String label, int min, int max, int initial, IntSetter setter) {
        final int[] value = {Math.max(min, Math.min(max, initial))};
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, dp(8), 0, dp(8));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(mutedColor());
        title.setTextSize(13);
        group.addView(title);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        group.addView(row);

        TextView minus = stepButton("−");
        EditText valueInput = stepInput(String.valueOf(value[0]));
        TextView plus = stepButton("+");
        minus.setOnClickListener(v -> {
            value[0] = Math.max(min, parseBounded(valueInput.getText().toString(), min, max, value[0]) - 10);
            valueInput.setText(String.valueOf(value[0]));
            valueInput.setSelection(valueInput.getText().length());
            setter.set(value[0]);
        });
        plus.setOnClickListener(v -> {
            value[0] = Math.min(max, parseBounded(valueInput.getText().toString(), min, max, value[0]) + 10);
            valueInput.setText(String.valueOf(value[0]));
            valueInput.setSelection(valueInput.getText().length());
            setter.set(value[0]);
        });
        row.addView(minus);
        row.addView(valueInput, new LinearLayout.LayoutParams(0, dp(44), 1f));
        row.addView(plus);
        return group;
    }

    private interface IntSetter {
        void set(int value);
    }

    private TextView stepButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(22);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(inkColor());
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundedBg(cardColorHex(), 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(44));
        params.setMargins(dp(3), dp(8), dp(3), dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private TextView stepValue(String text) {
        TextView value = new TextView(this);
        value.setText(text);
        value.setTextSize(18);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextColor(inkColor());
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        value.setMinWidth(dp(80));
        value.setBackground(roundedBg(cardColorHex(), 14));
        return value;
    }

    private EditText stepInput(String text) {
        EditText value = new EditText(this);
        value.setText(text);
        value.setTextSize(18);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextColor(inkColor());
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        value.setSelectAllOnFocus(true);
        value.setInputType(InputType.TYPE_CLASS_NUMBER);
        value.setBackground(roundedBg(cardColorHex(), 14));
        value.setPadding(dp(8), 0, dp(8), 0);
        return value;
    }

    private Button pageSaveButton(Runnable onSave) {
        Button save = new Button(this);
        save.setText("保存并应用");
        save.setTextColor(Color.WHITE);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setTextSize(16);
        save.setBackground(roundedBg("#172033", 14));
        save.setOnClickListener(v -> onSave.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(12), 0, 0);
        save.setLayoutParams(params);
        return save;
    }

    private void showGlobalSettings() {
        LinearLayout panel = settingsPagePanel("全局设置");

        panel.addView(sectionHeader("显示"));
        LinearLayout displayCard = settingsGroup();
        displayCard.addView(settingValueRow("课程表", scheduleName, v -> showScheduleSwitchDialog()));
        displayCard.addView(settingValueRow("深色模式", darkMode,
                v -> showChoiceDialog(v, "深色模式", new String[]{"跟随系统", "浅色", "深色"}, darkMode,
                        value -> {
                            darkMode = value;
                            scheduleRepository.saveGlobalDarkMode(darkMode);
                            saveConfig();
                            applyShellAppearance();
                            refreshMyPageBehindSettings();
                            renderSchedule();
                            updateSettingValueRow(v, darkMode);
                            refreshVisibleSettingsTheme();
                        })));
        backgroundSettingRow = settingValueRow("课程表背景", backgroundImageUri.length() == 0 ? "未设置" : "系统相册",
                v -> showChoiceDialog(v, "课程表背景", new String[]{"从系统相册选择", "清除背景"}, backgroundImageUri.length() == 0 ? "清除背景" : "从系统相册选择",
                        value -> {
                            if (value.startsWith("从")) {
                                openBackgroundPicker();
                            } else {
                                backgroundImageUri = "";
                                timetableBackground = "清爽蓝";
                                saveGlobalAppearance();
                                applyShellAppearance();
                                renderSchedule();
                                updateSettingValueRow(v, "未设置");
                                refreshVisibleSettingsTheme();
                            }
                        }));
        displayCard.addView(backgroundSettingRow);
        panel.addView(displayCard);

        panel.addView(sectionHeader("界面"));
        LinearLayout shellCard = settingsGroup();
        shellCard.addView(settingSwitchRow("全局毛玻璃效果", shellBarsBlurEnabled, value -> {
            shellBarsBlurEnabled = value;
            saveGlobalAppearance();
            applyShellAppearance();
        }));
        shellCard.addView(settingValueRow("课表顶部透明度", timetableHeaderOpacity + "%",
                v -> showNumberDialog("课表顶部透明度", 40, 100, timetableHeaderOpacity, value -> {
                    timetableHeaderOpacity = Math.max(40, Math.min(100, value));
                    saveGlobalAppearance();
                    applyShellAppearance();
                    refreshActiveSettingsPage();
                })));
        shellCard.addView(settingValueRow("课表底部透明度", bottomNavOpacity + "%",
                v -> showNumberDialog("课表底部透明度", 40, 100, bottomNavOpacity, value -> {
                    bottomNavOpacity = Math.max(40, Math.min(100, value));
                    saveGlobalAppearance();
                    applyShellAppearance();
                    refreshActiveSettingsPage();
                })));
        shellCard.addView(settingValueRow("底部切换栏高度", bottomNavHeight + " dp",
                v -> showNumberDialog("底部切换栏高度", 56, 120, bottomNavHeight, 1, value -> {
                    bottomNavHeight = Math.max(56, Math.min(120, value));
                    saveGlobalAppearance();
                    applyShellAppearance();
                    refreshMyPageBehindSettings();
                    renderSchedule();
                    refreshActiveSettingsPage();
                })));
        shellCard.addView(settingValueRow("底部切换栏形状", bottomNavShape,
                v -> showChoiceDialog(v, "底部切换栏形状", new String[]{"矩形", "分散", "侧边"}, bottomNavShape,
                        value -> {
                            bottomNavShape = normalizeBottomNavShape(value);
                            saveGlobalAppearance();
                            applyShellAppearance();
                            updateSettingValueRow(v, bottomNavShape);
                        })));
        shellCard.addView(settingValueRow("底部圆角半径", bottomNavRadius() + " dp",
                v -> showNumberDialog("底部圆角半径", 0, 72, bottomNavRadius(), value -> {
                    setBottomNavRadiusForShape(bottomNavShape, value);
                    saveGlobalAppearance();
                    applyShellAppearance();
                    updateSettingValueRow(v, bottomNavRadius() + " dp");
                })));
        shellCard.addView(settingValueRow("底部侧边距", bottomNavSideMargin + " dp",
                v -> showNumberDialog("底部侧边距", 0, 48, bottomNavSideMargin, value -> {
                    bottomNavSideMargin = value;
                    saveGlobalAppearance();
                    applyShellAppearance();
                    updateSettingValueRow(v, bottomNavSideMargin + " dp");
                })));
        panel.addView(shellCard);

        panel.addView(sectionHeader("课表框架"));
        LinearLayout frameCard = settingsGroup();
        frameCard.addView(settingValueRow("课程格子高度", courseCellHeight + " dp",
                v -> showNumberDialog("课程格子高度", 56, 120, courseCellHeight, value -> {
                    courseCellHeight = value;
                    saveGlobalAppearance();
                    renderSchedule();
                    refreshActiveSettingsPage();
                })));
        frameCard.addView(settingValueRow("格子圆角半径", courseCornerRadius + " dp",
                v -> showNumberDialog("格子圆角半径", 0, 24, courseCornerRadius, value -> {
                    courseCornerRadius = value;
                    saveGlobalAppearance();
                    renderSchedule();
                    refreshActiveSettingsPage();
                })));
        frameCard.addView(settingValueRow("格子不透明度", courseBlockOpacity + "%",
                v -> showNumberDialog("格子不透明度", 45, 100, courseBlockOpacity, value -> {
                    courseBlockOpacity = Math.max(45, Math.min(100, value));
                    saveGlobalAppearance();
                    renderSchedule();
                    refreshActiveSettingsPage();
                })));
        panel.addView(frameCard);
        showSettingsPage("全局设置", panel);
    }

    private void applyShellAppearance() {
        if (getWindow() != null) {
            getWindow().setStatusBarColor(backgroundColor());
            getWindow().setNavigationBarColor(backgroundColor());
        }
        if (rootView != null) {
            rootView.setBackgroundColor(backgroundColor());
        }
        if (topPanelGlassLayer != null) {
            updateGlassLayer(topPanelGlassLayer, liquidGlassBg(timetableHeaderOpacity), 24);
        }
        if (bottomNavView != null) {
            refreshBottomNavView();
        }
        if (title != null) {
            title.setTextColor(inkColor());
        }
        if (subtitle != null) {
            subtitle.setTextColor(mutedColor());
        }
        if (scheduleNav != null) {
            boolean active = activeTab == 0;
            scheduleNav.setTextColor(active ? inkColor() : mutedColor());
        }
        if (myNav != null) {
            boolean active = activeTab == 1;
            myNav.setTextColor(active ? inkColor() : mutedColor());
        }
        if (settingsPage != null) {
            settingsPage.setBackgroundColor(backgroundColor());
        }
    }

    private void showScheduleSwitchDialog() {
        List<ScheduleRepository.ScheduleEntry> schedules = scheduleRepository.loadSchedules();
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("课程表");
        for (ScheduleRepository.ScheduleEntry entry : schedules) {
            TextView item = new TextView(this);
            item.setText(entry.id.equals(activeScheduleId) ? entry.name + "  当前" : entry.name);
            item.setGravity(Gravity.CENTER);
            item.setTextSize(17);
            boolean active = entry.id.equals(activeScheduleId);
            item.setTextColor(active ? selectedTextColor() : inkColor());
            item.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.setBackground(roundedBg(active ? selectedFillHex() : cardColorHex(), 14));
            item.setOnClickListener(v -> {
                dialog.dismiss();
                if (active) {
                    showRenameScheduleDialog();
                } else {
                    switchSchedule(entry.id);
                    showGlobalSettings();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
            params.setMargins(0, dp(5), 0, dp(5));
            panel.addView(item, params);
        }
        panel.addView(dialogAction("新增课表", v -> {
            dialog.dismiss();
            showCreateScheduleDialog();
        }));
        panel.addView(dialogAction("删除此课表", v -> {
            dialog.dismiss();
            showDeleteCurrentScheduleDialog();
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void showRenameScheduleDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("修改课表名称");
        EditText nameInput = input("课表名称", scheduleName);
        panel.addView(nameInput);
        panel.addView(pageSaveButton(() -> {
            String nextName = nameInput.getText().toString().trim();
            if (nextName.length() == 0) {
                Toast.makeText(this, "课表名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            scheduleName = nextName;
            saveConfig();
            updateHeader();
            refreshMyPage();
            dialog.dismiss();
            showGlobalSettings();
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void showCreateScheduleDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("新增课表");
        EditText nameInput = input("课表名称，例如 大二下", "");
        panel.addView(nameInput);
        panel.addView(pageSaveButton(() -> {
            String name = nameInput.getText().toString().trim();
            ScheduleRepository.ScheduleEntry entry = scheduleRepository.createSchedule(
                    name.length() == 0 ? "新课表" : name);
            copyGlobalAppearanceToSchedule(entry.id);
            switchSchedule(entry.id);
            dialog.dismiss();
            showGlobalSettings();
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void showDeleteCurrentScheduleDialog() {
        List<ScheduleRepository.ScheduleEntry> schedules = scheduleRepository.loadSchedules();
        if (schedules.size() <= 1) {
            Toast.makeText(this, "至少保留一个课表", Toast.LENGTH_SHORT).show();
            showScheduleSwitchDialog();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("删除此课表？");
        TextView message = new TextView(this);
        message.setText("将删除“" + scheduleName + "”。删除后会自动切换到其他课表。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(dp(4), 1f);
        panel.addView(message);
        panel.addView(dialogAction("确认删除", v -> {
            dialog.dismiss();
            deleteCurrentSchedule();
            showGlobalSettings();
        }));
        panel.addView(dialogAction("取消", v -> {
            dialog.dismiss();
            showScheduleSwitchDialog();
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void deleteCurrentSchedule() {
        List<ScheduleRepository.ScheduleEntry> schedules = scheduleRepository.loadSchedules();
        if (schedules.size() <= 1) {
            Toast.makeText(this, "至少保留一个课表", Toast.LENGTH_SHORT).show();
            return;
        }
        List<ScheduleRepository.ScheduleEntry> next = new ArrayList<>();
        String nextActiveId = "";
        for (ScheduleRepository.ScheduleEntry entry : schedules) {
            if (entry.id.equals(activeScheduleId)) {
                continue;
            }
            if (nextActiveId.length() == 0) {
                nextActiveId = entry.id;
            }
            next.add(entry);
        }
        if (next.isEmpty()) {
            Toast.makeText(this, "至少保留一个课表", Toast.LENGTH_SHORT).show();
            return;
        }
        scheduleRepository.saveSchedules(next);
        activeScheduleId = nextActiveId;
        scheduleRepository.setActiveScheduleId(nextActiveId);
        applyConfig(scheduleRepository.loadConfig(activeScheduleId));
        currentWeek = currentWeekFromDate();
        courses.clear();
        courses.addAll(scheduleRepository.loadCourses(activeScheduleId));
        updateHeader();
        renderSchedule();
        updateEmptyScheduleView();
        refreshMyPage();
        Toast.makeText(this, "已删除课表", Toast.LENGTH_SHORT).show();
    }

    private void switchSchedule(String scheduleId) {
        saveConfig();
        scheduleRepository.saveCourses(activeScheduleId, courses);
        activeScheduleId = scheduleId;
        scheduleRepository.setActiveScheduleId(scheduleId);
        applyConfig(scheduleRepository.loadConfig(activeScheduleId));
        currentWeek = currentWeekFromDate();
        courses.clear();
        courses.addAll(scheduleRepository.loadCourses(activeScheduleId));
        updateHeader();
        renderSchedule();
        updateEmptyScheduleView();
        refreshMyPage();
    }

    private void showSecuritySettings() {
        LinearLayout panel = settingsPagePanel("安全设置");
        panel.addView(sectionHeader("账号"));
        LinearLayout accountCard = settingsGroup();
        accountCard.addView(settingValueRow("年级和学校", gradeAndSchool,
                v -> showChoiceDialog(v, "年级和学校", new String[]{"2025级 · Polaris大学", "2024级 · Polaris大学", "管理员"}, gradeAndSchool,
                        value -> {
                            gradeAndSchool = value;
                            saveConfig();
                            refreshMyPage();
                            showSecuritySettings();
                        })));
        accountCard.addView(settingValueRow("登录方式", "管理员 · 无需账号登录", v -> {}));
        accountCard.addView(settingValueRow("本机数据", "课程保存在本机", v -> {}));
        panel.addView(accountCard);
        showSettingsPage("安全设置", panel);
    }

    private void changeWeek(int delta) {
        if (scheduleBoard != null) {
            scheduleBoard.captureCurrentBoardForTransition();
        }
        currentWeek = Math.max(1, Math.min(semesterWeeks, currentWeek + delta));
        updateHeader();
        renderSchedule();
        if (scheduleBoard != null) {
            scheduleBoard.playWeekTransition(delta);
        }
    }

    private void updateHeader() {
        setHeader(headerTitle(), headerSubtitle());
    }

    private String headerTitle() {
        return courses.isEmpty() ? weekSubtitle() : weekTitle();
    }

    private String headerSubtitle() {
        return courses.isEmpty() ? "" : weekSubtitle();
    }

    private String weekTitle() {
        return "第 " + currentWeek + " 周";
    }

    private String weekSubtitle() {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(System.currentTimeMillis());
        return date.get(Calendar.YEAR) + "/" + (date.get(Calendar.MONTH) + 1) + "/"
                + date.get(Calendar.DAY_OF_MONTH) + " " + weekdayText(date);
    }

    private void applyConfig(ScheduleRepository.Config config) {
        scheduleName = config.scheduleName;
        classTimeConfig = config.classTimeConfig;
        firstClassStartTime = normalizedTimeText(config.firstClassStartTime);
        classDurationMinutes = Math.max(20, Math.min(120, config.classDurationMinutes));
        classBreakMinutes = Math.max(0, Math.min(60, config.classBreakMinutes));
        classBigBreakMinutes = Math.max(0, Math.min(120, config.classBigBreakMinutes));
        afternoonStartTime = normalizedTimeText(config.afternoonStartTime);
        lateAfternoonStartTime = normalizedTimeText(config.lateAfternoonStartTime);
        firstWeekDay = config.firstWeekDay;
        gradeAndSchool = config.gradeAndSchool;
        timetableBackground = config.timetableBackground;
        backgroundImageUri = config.backgroundImageUri;
        courseSectionCount = Math.max(1, Math.min(20, config.sectionCount));
        semesterWeeks = Math.max(1, Math.min(20, config.semesterWeeks));
        showSaturday = config.showSaturday;
        showSunday = config.showSunday;
        showOutOfWeekCourses = config.showOutOfWeekCourses;
        courseCellHeight = Math.max(56, Math.min(120, config.courseCellHeight));
        courseCornerRadius = Math.max(0, Math.min(24, config.courseCornerRadius));
        courseBlockOpacity = Math.max(45, Math.min(100, config.courseBlockOpacity));
        selectedParserModel = parserModelFromConfig(config.parserModel);
        applySchoolTimeDefaults(selectedParserModel);
        collapseXautMiddleSections = config.collapseXautMiddleSections;
        timetableHeaderOpacity = Math.max(40, Math.min(100, config.timetableHeaderOpacity));
        bottomNavOpacity = Math.max(40, Math.min(100, config.bottomNavOpacity));
        bottomNavShape = normalizeBottomNavShape(config.bottomNavShape);
        bottomNavHeight = Math.max(56, Math.min(120, config.bottomNavHeight));
        bottomNavRectCornerRadius = Math.max(0, Math.min(72, config.bottomNavRectCornerRadius));
        bottomNavSplitCornerRadius = Math.max(0, Math.min(72, config.bottomNavSplitCornerRadius));
        bottomNavSideCornerRadius = Math.max(0, Math.min(72, config.bottomNavSideCornerRadius));
        bottomNavSideMargin = Math.max(0, Math.min(48, config.bottomNavSideMargin));
        shellBarsBlurEnabled = config.shellBarsBlurEnabled;
        updateVisibleDayCount();
    }

    private void saveConfig() {
        ScheduleRepository.Config config = new ScheduleRepository.Config();
        config.scheduleName = scheduleName;
        config.firstClassStartTime = firstClassStartTime;
        config.classDurationMinutes = classDurationMinutes;
        config.classBreakMinutes = classBreakMinutes;
        config.classBigBreakMinutes = classBigBreakMinutes;
        config.afternoonStartTime = afternoonStartTime;
        config.lateAfternoonStartTime = lateAfternoonStartTime;
        config.classTimeConfig = classTimeConfig;
        config.parserModel = selectedParserModel == null ? "" : selectedParserModel.name();
        config.firstWeekDay = firstWeekDay;
        config.gradeAndSchool = gradeAndSchool;
        config.timetableBackground = timetableBackground;
        config.backgroundImageUri = backgroundImageUri;
        config.darkMode = darkMode;
        config.sectionCount = courseSectionCount;
        config.semesterWeeks = semesterWeeks;
        config.showSaturday = showSaturday;
        config.showSunday = showSunday;
        config.showOutOfWeekCourses = showOutOfWeekCourses;
        config.collapseXautMiddleSections = collapseXautMiddleSections;
        config.courseCellHeight = courseCellHeight;
        config.courseCornerRadius = courseCornerRadius;
        config.courseBlockOpacity = courseBlockOpacity;
        config.timetableHeaderOpacity = timetableHeaderOpacity;
        config.bottomNavOpacity = bottomNavOpacity;
        config.bottomNavShape = bottomNavShape;
        config.bottomNavHeight = bottomNavHeight;
        config.bottomNavCornerRadius = bottomNavRectCornerRadius;
        config.bottomNavRectCornerRadius = bottomNavRectCornerRadius;
        config.bottomNavSplitCornerRadius = bottomNavSplitCornerRadius;
        config.bottomNavSideCornerRadius = bottomNavSideCornerRadius;
        config.bottomNavSideMargin = bottomNavSideMargin;
        config.shellBarsBlurEnabled = shellBarsBlurEnabled;
        scheduleRepository.saveConfig(activeScheduleId, config);
        syncActiveScheduleName();
    }

    private SchoolParserModel parserModelFromConfig(String value) {
        if (value == null || value.length() == 0) {
            return null;
        }
        try {
            return SchoolParserModel.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void saveGlobalAppearance() {
        saveConfig();
        List<ScheduleRepository.ScheduleEntry> schedules = scheduleRepository.loadSchedules();
        for (ScheduleRepository.ScheduleEntry entry : schedules) {
            copyGlobalAppearanceToSchedule(entry.id);
        }
    }

    private void copyGlobalAppearanceToSchedule(String scheduleId) {
        ScheduleRepository.Config config = scheduleRepository.loadConfig(scheduleId);
        config.timetableBackground = timetableBackground;
        config.backgroundImageUri = backgroundImageUri;
        config.courseCellHeight = courseCellHeight;
        config.courseCornerRadius = courseCornerRadius;
        config.courseBlockOpacity = courseBlockOpacity;
        config.timetableHeaderOpacity = timetableHeaderOpacity;
        config.bottomNavOpacity = bottomNavOpacity;
        config.bottomNavShape = bottomNavShape;
        config.bottomNavHeight = bottomNavHeight;
        config.bottomNavCornerRadius = bottomNavRectCornerRadius;
        config.bottomNavRectCornerRadius = bottomNavRectCornerRadius;
        config.bottomNavSplitCornerRadius = bottomNavSplitCornerRadius;
        config.bottomNavSideCornerRadius = bottomNavSideCornerRadius;
        config.bottomNavSideMargin = bottomNavSideMargin;
        config.shellBarsBlurEnabled = shellBarsBlurEnabled;
        scheduleRepository.saveConfig(scheduleId, config);
    }

    private void syncActiveScheduleName() {
        List<ScheduleRepository.ScheduleEntry> schedules = scheduleRepository.loadSchedules();
        List<ScheduleRepository.ScheduleEntry> next = new ArrayList<>();
        boolean changed = false;
        for (ScheduleRepository.ScheduleEntry entry : schedules) {
            if (entry.id.equals(activeScheduleId)) {
                next.add(new ScheduleRepository.ScheduleEntry(entry.id, scheduleName));
                changed = true;
            } else {
                next.add(entry);
            }
        }
        if (!changed) {
            next.add(new ScheduleRepository.ScheduleEntry(activeScheduleId, scheduleName));
        }
        scheduleRepository.saveSchedules(next);
    }

    private void updateVisibleDayCount() {
        visibleDayCount = 5 + (showSaturday ? 1 : 0) + (showSunday ? 1 : 0);
    }

    private int inferSectionCount(List<Course> source) {
        int max = 11;
        for (Course course : source) {
            max = Math.max(max, course.endSection);
        }
        return Math.max(1, Math.min(20, max));
    }

    private int inferSemesterWeeks(List<Course> source) {
        int max = 20;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\\s*-\\s*(\\d+)");
        for (Course course : source) {
            java.util.regex.Matcher matcher = pattern.matcher(course.weeks == null ? "" : course.weeks);
            if (matcher.find()) {
                max = Math.max(max, Integer.parseInt(matcher.group(2)));
            }
        }
        return Math.max(1, Math.min(20, max));
    }

    private long firstWeekStartMillis() {
        return weekMondayFromText(firstWeekDay).getTimeInMillis();
    }

    private String todayText() {
        Calendar date = Calendar.getInstance();
        return date.get(Calendar.YEAR) + "/" + (date.get(Calendar.MONTH) + 1) + "/"
                + date.get(Calendar.DAY_OF_MONTH);
    }

    private Calendar calendarFromText(String value) {
        Calendar date = Calendar.getInstance();
        String[] parts = value == null ? new String[0] : value.split("/");
        try {
            date.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                    Integer.parseInt(parts[2]), 0, 0, 0);
        } catch (Exception ignored) {
            date.set(2026, Calendar.MARCH, 3, 0, 0, 0);
        }
        date.set(Calendar.MILLISECOND, 0);
        return date;
    }

    private Calendar weekMondayFromText(String value) {
        Calendar date = calendarFromText(value);
        int dayOfWeek = date.get(Calendar.DAY_OF_WEEK);
        int mondayOffset = dayOfWeek == Calendar.SUNDAY ? -6 : Calendar.MONDAY - dayOfWeek;
        date.add(Calendar.DATE, mondayOffset);
        return date;
    }

    private int[] timeFromText(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})")
                .matcher(value == null ? "" : value);
        if (matcher.find()) {
            return new int[]{
                    Math.max(0, Math.min(23, Integer.parseInt(matcher.group(1)))),
                    Math.max(0, Math.min(59, Integer.parseInt(matcher.group(2))))
            };
        }
        return new int[]{8, 0};
    }

    private String normalizedTimeText(String value) {
        int[] time = timeFromText(value);
        return twoDigits(time[0]) + ":" + twoDigits(time[1]);
    }

    private String firstTimeFromClassTimeConfig(String value) {
        return normalizedTimeText(value == null || value.length() == 0 ? firstClassStartTime : value);
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private int backgroundColor() {
        if (isDarkModeActive()) {
            return color("#0D1422");
        }
        if ("纯白".equals(timetableBackground)) {
            return color("#F8FBFF");
        }
        if ("深海".equals(timetableBackground)) {
            return color("#DDE8F5");
        }
        return color("#EAF3FB");
    }

    private boolean isDarkModeActive() {
        if ("深色".equals(darkMode)) {
            return true;
        }
        if ("浅色".equals(darkMode)) {
            return false;
        }
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private int inkColor() {
        return isDarkModeActive() ? color("#EEF4FF") : color("#172033");
    }

    private int mutedColor() {
        return isDarkModeActive() ? color("#9AA8BE") : color("#667085");
    }

    private String cardColorHex() {
        return isDarkModeActive() ? "#182235" : "#F8FBFF";
    }

    private String groupColorHex() {
        return isDarkModeActive() ? "#141E30" : "#F2F0FA";
    }

    private String selectedFillHex() {
        return isDarkModeActive() ? "#FFFFFF" : "#172033";
    }

    private int bottomNavRadius() {
        if ("分散".equals(bottomNavShape)) {
            return Math.max(0, Math.min(72, bottomNavSplitCornerRadius));
        }
        if ("侧边".equals(bottomNavShape)) {
            return Math.max(0, Math.min(72, bottomNavSideCornerRadius));
        }
        return Math.max(0, Math.min(72, bottomNavRectCornerRadius));
    }

    private void setBottomNavRadiusForShape(String shape, int value) {
        int radius = Math.max(0, Math.min(72, value));
        if ("分散".equals(shape)) {
            bottomNavSplitCornerRadius = radius;
        } else if ("侧边".equals(shape)) {
            bottomNavSideCornerRadius = radius;
        } else {
            bottomNavRectCornerRadius = radius;
        }
    }

    private String normalizeBottomNavShape(String value) {
        if ("分散".equals(value) || "侧边".equals(value)) {
            return value;
        }
        return "矩形";
    }

    private int selectedTextColor() {
        return isDarkModeActive() ? color("#172033") : Color.WHITE;
    }

    private int color(String hex) {
        return Color.parseColor(hex);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String defaultWeeks() {
        return "1-20周";
    }

    private String normalizeWeeks(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() == 0 ? defaultWeeks() : text;
    }

    private int parseBounded(String value, int min, int max, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String weekdayText(Calendar date) {
        String[] names = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return names[date.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private GradientDrawable liquidGlassBg(int opacityPercent) {
        return floatingPanelBg(opacityPercent, 24);
    }

    private View glassLayer(GradientDrawable background, int radius) {
        BackdropBlurView layer = new BackdropBlurView(this);
        layer.setSourceView(contentHost);
        layer.setGlassBackground(background, dp(radius));
        layer.setBlurEnabled(shellBarsBlurEnabled, dp(18));
        return layer;
    }

    private void updateGlassLayer(View layer, GradientDrawable background, int radius) {
        if (layer instanceof BackdropBlurView) {
            BackdropBlurView blurView = (BackdropBlurView) layer;
            blurView.setSourceView(contentHost);
            blurView.setGlassBackground(background, dp(radius));
            blurView.setBlurEnabled(shellBarsBlurEnabled, dp(18));
        } else {
            layer.setBackground(background);
        }
    }

    private void syncTopGlassHeight(int contentHeight) {
        if (topPanelGlassLayer == null || contentHeight <= 0) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) topPanelGlassLayer.getLayoutParams();
        if (params.height == contentHeight) {
            return;
        }
        params.height = contentHeight;
        topPanelGlassLayer.setLayoutParams(params);
    }

    private GradientDrawable floatingPanelBg(int opacityPercent, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        int boundedOpacity = Math.max(40, Math.min(100, opacityPercent));
        boolean realBlurEnabled = shellBarsBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        int alphaPercent;
        if (realBlurEnabled && isDarkModeActive()) {
            alphaPercent = Math.round(20f + boundedOpacity * 0.36f);
        } else if (realBlurEnabled) {
            alphaPercent = Math.round(14f + boundedOpacity * 0.32f);
        } else {
            alphaPercent = boundedOpacity;
        }
        int alpha = Math.round(255f * alphaPercent / 100f);
        if (isDarkModeActive()) {
            drawable.setColor(Color.argb(alpha, 24, 34, 53));
            drawable.setStroke(dp(1), Color.argb(shellBarsBlurEnabled ? 90 : 130, 255, 255, 255));
        } else {
            drawable.setColor(Color.argb(alpha, 248, 251, 255));
            drawable.setStroke(dp(1), Color.argb(shellBarsBlurEnabled ? 150 : 190, 103, 116, 138));
        }
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable sidePanelBg(int opacityPercent, boolean roundLeft) {
        GradientDrawable drawable = floatingPanelBg(opacityPercent, 0);
        float radius = dp(bottomNavRadius());
        if (roundLeft) {
            drawable.setCornerRadii(new float[]{radius, radius, 0f, 0f, 0f, 0f, radius, radius});
        } else {
            drawable.setCornerRadii(new float[]{0f, 0f, radius, radius, radius, radius, 0f, 0f});
        }
        return drawable;
    }

    private GradientDrawable roundedBg(String hex, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(hex));
        drawable.setStroke(dp(1), Color.argb(130, 255, 255, 255));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private GradientDrawable roundedStrokeBg(String fillHex, String strokeHex, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor("transparent".equals(fillHex) ? Color.TRANSPARENT : color(fillHex));
        drawable.setStroke(dp(1), color(strokeHex));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int currentWeekFromDate() {
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(firstWeekStartMillis());
        Calendar today = Calendar.getInstance();
        long days = (today.getTimeInMillis() - base.getTimeInMillis()) / (24L * 60L * 60L * 1000L);
        int week = 1 + (int) Math.floor(days / 7.0);
        return Math.max(1, Math.min(semesterWeeks, week));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildLayout();
        renderSchedule();
    }

    private class MySettingIconView extends View {
        private final String type;
        private final int primary;
        private final int secondary;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        MySettingIconView(Context context, String type, int primary, int secondary) {
            super(context);
            this.type = type;
            this.primary = primary;
            this.secondary = secondary;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(primary);
            if ("schedule".equals(type)) {
                drawScheduleIcon(canvas, width, height);
            } else if ("settings".equals(type)) {
                drawSettingsIcon(canvas, width, height);
            } else {
                drawShieldIcon(canvas, width, height);
            }
        }

        private void drawScheduleIcon(Canvas canvas, float width, float height) {
            float left = width * 0.18f;
            float top = height * 0.2f;
            float right = width * 0.82f;
            float bottom = height * 0.8f;
            canvas.drawRoundRect(left, top, right, bottom, dp(4), dp(4), paint);
            canvas.drawLine(left, height * 0.38f, right, height * 0.38f, paint);
            canvas.drawLine(width * 0.39f, height * 0.38f, width * 0.39f, bottom, paint);
            canvas.drawLine(width * 0.61f, height * 0.38f, width * 0.61f, bottom, paint);
            canvas.drawLine(width * 0.3f, top - dp(3), width * 0.3f, top + dp(5), paint);
            canvas.drawLine(width * 0.7f, top - dp(3), width * 0.7f, top + dp(5), paint);
        }

        private void drawSettingsIcon(Canvas canvas, float width, float height) {
            float centerX = width / 2f;
            float centerY = height / 2f;
            float outer = Math.min(width, height) * 0.34f;
            float inner = Math.min(width, height) * 0.13f;
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2d * i / 8d;
                float startX = centerX + (float) Math.cos(angle) * outer * 0.78f;
                float startY = centerY + (float) Math.sin(angle) * outer * 0.78f;
                float endX = centerX + (float) Math.cos(angle) * outer;
                float endY = centerY + (float) Math.sin(angle) * outer;
                canvas.drawLine(startX, startY, endX, endY, paint);
            }
            canvas.drawCircle(centerX, centerY, outer * 0.66f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(secondary);
            canvas.drawCircle(centerX, centerY, inner, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(primary);
        }

        private void drawShieldIcon(Canvas canvas, float width, float height) {
            path.reset();
            path.moveTo(width * 0.5f, height * 0.14f);
            path.lineTo(width * 0.78f, height * 0.25f);
            path.lineTo(width * 0.74f, height * 0.56f);
            path.quadTo(width * 0.68f, height * 0.76f, width * 0.5f, height * 0.88f);
            path.quadTo(width * 0.32f, height * 0.76f, width * 0.26f, height * 0.56f);
            path.lineTo(width * 0.22f, height * 0.25f);
            path.close();
            canvas.drawPath(path, paint);
            canvas.drawLine(width * 0.39f, height * 0.5f, width * 0.47f, height * 0.6f, paint);
            canvas.drawLine(width * 0.47f, height * 0.6f, width * 0.63f, height * 0.42f, paint);
        }
    }

    private class SwitchThumbView extends View {
        private boolean checked;
        private final boolean dark;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        SwitchThumbView(Context context, boolean checked, boolean dark) {
            super(context);
            this.checked = checked;
            this.dark = dark;
        }

        void setChecked(boolean checked) {
            if (this.checked == checked) {
                return;
            }
            this.checked = checked;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float radius = height / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(checked ? color("#3E8BFF") : color(dark ? "#252B36" : "#D8DDE6"));
            canvas.drawRoundRect(0f, 0f, width, height, radius, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(checked ? color("#63A4FF") : color(dark ? "#3B424D" : "#C9D0DA"));
            float inset = getResources().getDisplayMetrics().density * 0.5f;
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            float thumbRadius = height / 2f - dp(4);
            float centerX = checked ? width - radius : radius;
            canvas.drawCircle(centerX, height / 2f, thumbRadius, paint);
        }
    }
}
