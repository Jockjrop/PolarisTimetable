package com.polaris.timetable;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
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
import com.polaris.timetable.importer.ImportReviewSummary;
import com.polaris.timetable.importer.ImageImportCommitPolicy;
import com.polaris.timetable.importer.ImageScheduleImportCoordinator;
import com.polaris.timetable.importer.ImageScheduleRecognitionResult;
import com.polaris.timetable.importer.LocalOcrScheduleRecognizer;
import com.polaris.timetable.importer.ScheduleImportConfirmation;
import com.polaris.timetable.importer.ScheduleImportPreviewData;
import com.polaris.timetable.importer.ai.AiImportIssueFormatter;
import com.polaris.timetable.importer.ai.AiScheduleImportWorkflow;
import com.polaris.timetable.importer.ai.PolarisAiPromptV1;
import com.polaris.timetable.export.ExportFileProvider;
import com.polaris.timetable.export.ScheduleImageExporter;
import com.polaris.timetable.export.SchedulePdfExporter;
import com.polaris.timetable.export.SemesterPdfExporter;
import com.polaris.timetable.model.ParseError;
import com.polaris.timetable.model.ParseResult;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.parser.SchoolParserModel;
import com.polaris.timetable.parser.ParseDiagnosticsReport;
import com.polaris.timetable.reminder.CourseReminderScheduler;
import com.polaris.timetable.sharing.ScheduleShareCodec;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.time.CourseTimeResolver;
import com.polaris.timetable.validation.CourseConflictDetector;
import com.polaris.timetable.ui.BackdropBlurView;
import com.polaris.timetable.ui.BackgroundCropView;
import com.polaris.timetable.ui.BackgroundImageCrop;
import com.polaris.timetable.ui.BackgroundImageLoader;
import com.polaris.timetable.ui.CircleAvatarView;
import com.polaris.timetable.ui.CourseDetailDialog;
import com.polaris.timetable.ui.CourseEditorDialog;
import com.polaris.timetable.ui.CourseConflictSummaryView;
import com.polaris.timetable.ui.PolarisThemeBackgroundView;
import com.polaris.timetable.ui.PolarisVisualTheme;
import com.polaris.timetable.ui.ScheduleBoardView;
import com.polaris.timetable.ui.TodayOverviewView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private interface BooleanSetter {
        void set(boolean value);
    }

    private interface StringSetter {
        void set(String value);
    }

    private static final class ImportDestination {
        final String scheduleId;
        final String scheduleName;
        final SchoolParserModel parserModel;
        final boolean createNewSchedule;
        final List<Course> existingCourses;

        ImportDestination(String scheduleId, String scheduleName,
                          SchoolParserModel parserModel, boolean createNewSchedule,
                          List<Course> existingCourses) {
            this.scheduleId = scheduleId == null ? "" : scheduleId;
            this.scheduleName = scheduleName == null ? "" : scheduleName;
            this.parserModel = parserModel;
            this.createNewSchedule = createNewSchedule;
            this.existingCourses = Collections.unmodifiableList(new ArrayList<>(
                    existingCourses == null
                            ? Collections.<Course>emptyList() : existingCourses));
        }
    }

    private static final int PICK_PDF = 1001;
    private static final int PICK_BACKGROUND_IMAGE = 1002;
    private static final int PICK_AVATAR_IMAGE = 1003;
    private static final int PICK_SCHEDULE_IMAGE = 1004;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 2001;
    private static final String TAG_SETTINGS_GROUP = "settings_group";
    private static final String TAG_SECTION_HEADER = "section_header";
    private static final String TAG_SETTING_LABEL = "setting_label";
    private static final String TAG_SETTING_VALUE = "setting_value";
    private static final String TAG_SWITCH_THUMB = "switch_thumb";
    private static final String TAG_SETTINGS_HEADER = "settings_header";
    private static final String TAG_SETTINGS_SCROLL = "settings_scroll";
    private static final int BOTTOM_NAV_SCROLL_THRESHOLD_DP = 12;
    private static final int BOTTOM_NAV_HIDE_DURATION_MS = 160;
    private static final int BOTTOM_NAV_SHOW_DURATION_MS = 220;
    private static final int SETTINGS_PAGE_EXIT_DURATION_MS = 170;
    private static final int SETTINGS_PAGE_REVEAL_OFFSET_DP = 16;
    private static final int ACTION_PANEL_OPACITY_PERCENT = 70;
    private static final String UI_ONBOARDING_PREFERENCES = "polaris_ui_onboarding";
    private static final String WEEK_SWIPE_HINT_SHOWN = "week_swipe_hint_shown_v1";
    private static final String[] APPEARANCE_PRESETS = {"标准", "紧凑", "沉浸"};
    private final List<StructuredCourse> structuredCourses = new ArrayList<>();
    private final List<Course> courses = new ArrayList<>();
    private final CourseStructureMapper courseStructureMapper = new CourseStructureMapper();
    private final AiScheduleImportWorkflow aiScheduleImportWorkflow =
            new AiScheduleImportWorkflow();
    private final ExecutorService scheduleExportExecutor = Executors.newSingleThreadExecutor();
    private ScheduleRepository scheduleRepository;
    private PdfImportCoordinator importCoordinator;
    private ImageScheduleImportCoordinator imageScheduleImportCoordinator;
    private ScheduleBoardView scheduleBoard;
    private FrameLayout contentHost;
    private FrameLayout rootView;
    private PolarisThemeBackgroundView themeBackgroundView;
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
    private TextView returnCurrentWeekButton;
    private Button overflowMenuButton;
    private TodayOverviewView todayOverviewView;
    private CourseConflictSummaryView conflictSummaryView;
    private int currentWeek = 18;
    private int visibleDayCount = 7;
    private int activeTab = 0;
    private String semesterName = "";
    private String schoolName = "";
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
    private boolean remindersEnabled = false;
    private int reminderMinutesBefore = 15;
    private boolean showSaturday = false;
    private boolean showSunday = false;
    private boolean showOutOfWeekCourses = true;
    private boolean showPracticeBanner = true;
    private boolean collapseLunchBreak = false;
    private int courseCellHeight = 76;
    private int courseCornerRadius = 9;
    private int courseBlockOpacity = 100;
    private int timetableHeaderOpacity = 78;
    private int bottomNavOpacity = 86;
    private String bottomNavShape = "矩形";
    private int bottomNavHeight = 60;
    private int bottomNavRectCornerRadius = 58;
    private int bottomNavSplitCornerRadius = 58;
    private int bottomNavSideCornerRadius = 58;
    private int bottomNavSideMargin = 10;
    private boolean shellBarsBlurEnabled = true;
    private String timetableBackground = "清爽蓝";
    private String visualTheme = PolarisVisualTheme.MINIMAL;
    private String backgroundImageUri = "";
    private BackgroundImageCrop backgroundImageCrop = BackgroundImageCrop.full();
    private String accountName = "管理员";
    private String avatarImageUri = "";
    private BackgroundImageCrop avatarImageCrop = BackgroundImageCrop.full();
    private String draftAvatarImageUri = "";
    private BackgroundImageCrop draftAvatarImageCrop = BackgroundImageCrop.full();
    private Dialog accountProfileDialog;
    private EditText accountNameInput;
    private CircleAvatarView accountAvatarPreview;
    private String darkMode = "跟随系统";
    private String currentTitle = "Polaris课程表";
    private String currentSubtitle = "2026/7/1";
    private String activeSettingsTitle = "";
    private String previousSettingsTitle = "";
    private String activeScheduleId = "default";
    private boolean suppressSettingsPageAnimation = false;
    private SchoolParserModel selectedParserModel;
    private boolean collapseXautMiddleSections = false;
    private LinearLayout courseManageListContainer;
    private View backgroundSettingRow;
    private View appearancePresetSettingRow;
    private boolean bottomNavHidden;
    private boolean scheduleExportInProgress;
    private boolean pdfImportInProgress;
    private boolean imageImportInProgress;
    private String lastParseDiagnosticsSummary = "暂无导入记录";
    private String lastParseDiagnosticsText = "";
    private int bottomNavScrollAccumulator;
    private boolean weekSwipeHintScheduled;
    private View weekSwipeHintView;
    private View courseSaveUndoView;
    private final Handler todayOverviewHandler = new Handler(Looper.getMainLooper());
    private final Runnable todayOverviewTicker = new Runnable() {
        @Override
        public void run() {
            updateTodayOverview();
            long untilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L) + 250L;
            todayOverviewHandler.postDelayed(this, untilNextMinute);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scheduleRepository = new ScheduleRepository(this);
        importCoordinator = new PdfImportCoordinator(this);
        imageScheduleImportCoordinator = new ImageScheduleImportCoordinator(
                this, new LocalOcrScheduleRecognizer());
        activeScheduleId = scheduleRepository.activeScheduleId();
        darkMode = scheduleRepository.loadGlobalDarkMode();
        applyAccountProfile(scheduleRepository.loadAccountProfile());
        applyConfig(scheduleRepository.loadConfig(activeScheduleId));
        currentWeek = currentWeekFromDate();
        loadActiveCourses();
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
        rescheduleCourseReminders();
        renderSchedule();
        if (scheduleBoard != null) {
            scheduleBoard.post(this::renderSchedule);
        }
        startTodayOverviewTicker();
        refreshActiveSettingsPage();
        scheduleWeekSwipeHintIfNeeded();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) {
            return;
        }
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            enableCourseReminders();
        } else {
            remindersEnabled = false;
            CourseReminderScheduler.cancelAll(this);
            refreshActiveSettingsPage();
            Toast.makeText(this, "未获得通知权限，课程提醒没有开启", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        todayOverviewHandler.removeCallbacks(todayOverviewTicker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scheduleExportExecutor.shutdownNow();
        imageScheduleImportCoordinator.close();
        super.onDestroy();
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
        weekSwipeHintView = null;
        weekSwipeHintScheduled = false;
        courseSaveUndoView = null;

        rootView = new FrameLayout(this);
        rootView.setBackgroundColor(backgroundColor());
        themeBackgroundView = new PolarisThemeBackgroundView(this);
        themeBackgroundView.setVisualTheme(visualTheme, isDarkModeActive());

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
        returnCurrentWeekButton = topHeaderAction(
                getString(R.string.return_to_current_week), v -> returnToCurrentWeek());
        returnCurrentWeekButton.setContentDescription(
                getString(R.string.return_to_current_week));
        returnCurrentWeekButton.setVisibility(View.GONE);
        actions.addView(returnCurrentWeekButton);
        overflowMenuButton = transparentTopButton("⋯", v -> {
            v.animate().rotationBy(90f).scaleX(0.88f).scaleY(0.88f).setDuration(90)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                    .start();
            showActionPanel(v);
        });
        overflowMenuButton.setContentDescription("更多操作");
        actions.addView(overflowMenuButton);

        scheduleBoard = new ScheduleBoardView(this);
        scheduleBoard.setOnCourseClickListener(this::showCourseDetail);
        scheduleBoard.setOnCourseLongClickListener(this::showCourseEditor);
        scheduleBoard.setOnPracticeBannerClickListener(this::showPracticeCourses);
        scheduleBoard.setOnVerticalScrollListener(this::handleScheduleVerticalScroll);
        scheduleBoard.setOnSlotLongClickListener((day, section) ->
                showCourseEditor(new Course(day, section, section, "", defaultWeeks(), "", "", "")));
        scheduleBoard.setOnWeekSwipeListener(this::changeWeek);
        scheduleBoard.setWeekBounds(1, semesterWeeks);
        scheduleBoard.setCurrentWeek(currentWeek);
        scheduleBoard.setVisibleDays(showSaturday, showSunday);
        scheduleBoard.setSectionCount(courseSectionCount);
        scheduleBoard.setFirstWeekStartMillis(firstWeekStartMillis());
        scheduleBoard.setClassTimeSettings(firstClassStartTime, classDurationMinutes, classBreakMinutes,
                classBigBreakMinutes, afternoonStartTime, lateAfternoonStartTime, classTimeConfig);
        scheduleBoard.setCollapseLunchBreak(collapseLunchBreak);
        scheduleBoard.setVisualTheme(visualTheme);
        scheduleBoard.setDarkMode(isDarkModeActive());
        scheduleBoard.setShowOutOfWeekCourses(showOutOfWeekCourses);
        scheduleBoard.setShowPracticeBanner(showPracticeBanner);
        scheduleBoard.setCourseMetrics(courseCellHeight, courseCornerRadius);
        scheduleBoard.setCourseBlockOpacity(courseBlockOpacity);
        scheduleBoard.setOverlayInsets(scheduleOverlayTopInset(tablet), bottomContentInset());
        scheduleBoard.setBackgroundImage(backgroundImageUri, backgroundImageCrop);
        scheduleBoard.setCourses(courses);

        todayOverviewView = new TodayOverviewView(this);
        todayOverviewView.setVisualTheme(visualTheme);
        todayOverviewView.setOnCourseClickListener(this::showCourseDetail);
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        overviewParams.topMargin = dp(5);
        topPanel.addView(todayOverviewView, overviewParams);
        updateTodayOverview();

        conflictSummaryView = new CourseConflictSummaryView(this);
        conflictSummaryView.setOnClickListener(v -> showCurrentWeekConflicts());
        LinearLayout.LayoutParams conflictParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        conflictParams.topMargin = dp(5);
        topPanel.addView(conflictSummaryView, conflictParams);
        updateConflictSummary();

        myPage = buildMyPage();
        settingsPage = new FrameLayout(this);
        settingsPage.setVisibility(View.GONE);
        contentHost = new FrameLayout(this);
        contentHost.addView(themeBackgroundView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
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
                updateTopPanelLayout(bottom - top));
        rootView.addView(topPanelContainer, topParams);

        bottomNavView = bottomNav();
        rootView.addView(bottomNavView, bottomNavLayoutParams());
        setContentView(rootView);
        updateSystemBarAppearance(getWindow());
        switchTab(activeTab);
        scheduleBoard.post(this::renderSchedule);
        scheduleWeekSwipeHintIfNeeded();
    }

    private void openPdfPicker() {
        if (selectedParserModel == null) {
            showParserModelDialog(this::launchPdfPicker);
            return;
        }
        launchPdfPicker();
    }

    private void launchPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, PICK_PDF);
    }

    private void openScheduleImagePicker() {
        if (imageImportInProgress) {
            Toast.makeText(this, "图片正在识别，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_SCHEDULE_IMAGE);
        } catch (RuntimeException exception) {
            Toast.makeText(this, "无法打开图片选择器", Toast.LENGTH_LONG).show();
        }
    }

    private void showAiImportDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("AI 识别导入");

        TextView instruction = new TextView(this);
        instruction.setText("1. 复制识别指令\n"
                + "2. 在任意支持图片的 AI 中发送指令和课表截图\n"
                + "3. 复制 AI 返回结果\n"
                + "4. 回到 Polaris 粘贴并预览");
        instruction.setTextColor(mutedColor());
        instruction.setTextSize(14);
        instruction.setLineSpacing(dp(3), 1f);
        instruction.setPadding(0, 0, 0, dp(6));
        panel.addView(instruction);

        panel.addView(dialogAction("复制 AI 识别指令", v -> copyAiRecognitionPrompt()));

        EditText input = new EditText(this);
        input.setHint("将 AI 返回内容粘贴到这里");
        input.setTextColor(inkColor());
        input.setHintTextColor(mutedColor());
        input.setTextSize(15);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setSingleLine(false);
        input.setHorizontallyScrolling(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(roundedBg(groupColorHex(), 12));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200_000)});
        input.setContentDescription("AI 返回内容输入框");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190));
        inputParams.setMargins(0, dp(8), 0, dp(4));
        panel.addView(input, inputParams);

        TextView errorView = new TextView(this);
        errorView.setTextColor(Color.parseColor(
                isDarkModeActive() ? "#FFC266" : "#8A4B00"));
        errorView.setTextSize(14);
        errorView.setLineSpacing(dp(3), 1f);
        errorView.setPadding(dp(12), dp(10), dp(12), dp(10));
        errorView.setBackground(roundedBg(groupColorHex(), 12));
        errorView.setTextIsSelectable(true);
        errorView.setVisibility(View.GONE);
        panel.addView(errorView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        panel.addView(dialogAction("从剪贴板粘贴",
                v -> pasteAiTextFromClipboard(input, errorView)));

        TextView parse = dialogAction("解析并预览", v -> {
            String aiText = input.getText() == null
                    ? "" : input.getText().toString();
            if (aiText.trim().isEmpty()) {
                showAiImportErrors(errorView,
                        Collections.singletonList("请先粘贴或输入 AI 返回内容"));
                return;
            }
            v.setEnabled(false);
            AiScheduleImportWorkflow.PrepareResult prepared =
                    aiScheduleImportWorkflow.prepare(aiText);
            if (prepared instanceof AiScheduleImportWorkflow.PrepareFailure) {
                List<String> messages = AiImportIssueFormatter.formatAll(
                        ((AiScheduleImportWorkflow.PrepareFailure) prepared).errors);
                showAiImportErrors(errorView, messages);
                v.setEnabled(true);
                return;
            }
            dialog.dismiss();
            showAiImportPreview((AiScheduleImportWorkflow.PrepareSuccess) prepared);
        });
        parse.setTextColor(Color.WHITE);
        parse.setBackground(roundedBg(primaryActionFillHex(), 14));
        parse.setContentDescription("解析 AI 返回内容并打开课程预览");
        panel.addView(parse);
        panel.addView(dialogAction("取消", v -> dialog.dismiss()));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(glassDialogContent(scroll, panel, 22));
        dialog.show();
        transparentDialog(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void copyAiRecognitionPrompt() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "无法访问系统剪贴板", Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "Polaris AI 识别指令", PolarisAiPromptV1.getPrompt()));
        Toast.makeText(this, "AI 识别指令已复制", Toast.LENGTH_SHORT).show();
    }

    private void pasteAiTextFromClipboard(EditText input, TextView errorView) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        CharSequence text = clip == null || clip.getItemCount() == 0
                ? null : clip.getItemAt(0).getText();
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, "剪贴板中没有可用文本", Toast.LENGTH_SHORT).show();
            return;
        }
        input.setText(text);
        input.setSelection(input.length());
        errorView.setVisibility(View.GONE);
        input.announceForAccessibility("已从剪贴板粘贴 AI 返回内容");
    }

    private void showAiImportErrors(TextView errorView, List<String> messages) {
        StringBuilder text = new StringBuilder("请检查以下问题：");
        if (messages == null || messages.isEmpty()) {
            text.append("\nAI 识别结果无法解析");
        } else {
            for (String message : messages) {
                text.append("\n\n").append(message);
            }
        }
        errorView.setText(text.toString());
        errorView.setContentDescription("AI 导入错误，" + text);
        errorView.setVisibility(View.VISIBLE);
        errorView.announceForAccessibility(text);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == PICK_PDF) {
            startPdfImportFlow(data.getData());
        } else if (requestCode == PICK_SCHEDULE_IMAGE) {
            startImageImportFlow(data.getData());
        } else if (requestCode == PICK_BACKGROUND_IMAGE) {
            previewBackgroundImage(data.getData());
        } else if (requestCode == PICK_AVATAR_IMAGE) {
            if (accountProfileDialog == null || !accountProfileDialog.isShowing()) {
                showAccountProfileEditor();
            }
            previewAvatarImage(data.getData());
        }
    }

    private void startImageImportFlow(Uri uri) {
        if (imageImportInProgress) {
            Toast.makeText(this, "图片正在识别，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        imageImportInProgress = true;
        try {
            imageScheduleImportCoordinator.prepare(uri,
                    new ImageScheduleImportCoordinator.Callback() {
                @Override
                public void onStarted(String displayName) {
                    setHeader(displayName, "正在进行本地文字识别…");
                }

                @Override
                public void onPreviewReady(String displayName, Bitmap preview,
                                           ImageScheduleRecognitionResult result) {
                    imageImportInProgress = false;
                    if (isFinishing()
                            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                            && isDestroyed())) {
                        if (!preview.isRecycled()) {
                            preview.recycle();
                        }
                        return;
                    }
                    updateHeader();
                    showImageImportPreview(displayName, preview, result);
                }

                @Override
                public void onFailed(Exception exception) {
                    imageImportInProgress = false;
                    if (isFinishing()
                            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                            && isDestroyed())) {
                        return;
                    }
                    updateHeader();
                    String reason = exception.getMessage() == null
                            ? "未知原因" : exception.getMessage();
                    Toast.makeText(MainActivity.this, "本地图片识别失败：" + reason,
                            Toast.LENGTH_LONG).show();
                }
            });
        } catch (RuntimeException exception) {
            imageImportInProgress = false;
            updateHeader();
            String reason = exception.getMessage() == null
                    ? "未知原因" : exception.getMessage();
            Toast.makeText(this, "无法准备图片：" + reason, Toast.LENGTH_LONG).show();
        }
    }

    private void showImageImportPreview(String displayName, Bitmap preview,
                                        ImageScheduleRecognitionResult result) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("预览课表图片");
        ScheduleImportPreviewData previewData = ScheduleImportPreviewData.basic(
                "本地图片识别", null, result.structuredCourses, result.warnings);
        ScheduleImportConfirmation confirmation = new ScheduleImportConfirmation();

        TextView notice = new TextView(this);
        notice.setText(displayName + "\n" + result.notice);
        notice.setTextColor(mutedColor());
        notice.setTextSize(14);
        notice.setGravity(Gravity.CENTER);
        notice.setLineSpacing(dp(3), 1f);
        panel.addView(notice, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(preview);
        image.setBackground(roundedBg(cardColorHex(), 14));
        image.setPadding(dp(6), dp(6), dp(6), dp(6));
        image.setContentDescription("所选课表图片预览：" + displayName);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
        imageParams.setMargins(0, dp(12), 0, dp(8));
        panel.addView(image, imageParams);

        panel.addView(importReviewLine("置信度",
                Math.round(result.confidence * 100f) + "%",
                result.confidence < ImageScheduleRecognitionResult.MIN_IMPORT_CONFIDENCE));
        addScheduleImportCandidatePreview(panel, previewData);

        boolean replacingExisting = !structuredCourses.isEmpty();
        TextView warning = new TextView(this);
        warning.setText(replacingExisting
                ? "确认后将覆盖当前课表。取消不会修改任何课程数据。"
                : "确认后将保存到当前课表。取消不会修改任何课程数据。");
        warning.setTextColor(replacingExisting
                ? Color.parseColor(isDarkModeActive() ? "#FFC266" : "#9A5B00")
                : mutedColor());
        warning.setTextSize(14);
        warning.setLineSpacing(dp(3), 1f);
        warning.setPadding(0, dp(10), 0, dp(4));
        panel.addView(warning);

        if (result.isImportable()) {
            String confirmText = replacingExisting ? "确认并覆盖当前课表" : "确认导入课表";
            TextView confirm = dialogAction(confirmText, v -> {
                v.setEnabled(false);
                confirmation.confirm(previewData, candidates -> {
                    dialog.dismiss();
                    applyImageRecognition(result, true);
                });
            });
            confirm.setTextColor(Color.WHITE);
            confirm.setBackground(roundedBg(primaryActionFillHex(), 14));
            confirm.setContentDescription(confirmText + "，确认后才会写入课程数据");
            panel.addView(confirm);
        } else {
            panel.addView(importReviewLine("导入状态",
                    "当前结果不满足安全导入条件，当前课表不会改变", true));
        }
        panel.addView(dialogAction(result.isImportable() ? "取消导入" : "关闭", v -> {
            confirmation.cancel();
            dialog.dismiss();
        }));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(ignored -> confirmation.cancel());
        dialog.setOnDismissListener(ignored -> {
            confirmation.cancel();
            image.setImageDrawable(null);
            if (!preview.isRecycled()) {
                preview.recycle();
            }
        });
        dialog.setContentView(glassDialogContent(scroll, panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void applyImageRecognition(ImageScheduleRecognitionResult result,
                                       boolean confirmed) {
        if (!ImageImportCommitPolicy.canCommit(confirmed, result)) {
            Toast.makeText(this, "识别结果为空或置信度不足，已取消写入以保护当前课表",
                    Toast.LENGTH_LONG).show();
            return;
        }
        applyCurrentScheduleImport(
                result.structuredCourses,
                "本地图片识别 · " + result.structuredCourses.size()
                        + " 门课程 · " + result.meetingCount() + " 条安排",
                "识别来源：本地 ML Kit OCR\n"
                + "文件写入：已由用户确认\n"
                + "课程数量：" + result.structuredCourses.size() + "\n"
                + "上课安排：" + result.meetingCount() + "\n"
                + "置信度：" + Math.round(result.confidence * 100f) + "%\n"
                + "说明：" + result.notice,
                "图片识别结果已保存并显示在课表中");
    }

    private void showAiImportPreview(AiScheduleImportWorkflow.PrepareSuccess prepared) {
        ScheduleImportPreviewData preview = prepared.preview;
        if (preview == null || preview.isEmpty()) {
            Toast.makeText(this, "没有识别到可导入课程", Toast.LENGTH_LONG).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("检查 AI 识别结果");
        addScheduleImportCandidatePreview(panel, preview);

        boolean replacingExisting = !structuredCourses.isEmpty();
        TextView replacementNotice = new TextView(this);
        replacementNotice.setText(replacingExisting
                ? "确认后将覆盖当前正在查看的课表。识别学期和教学班仅用于本次预览。"
                : "确认后将导入当前正在查看的课表。识别学期和教学班仅用于本次预览。");
        replacementNotice.setTextColor(replacingExisting
                ? Color.parseColor(isDarkModeActive() ? "#FFC266" : "#8A4B00")
                : mutedColor());
        replacementNotice.setTextSize(14);
        replacementNotice.setLineSpacing(dp(3), 1f);
        replacementNotice.setPadding(0, dp(12), 0, dp(4));
        panel.addView(replacementNotice);

        ScheduleImportConfirmation confirmation = new ScheduleImportConfirmation();
        String confirmText = replacingExisting ? "确认并覆盖当前课表" : "确认导入";
        TextView confirm = dialogAction(confirmText, v -> {
            if (isFinishing()
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                    && isDestroyed())) {
                confirmation.cancel();
                dialog.dismiss();
                return;
            }
            v.setEnabled(false);
            confirmation.confirm(preview, candidates -> {
                dialog.dismiss();
                int courseCount = candidates.size();
                applyCurrentScheduleImport(
                        candidates,
                        "外部 AI 识别 · " + courseCount + " 门课程 · "
                                + preview.meetingCount() + " 条安排",
                        "识别来源：外部多模态 AI\n"
                                + "协议：Polaris Schedule JSON v1\n"
                                + "文件写入：已由用户预览并确认\n"
                                + "课程数量：" + courseCount + "\n"
                                + "上课安排：" + preview.meetingCount(),
                        "课表导入成功，共 " + courseCount + " 门课程");
            });
        });
        confirm.setTextColor(Color.WHITE);
        confirm.setBackground(roundedBg(primaryActionFillHex(), 14));
        confirm.setContentDescription(confirmText + "，确认后才会覆盖当前课表数据");
        panel.addView(confirm);
        panel.addView(dialogAction("取消", v -> {
            confirmation.cancel();
            dialog.dismiss();
        }));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(ignored -> confirmation.cancel());
        dialog.setOnDismissListener(ignored -> confirmation.cancel());
        dialog.setContentView(glassDialogContent(scroll, panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void addScheduleImportCandidatePreview(
            LinearLayout panel, ScheduleImportPreviewData preview) {
        panel.addView(importReviewHeading("课程候选"));
        panel.addView(importReviewLine("识别到",
                preview.regularCourseCount() + " 门普通课程 · "
                        + preview.practiceCourseCount() + " 门实践课程",
                false));
        if (preview.semester != null && !preview.semester.trim().isEmpty()) {
            panel.addView(importReviewLine("识别学期", preview.semester, false));
        }

        for (StructuredCourse course : preview.courses) {
            if (course != null) {
                panel.addView(scheduleImportCourseCard(course, preview));
            }
        }

        if (!preview.warnings.isEmpty()) {
            panel.addView(importReviewHeading("需要检查"));
            for (String warning : preview.warnings) {
                panel.addView(importReviewLine("提示", warning, true));
            }
        }
    }

    private View scheduleImportCourseCard(StructuredCourse course,
                                          ScheduleImportPreviewData preview) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedBg(groupColorHex(), 14));
        card.setContentDescription("待导入课程，" + course.name);

        TextView name = new TextView(this);
        name.setText(course.name);
        name.setTextColor(inkColor());
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(false);
        card.addView(name);

        if (!course.teacher.trim().isEmpty()) {
            card.addView(scheduleImportDetail("教师：" + course.teacher, false));
        }
        if (!course.credit.trim().isEmpty()) {
            card.addView(scheduleImportDetail("学分：" + course.credit, false));
        }
        for (ScheduleImportPreviewData.Detail detail : preview.detailsFor(course)) {
            String note = detail.note.isEmpty() ? "" : "（" + detail.note + "）";
            card.addView(scheduleImportDetail(
                    detail.label + "：" + detail.value + note, false));
        }

        for (CourseMeeting meeting : course.meetings) {
            if (meeting == null) {
                continue;
            }
            card.addView(scheduleImportDetail(
                    scheduleImportMeetingText(course, meeting), true));
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, dp(6));
        card.setLayoutParams(params);
        return card;
    }

    private TextView scheduleImportDetail(String text, boolean meeting) {
        TextView detail = new TextView(this);
        detail.setText(text);
        detail.setTextColor(meeting ? inkColor() : mutedColor());
        detail.setTextSize(meeting ? 14 : 13);
        detail.setLineSpacing(dp(2), 1f);
        detail.setSingleLine(false);
        detail.setPadding(0, meeting ? dp(8) : dp(3), 0, 0);
        return detail;
    }

    private String scheduleImportMeetingText(StructuredCourse course,
                                             CourseMeeting meeting) {
        String weeks = meeting.weekRule == null
                ? "周次待确认" : meeting.weekRule.displayText();
        if (course.courseType == CourseType.PRACTICE
                && meeting.timeMode == CourseTimeMode.NONE) {
            return weeks + "\n无固定上课时间";
        }

        StringBuilder text = new StringBuilder();
        text.append(dayName(meeting.day)).append(' ');
        if (meeting.hasExactTime()) {
            text.append(twoDigits(meeting.startMinuteOfDay / 60)).append(':')
                    .append(twoDigits(meeting.startMinuteOfDay % 60)).append('-')
                    .append(twoDigits(meeting.endMinuteOfDay / 60)).append(':')
                    .append(twoDigits(meeting.endMinuteOfDay % 60));
        } else {
            text.append(meeting.startSection).append('-')
                    .append(meeting.endSection).append("节");
        }
        text.append('\n').append(weeks);
        if (!meeting.location.trim().isEmpty()) {
            text.append('\n').append(meeting.location);
        }
        return text.toString();
    }

    private void applyCurrentScheduleImport(List<StructuredCourse> importedCourses,
                                            String diagnosticsSummary,
                                            String diagnosticsText,
                                            String successMessage) {
        if (importedCourses == null || importedCourses.isEmpty()) {
            Toast.makeText(this, "没有识别到可导入课程", Toast.LENGTH_LONG).show();
            return;
        }
        replaceCanonicalCourses(importedCourses);
        semesterWeeks = inferSemesterWeeks(courses);
        courseSectionCount = inferSectionCount(courses);
        currentWeek = Math.max(1, Math.min(semesterWeeks, currentWeekFromDate()));
        lastParseDiagnosticsSummary = diagnosticsSummary == null ? "" : diagnosticsSummary;
        lastParseDiagnosticsText = diagnosticsText == null ? "" : diagnosticsText;
        scheduleRepository.saveStructuredCourses(activeScheduleId, structuredCourses);
        saveConfig();
        rescheduleCourseReminders();
        updateHeader();
        renderSchedule();
        updateEmptyScheduleView();
        refreshCourseManageList();
        refreshMyPage();
        switchTab(0);
        Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
    }

    private void startPdfImportFlow(Uri uri) {
        if (selectedParserModel == null) {
            showParserModelDialog(() -> startPdfImportFlow(uri));
            return;
        }
        if (courses.isEmpty()) {
            loadPdf(uri, new ImportDestination(activeScheduleId, scheduleName,
                    selectedParserModel, false, Collections.<Course>emptyList()));
            return;
        }
        showImportOverwriteDialog(uri);
    }

    private void showImportOverwriteDialog(Uri uri) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("导入到当前课表？");
        final SchoolParserModel[] importParserModel = {selectedParserModel};
        TextView message = new TextView(this);
        message.setText("先解析 PDF 并检查课程、缺失字段和冲突；只有在检查页再次确认后，才会覆盖当前课表。请先选择学校解析模型。");
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
                    parserChoice[0].setText("学校：" + model.label);
                    chooser.dismiss();
                }));
            }
            chooser.setContentView(glassDialogContent(chooserPanel, 22));
            chooser.show();
            transparentDialog(chooser);
        });
        panel.addView(parserChoice[0]);
        TextView cover = dialogAction("解析并检查", v -> {
            if (importParserModel[0] == null) {
                Toast.makeText(this, "请先选择学校解析模型", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            loadPdf(uri, new ImportDestination(activeScheduleId, scheduleName,
                    importParserModel[0], false, courses));
        });
        TextView create = dialogAction("新建课表并检查", v -> {
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
        TextView startImport = dialogAction("开始解析并检查", v -> {
            String name = nameInput.getText().toString().trim();
            if (name.length() == 0) {
                Toast.makeText(this, "课表名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            loadPdf(uri, new ImportDestination(activeScheduleId, name,
                    parserModel, createNewSchedule,
                    createNewSchedule ? Collections.<Course>emptyList() : courses));
        });
        startImport.setTextColor(Color.WHITE);
        startImport.setBackground(roundedBg(primaryActionFillHex(), 14));
        panel.addView(startImport);
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private String nextScheduleName() {
        int index = scheduleRepository.loadSchedules().size() + 1;
        return "新课表" + index;
    }

    private void loadPdf(Uri uri, ImportDestination destination) {
        if (pdfImportInProgress) {
            Toast.makeText(this, "课表正在解析，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        pdfImportInProgress = true;
        try {
            importCoordinator.importPdf(uri, destination.parserModel,
                    new PdfImportCoordinator.Callback() {
            @Override
            public void onImportStarted(String displayName) {
                setHeader(displayName, "正在解析课表...");
            }

            @Override
            public void onImportParsed(ParseResult result) {
                pdfImportInProgress = false;
                lastParseDiagnosticsSummary = ParseDiagnosticsReport.summary(result);
                lastParseDiagnosticsText = ParseDiagnosticsReport.build(result);
                updateHeader();
                renderSchedule();
                updateEmptyScheduleView();
                showImportReviewDialog(result, destination);
            }

            @Override
            public void onImportFailed(Exception exception) {
                pdfImportInProgress = false;
                String reason = exception.getMessage() == null
                        ? "未知原因" : exception.getMessage();
                lastParseDiagnosticsSummary = "解析失败";
                lastParseDiagnosticsText = "解析状态：失败\n原因：" + reason;
                setHeader(currentTitle, "导入失败");
                Toast.makeText(MainActivity.this, "PDF解析失败：" + reason,
                        Toast.LENGTH_LONG).show();
            }
            });
        } catch (RuntimeException exception) {
            pdfImportInProgress = false;
            String reason = exception.getMessage() == null
                    ? "未知原因" : exception.getMessage();
            setHeader(currentTitle, "导入失败");
            Toast.makeText(this, "无法启动 PDF 解析：" + reason,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showImportReviewDialog(ParseResult result,
                                        ImportDestination destination) {
        int importedSemesterWeeks = inferSemesterWeeks(result.courses);
        List<Course> comparisonCourses = destination.existingCourses;
        boolean replacingExisting = !destination.createNewSchedule
                && !comparisonCourses.isEmpty();
        ImportReviewSummary review = ImportReviewSummary.analyze(
                result, comparisonCourses, importedSemesterWeeks);

        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("检查导入结果");
        TextView summary = new TextView(this);
        summary.setText(review.canImport()
                ? "识别到 " + review.courseCount + " 门课程 · "
                        + review.meetingCount + " 条上课安排"
                : "没有识别到可导入的课程");
        summary.setTextColor(inkColor());
        summary.setTextSize(18);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(0, 0, 0, dp(10));
        panel.addView(summary);

        String importedSemester = result.semesterName.length() > 0
                ? result.semesterName
                : SemesterStartDateDefaults.resolveSemesterName(Calendar.getInstance());
        panel.addView(importReviewLine("学校", destination.parserModel.label, false));
        panel.addView(importReviewLine("学期", importedSemester, false));
        panel.addView(importReviewLine("PDF 页数", Math.max(0, result.pageCount) + " 页", false));
        panel.addView(importReviewLine("开学日期", "确认后设置", false));

        panel.addView(importReviewHeading(destination.createNewSchedule
                ? "将创建新课表"
                : replacingExisting ? "与当前课表对比" : "将导入当前课表"));
        panel.addView(importReviewLine("新增安排", review.addedCount + " 条", false));
        panel.addView(importReviewLine("修改安排", review.modifiedCount + " 条",
                review.modifiedCount > 0));
        panel.addView(importReviewLine("移除安排", review.removedCount + " 条",
                review.removedCount > 0));

        panel.addView(importReviewHeading("需要检查"));
        if (!review.hasIssues()) {
            panel.addView(importReviewLine("检查结果", "未发现明显问题", false));
        } else {
            if (review.errorCount > 0) {
                panel.addView(importReviewLine("解析错误", review.errorCount + " 条", true));
            }
            if (review.warningCount > 0) {
                panel.addView(importReviewLine("解析提示", review.warningCount + " 条", true));
            }
            if (review.unknownWeekCount > 0) {
                panel.addView(importReviewLine("周次不确定",
                        review.unknownWeekCount + " 条安排", true));
            }
            if (review.missingLocationCount > 0) {
                panel.addView(importReviewLine("缺少教室",
                        review.missingLocationCount + " 条安排", true));
            }
            if (review.missingTeacherCount > 0) {
                panel.addView(importReviewLine("缺少教师",
                        review.missingTeacherCount + " 条安排", true));
            }
            if (review.conflictCount > 0) {
                panel.addView(importReviewLine("课程冲突",
                        review.conflictCount + " 组", true));
            }
        }

        if (lastParseDiagnosticsText.length() > 0) {
            panel.addView(dialogAction("查看解析诊断", v -> showParseDiagnosticsDialog()));
        }
        if (review.canImport()) {
            String confirmText = destination.createNewSchedule
                    ? "确认并创建新课表"
                    : replacingExisting ? "确认并覆盖当前课表" : "确认导入课表";
            TextView confirm = dialogAction(confirmText, v -> {
                dialog.dismiss();
                applyReviewedImport(result, destination, importedSemesterWeeks);
            });
            confirm.setTextColor(Color.WHITE);
            confirm.setBackground(roundedBg(primaryActionFillHex(), 14));
            confirm.setContentDescription(confirmText + "，此操作将在确认后写入课程数据");
            panel.addView(confirm);
        } else {
            TextView blocked = new TextView(this);
            blocked.setText("当前课表不会发生任何变更。请查看解析诊断后重新选择 PDF。");
            blocked.setTextColor(mutedColor());
            blocked.setTextSize(14);
            blocked.setLineSpacing(dp(3), 1f);
            blocked.setPadding(0, dp(8), 0, dp(4));
            panel.addView(blocked);
        }
        panel.addView(dialogAction(review.canImport() ? "取消导入" : "关闭",
                v -> dialog.dismiss()));

        ScrollView reviewScroll = new ScrollView(this);
        reviewScroll.setFillViewport(false);
        reviewScroll.setVerticalScrollBarEnabled(true);
        reviewScroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(glassDialogContent(reviewScroll, panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private TextView importReviewHeading(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextColor(inkColor());
        heading.setTextSize(14);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, dp(14), 0, dp(5));
        return heading;
    }

    private TextView importReviewLine(String label, String value, boolean needsAttention) {
        TextView line = new TextView(this);
        line.setText(label + "：" + value);
        line.setTextColor(needsAttention
                ? Color.parseColor(isDarkModeActive() ? "#FFC266" : "#9A5B00")
                : mutedColor());
        line.setTextSize(14);
        line.setSingleLine(false);
        line.setLineSpacing(dp(2), 1f);
        line.setPadding(0, dp(3), 0, dp(3));
        line.setContentDescription(needsAttention
                ? "需要检查，" + label + "，" + value : label + "，" + value);
        return line;
    }

    private void applyReviewedImport(ParseResult result,
                                     ImportDestination destination,
                                     int importedSemesterWeeks) {
        if (!result.courses.isEmpty() && result.structuredCourses.isEmpty()) {
            Toast.makeText(this,
                    "结构化课程数据不可用，已取消写入以保护当前课表", Toast.LENGTH_LONG).show();
            return;
        }
        if (destination.createNewSchedule) {
            ScheduleRepository.ScheduleEntry entry = scheduleRepository.createSchedule(
                    destination.scheduleName);
            copyGlobalAppearanceToSchedule(entry.id);
            switchSchedule(entry.id);
        } else if (!destination.scheduleId.equals(activeScheduleId)) {
            switchSchedule(destination.scheduleId);
        }
        replaceCanonicalCourses(result.structuredCourses);
        scheduleName = destination.scheduleName;
        selectedParserModel = destination.parserModel;
        schoolName = destination.parserModel.label;
        semesterName = result.semesterName.length() > 0
                ? result.semesterName
                : SemesterStartDateDefaults.resolveSemesterName(Calendar.getInstance());
        applySchoolTimeDefaults(destination.parserModel);
        semesterWeeks = importedSemesterWeeks;
        firstWeekDay = SemesterStartDateDefaults.resolve(Calendar.getInstance());
        currentWeek = currentWeekFromDate();
        updateVisibleDayCount();
        scheduleRepository.saveStructuredCourses(activeScheduleId, structuredCourses);
        loadActiveCourses();
        saveConfig();
        refreshMyPage();
        updateHeader();
        renderSchedule();
        updateEmptyScheduleView();
        showImportedFirstWeekDayDialog();
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
        updateReturnCurrentWeekAction();
    }

    private void renderSchedule() {
        if (scheduleBoard != null) {
            boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
            scheduleBoard.setWeekBounds(1, semesterWeeks);
            scheduleBoard.setCurrentWeek(currentWeek);
            scheduleBoard.setVisibleDays(showSaturday, showSunday);
            scheduleBoard.setSectionCount(courseSectionCount);
            scheduleBoard.setFirstWeekStartMillis(firstWeekStartMillis());
            scheduleBoard.setClassTimeSettings(firstClassStartTime, classDurationMinutes, classBreakMinutes,
                    classBigBreakMinutes, afternoonStartTime, lateAfternoonStartTime, classTimeConfig);
            scheduleBoard.setCollapseLunchBreak(collapseLunchBreak);
            scheduleBoard.setVisualTheme(visualTheme);
            scheduleBoard.setDarkMode(isDarkModeActive());
            scheduleBoard.setShowOutOfWeekCourses(showOutOfWeekCourses);
            scheduleBoard.setShowPracticeBanner(showPracticeBanner);
            scheduleBoard.setCourseMetrics(courseCellHeight, courseCornerRadius);
            scheduleBoard.setCourseBlockOpacity(courseBlockOpacity);
            scheduleBoard.setOverlayInsets(scheduleOverlayTopInset(tablet), bottomContentInset());
            scheduleBoard.setBackgroundImage(backgroundImageUri, backgroundImageCrop);
            scheduleBoard.setCourses(courses);
        }
        if (todayOverviewView != null) {
            todayOverviewView.setVisualTheme(visualTheme);
        }
        updateTodayOverview();
        updateConflictSummary();
        updateEmptyScheduleView();
        updateReturnCurrentWeekAction();
    }

    private void showCourseDetail(Course course) {
        if (course == null) {
            return;
        }
        new CourseDetailDialog(this, isDarkModeActive(), dialogBlurSource(), courseTimeSettings())
                .show(course, this::showCourseEditor);
    }

    private CourseTimeResolver.Settings courseTimeSettings() {
        return new CourseTimeResolver.Settings(
                firstClassStartTime,
                classDurationMinutes,
                classBreakMinutes,
                classBigBreakMinutes,
                afternoonStartTime,
                lateAfternoonStartTime,
                classTimeConfig);
    }

    private void updateTodayOverview() {
        if (todayOverviewView == null) {
            return;
        }
        CourseTimeResolver.TodayOverview overview = CourseTimeResolver.resolveToday(
                courses,
                courseTimeSettings(),
                firstWeekStartMillis(),
                semesterWeeks,
                Calendar.getInstance());
        todayOverviewView.setOverview(overview, isDarkModeActive());
    }

    private void startTodayOverviewTicker() {
        todayOverviewHandler.removeCallbacks(todayOverviewTicker);
        todayOverviewTicker.run();
    }

    private void updateConflictSummary() {
        if (conflictSummaryView == null) {
            return;
        }
        int count = CourseConflictDetector.forWeek(
                courses, semesterWeeks, currentWeek, courseTimeSettings()).size();
        conflictSummaryView.setConflictCount(count, currentWeek, isDarkModeActive());
    }

    private void showCurrentWeekConflicts() {
        List<CourseConflictDetector.Conflict> conflicts = CourseConflictDetector.forWeek(
                courses, semesterWeeks, currentWeek, courseTimeSettings());
        if (conflicts.isEmpty()) {
            updateConflictSummary();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("第 " + currentWeek + " 周课程冲突");

        TextView explanation = new TextView(this);
        explanation.setText("以下课程在当前周的上课时间重叠。点击一组冲突，可选择要编辑的课程。");
        explanation.setTextColor(mutedColor());
        explanation.setTextSize(14);
        explanation.setPadding(0, 0, 0, dp(8));
        panel.addView(explanation, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(conflicts.size() > 4);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        for (CourseConflictDetector.Conflict conflict : conflicts) {
            TextView item = conflictDialogItem(conflict, v ->
                    showConflictCourseChoice(dialog, conflict));
            list.addView(item);
        }
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(dp(360), Math.max(dp(80), Math.min(4, conflicts.size()) * dp(84))));
        panel.addView(scroll, scrollParams);
        panel.addView(dialogAction("关闭", v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private TextView conflictDialogItem(CourseConflictDetector.Conflict conflict,
                                        View.OnClickListener listener) {
        String firstName = safeCourseName(conflict.first);
        String secondName = safeCourseName(conflict.second);
        String section = conflict.overlapTimeText();
        TextView item = dialogAction(
                firstName + "  ×  " + secondName + "\n"
                        + dayText(conflict.day) + " " + section + " · "
                        + conflict.commonWeeksText(),
                listener);
        item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        item.setTextSize(14);
        item.setSingleLine(false);
        item.setMaxLines(3);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76));
        params.setMargins(0, dp(5), 0, dp(5));
        item.setLayoutParams(params);
        item.setContentDescription(firstName + "与" + secondName + "时间冲突，"
                + dayText(conflict.day) + section + "，" + conflict.commonWeeksText()
                + "，点击选择要编辑的课程");
        return item;
    }

    private void showConflictCourseChoice(Dialog conflictDialog,
                                          CourseConflictDetector.Conflict conflict) {
        Dialog chooser = new Dialog(this);
        LinearLayout panel = dialogPanel("选择要编辑的课程");
        TextView firstAction = dialogAction("编辑：" + courseChoiceText(conflict.first), v -> {
            chooser.dismiss();
            conflictDialog.dismiss();
            showCourseEditor(conflict.first);
        });
        firstAction.setSingleLine(true);
        firstAction.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(firstAction);
        TextView secondAction = dialogAction("编辑：" + courseChoiceText(conflict.second), v -> {
            chooser.dismiss();
            conflictDialog.dismiss();
            showCourseEditor(conflict.second);
        });
        secondAction.setSingleLine(true);
        secondAction.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(secondAction);
        panel.addView(dialogAction("取消", v -> chooser.dismiss()));
        chooser.setContentView(glassDialogContent(panel, 22));
        chooser.show();
        transparentDialog(chooser);
    }

    private String courseChoiceText(Course course) {
        String location = course == null || course.location == null
                ? "" : course.location.trim();
        return location.isEmpty() ? safeCourseName(course)
                : safeCourseName(course) + "（" + location + "）";
    }

    private String safeCourseName(Course course) {
        if (course == null || course.name == null || course.name.trim().isEmpty()) {
            return "未命名课程";
        }
        return course.name.trim();
    }

    private String dayText(int day) {
        String[] days = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return day >= 0 && day < days.length ? days[day] : "日期待定";
    }

    private int scheduleOverlayTopInset(boolean tablet) {
        if (topPanelContainer != null && topPanelContainer.getHeight() > 0
                && topPanelContainer.getVisibility() == View.VISIBLE) {
            return topPanelContainer.getTop() + topPanelContainer.getHeight() + dp(8);
        }
        return statusBarHeight() + dp(tablet ? 172 : 160);
    }

    private View buildEmptyScheduleView() {
        FrameLayout layer = new FrameLayout(this);
        layer.setPadding(dp(20), statusBarHeight() + dp(70), dp(20), bottomContentInset() + dp(48));
        layer.setBackgroundColor(pageSurfaceColor());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(24), dp(28), dp(24), dp(28));
        card.setBackground(roundedBg(cardColorHex(), isMinimalVisualTheme() ? 18 : 22));
        if (!isMinimalVisualTheme()) {
            applyThemeElevation(card, 4);
        }

        TextView titleView = new TextView(this);
        titleView.setText("还没有课程");
        titleView.setTextColor(inkColor());
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView);

        TextView message = new TextView(this);
        message.setText("选择学校后导入教务系统 PDF，Polaris 会解析课程，并在写入前让你检查结果。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, dp(10), 0, 0);
        card.addView(message, messageParams);

        TextView importButton = new TextView(this);
        importButton.setText(R.string.import_schedule);
        importButton.setContentDescription(getString(R.string.import_schedule));
        importButton.setTextColor(Color.WHITE);
        importButton.setTextSize(17);
        importButton.setTypeface(Typeface.DEFAULT_BOLD);
        importButton.setGravity(Gravity.CENTER);
        importButton.setMinHeight(dp(52));
        importButton.setBackground(roundedBg(primaryActionFillHex(), 15));
        importButton.setOnClickListener(v -> openPdfPicker());
        attachPressFeedback(importButton);
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        importParams.setMargins(0, dp(20), 0, 0);
        card.addView(importButton, importParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        layer.addView(card, cardParams);
        return layer;
    }

    private void showParserModelDialog() {
        showParserModelDialog(null);
    }

    private void showParserModelDialog(Runnable onSelected) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("选择学校");
        TextView message = new TextView(this);
        message.setText("选择你的学校后，Polaris 会按对应的课表格式识别课程和上课时间。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(dp(4), 1f);
        panel.addView(message);
        for (SchoolParserModel model : SchoolParserModel.values()) {
            panel.addView(dialogAction(model.label, v -> {
                selectedParserModel = model;
                schoolName = model.label;
                applySchoolTimeDefaults(model);
                saveConfig();
                renderSchedule();
                dialog.dismiss();
                refreshActiveSettingsPage();
                refreshMyPage();
                if (onSelected != null) {
                    onSelected.run();
                }
            }));
        }
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
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

    private void applyBackgroundImage(Uri uri, BackgroundImageCrop crop) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Background provider granted only temporary read access", exception);
        }
        String nextBackgroundUri = uri.toString();
        BackgroundImageCrop nextCrop = crop == null ? BackgroundImageCrop.full() : crop;
        if (scheduleBoard != null && !scheduleBoard.setBackgroundImage(nextBackgroundUri, nextCrop)) {
            scheduleBoard.setBackgroundImage(backgroundImageUri, backgroundImageCrop);
            Toast.makeText(this, "无法读取所选图片，请重新选择", Toast.LENGTH_LONG).show();
            return;
        }
        backgroundImageUri = nextBackgroundUri;
        backgroundImageCrop = nextCrop;
        timetableBackground = "系统相册";
        saveGlobalAppearance();
        applyShellAppearance();
        renderSchedule();
        refreshMyPageBehindSettings();
        updateSettingValueRow(backgroundSettingRow, "系统相册");
        refreshVisibleSettingsTheme();
    }

    private void previewBackgroundImage(Uri uri) {
        int targetWidth = scheduleBoard != null && scheduleBoard.getWidth() > 0
                ? scheduleBoard.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int targetHeight = scheduleBoard != null && scheduleBoard.getHeight() > 0
                ? scheduleBoard.getHeight() : getResources().getDisplayMetrics().heightPixels;
        Bitmap previewBitmap;
        try {
            previewBitmap = BackgroundImageLoader.decode(
                    this, uri, Math.max(1, targetWidth), Math.max(1, targetHeight));
        } catch (Exception exception) {
            Log.w(TAG, "Unable to preview selected background", exception);
            Toast.makeText(this, "无法预览所选图片，请重新选择", Toast.LENGTH_LONG).show();
            return;
        } catch (OutOfMemoryError error) {
            Log.e(TAG, "Insufficient memory for background preview", error);
            Toast.makeText(this, "图片尺寸过大，无法作为课表背景", Toast.LENGTH_LONG).show();
            return;
        }
        if (previewBitmap == null) {
            Log.w(TAG, "Background preview decoder returned no drawable");
            Toast.makeText(this, "无法预览所选图片，请重新选择", Toast.LENGTH_LONG).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("调整展示区域");

        TextView instruction = new TextView(this);
        instruction.setText("单指拖动图片，双指缩放；白色框内将作为课表背景");
        instruction.setTextColor(mutedColor());
        instruction.setTextSize(14);
        instruction.setGravity(Gravity.CENTER);
        instruction.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        instructionParams.setMargins(0, 0, 0, dp(10));
        panel.addView(instruction, instructionParams);

        float targetAspect = targetWidth / (float) Math.max(1, targetHeight);
        BackgroundCropView cropView = new BackgroundCropView(this, previewBitmap, targetAspect);
        cropView.setBackground(roundedBg("#101827", 18));
        cropView.setClipToOutline(true);
        int previewHeight = Math.min(dp(420), Math.max(dp(200),
                getResources().getDisplayMetrics().heightPixels - dp(260)));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, previewHeight);
        imageParams.setMargins(0, 0, 0, dp(12));
        panel.addView(cropView, imageParams);

        Button use = new Button(this);
        use.setText("应用此展示区域");
        use.setContentDescription("应用当前背景展示区域");
        use.setTextColor(Color.WHITE);
        use.setTypeface(Typeface.DEFAULT_BOLD);
        use.setBackground(roundedBg(primaryActionFillHex(), 14));
        use.setOnClickListener(v -> {
            BackgroundImageCrop selection = cropView.getCropSelection();
            dialog.dismiss();
            applyBackgroundImage(uri, selection);
        });
        panel.addView(use, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        Button chooseAgain = new Button(this);
        chooseAgain.setText("重新选择");
        chooseAgain.setTextColor(inkColor());
        chooseAgain.setBackground(roundedBg(cardColorHex(), 14));
        chooseAgain.setOnClickListener(v -> {
            dialog.dismiss();
            openBackgroundPicker();
        });
        LinearLayout.LayoutParams againParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        againParams.topMargin = dp(8);
        panel.addView(chooseAgain, againParams);

        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setFillViewport(true);
        dialogScroll.setClipToPadding(false);
        dialogScroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(glassDialogContent(dialogScroll, panel, 22));
        dialog.setOnDismissListener(ignored -> cropView.releaseBitmap());
        dialog.show();
        transparentDialog(dialog);
    }

    private void showAccountProfileEditor() {
        if (accountProfileDialog != null && accountProfileDialog.isShowing()) {
            return;
        }
        draftAvatarImageUri = avatarImageUri;
        draftAvatarImageCrop = avatarImageCrop;

        Dialog dialog = new Dialog(this);
        accountProfileDialog = dialog;
        LinearLayout panel = dialogPanel("编辑账户资料");

        accountAvatarPreview = new CircleAvatarView(this);
        accountAvatarPreview.setProfile(accountName, draftAvatarImageUri, draftAvatarImageCrop);
        accountAvatarPreview.setPlaceholderColor(
                isDarkModeActive() ? color("#31527D") : color("#172033"));
        accountAvatarPreview.setContentDescription("选择并裁剪头像");
        accountAvatarPreview.setClickable(true);
        accountAvatarPreview.setFocusable(true);
        accountAvatarPreview.setOnClickListener(v -> openAvatarPicker());
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        avatarParams.bottomMargin = dp(8);
        panel.addView(accountAvatarPreview, avatarParams);

        TextView avatarHint = new TextView(this);
        avatarHint.setText("点击头像可从相册选择并裁剪");
        avatarHint.setTextColor(mutedColor());
        avatarHint.setTextSize(13);
        avatarHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.bottomMargin = dp(12);
        panel.addView(avatarHint, hintParams);

        accountNameInput = input("账户名称", accountName);
        accountNameInput.setSingleLine(true);
        accountNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        panel.addView(accountNameInput);

        Button chooseAvatar = new Button(this);
        chooseAvatar.setText("从相册选择头像");
        chooseAvatar.setTextColor(inkColor());
        chooseAvatar.setBackground(roundedBg(cardColorHex(), 14));
        chooseAvatar.setOnClickListener(v -> openAvatarPicker());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        chooseParams.topMargin = dp(10);
        panel.addView(chooseAvatar, chooseParams);

        panel.addView(pageSaveButton(() -> saveAccountProfile(dialog)));

        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setTextColor(mutedColor());
        cancel.setBackgroundColor(Color.TRANSPARENT);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        cancelParams.topMargin = dp(4);
        panel.addView(cancel, cancelParams);

        dialog.setOnDismissListener(ignored -> {
            if (accountProfileDialog == dialog) {
                accountProfileDialog = null;
                accountNameInput = null;
                accountAvatarPreview = null;
            }
        });
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void openAvatarPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_AVATAR_IMAGE);
    }

    private void previewAvatarImage(Uri uri) {
        Bitmap previewBitmap;
        try {
            previewBitmap = BackgroundImageLoader.decode(this, uri, dp(640), dp(640));
        } catch (Exception exception) {
            Log.w(TAG, "Unable to preview selected avatar", exception);
            Toast.makeText(this, "无法预览所选头像，请重新选择", Toast.LENGTH_LONG).show();
            return;
        } catch (OutOfMemoryError error) {
            Log.e(TAG, "Insufficient memory for avatar preview", error);
            Toast.makeText(this, "图片尺寸过大，无法设置为头像", Toast.LENGTH_LONG).show();
            return;
        }
        if (previewBitmap == null) {
            Toast.makeText(this, "无法预览所选头像，请重新选择", Toast.LENGTH_LONG).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("裁剪头像");

        TextView instruction = new TextView(this);
        instruction.setText("单指拖动，双指缩放；圆形区域将作为头像");
        instruction.setTextColor(mutedColor());
        instruction.setTextSize(14);
        instruction.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        instructionParams.bottomMargin = dp(10);
        panel.addView(instruction, instructionParams);

        BackgroundCropView cropView = new BackgroundCropView(this, previewBitmap, 1f, true);
        cropView.setContentDescription("头像裁剪区域，可单指拖动、双指缩放");
        cropView.setBackground(roundedBg("#101827", 18));
        cropView.setClipToOutline(true);
        LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(310));
        cropParams.bottomMargin = dp(12);
        panel.addView(cropView, cropParams);

        Button use = new Button(this);
        use.setText("使用此头像");
        use.setTextColor(Color.WHITE);
        use.setTypeface(Typeface.DEFAULT_BOLD);
        use.setBackground(roundedBg(primaryActionFillHex(), 14));
        use.setOnClickListener(v -> {
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException | IllegalArgumentException exception) {
                Log.w(TAG, "Avatar provider granted only temporary read access", exception);
            }
            draftAvatarImageUri = uri.toString();
            draftAvatarImageCrop = cropView.getCropSelection();
            if (accountAvatarPreview != null) {
                String draftName = accountNameInput == null
                        ? accountName : accountNameInput.getText().toString();
                accountAvatarPreview.setProfile(
                        draftName, draftAvatarImageUri, draftAvatarImageCrop);
            }
            dialog.dismiss();
        });
        panel.addView(use, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setTextColor(mutedColor());
        cancel.setBackgroundColor(Color.TRANSPARENT);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        cancelParams.topMargin = dp(4);
        panel.addView(cancel, cancelParams);

        ScrollView dialogScroll = new ScrollView(this);
        dialogScroll.setFillViewport(true);
        dialogScroll.setClipToPadding(false);
        dialogScroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setOnDismissListener(ignored -> cropView.releaseBitmap());
        dialog.setContentView(glassDialogContent(dialogScroll, panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void saveAccountProfile(Dialog dialog) {
        String nextName = accountNameInput == null
                ? accountName : accountNameInput.getText().toString().trim();
        if (nextName.length() == 0) {
            Toast.makeText(this, "账户名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        accountName = nextName;
        avatarImageUri = draftAvatarImageUri;
        avatarImageCrop = draftAvatarImageCrop;

        ScheduleRepository.AccountProfile profile = new ScheduleRepository.AccountProfile();
        profile.name = accountName;
        profile.avatarUri = avatarImageUri;
        profile.cropLeft = avatarImageCrop.left;
        profile.cropTop = avatarImageCrop.top;
        profile.cropRight = avatarImageCrop.right;
        profile.cropBottom = avatarImageCrop.bottom;
        scheduleRepository.saveAccountProfile(profile);

        dialog.dismiss();
        refreshMyPage();
        Toast.makeText(this, "账户资料已保存", Toast.LENGTH_SHORT).show();
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
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private TextView topHeaderAction(String text, View.OnClickListener listener) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setTextColor(inkColor());
        action.setTextSize(13);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(12), 0, dp(12), 0);
        action.setMinHeight(dp(40));
        action.setBackground(roundedBg(cardColorHex(), 14));
        action.setOnClickListener(listener);
        attachPressFeedback(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
        params.rightMargin = dp(4);
        action.setLayoutParams(params);
        return action;
    }

    private void returnToCurrentWeek() {
        int targetWeek = currentWeekFromDate();
        if (targetWeek == currentWeek) {
            updateReturnCurrentWeekAction();
            return;
        }
        int delta = targetWeek - currentWeek;
        if (scheduleBoard != null) {
            scheduleBoard.captureCurrentBoardForTransition();
        }
        currentWeek = targetWeek;
        updateHeader();
        renderSchedule();
        if (scheduleBoard != null) {
            scheduleBoard.playWeekTransition(delta);
        }
    }

    private void updateReturnCurrentWeekAction() {
        if (returnCurrentWeekButton == null) {
            return;
        }
        boolean show = !courses.isEmpty() && currentWeek != currentWeekFromDate();
        returnCurrentWeekButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void scheduleWeekSwipeHintIfNeeded() {
        if (weekSwipeHintScheduled || weekSwipeHintView != null || courseSaveUndoView != null
                || rootView == null
                || activeTab != 0 || courses.isEmpty()
                || getSharedPreferences(UI_ONBOARDING_PREFERENCES, MODE_PRIVATE)
                        .getBoolean(WEEK_SWIPE_HINT_SHOWN, false)) {
            return;
        }
        weekSwipeHintScheduled = true;
        rootView.postDelayed(() -> {
            weekSwipeHintScheduled = false;
            if (rootView == null || activeTab != 0 || courses.isEmpty() || !hasWindowFocus()
                    || getSharedPreferences(UI_ONBOARDING_PREFERENCES, MODE_PRIVATE)
                            .getBoolean(WEEK_SWIPE_HINT_SHOWN, false)) {
                return;
            }
            showWeekSwipeHint();
        }, 700L);
    }

    private void showWeekSwipeHint() {
        if (rootView == null || weekSwipeHintView != null) {
            return;
        }
        getSharedPreferences(UI_ONBOARDING_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(WEEK_SWIPE_HINT_SHOWN, true)
                .apply();

        TextView hint = new TextView(this);
        hint.setText(R.string.week_swipe_hint);
        hint.setContentDescription(getString(R.string.week_swipe_hint));
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(14);
        hint.setTypeface(Typeface.DEFAULT_BOLD);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(18), 0, dp(18), 0);
        hint.setMinHeight(dp(48));
        GradientDrawable background = new GradientDrawable();
        background.setColor(isDarkModeActive() ? color("#E61F73E0") : color("#E6172033"));
        background.setCornerRadius(dp(24));
        hint.setBackground(background);
        hint.setAlpha(0f);
        hint.setTranslationY(dp(8));
        hint.setOnClickListener(v -> dismissWeekSwipeHint(hint));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(48),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = bottomContentInset() + dp(18);
        rootView.addView(hint, params);
        weekSwipeHintView = hint;
        hint.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .start();
        hint.announceForAccessibility(getString(R.string.week_swipe_hint));
        hint.postDelayed(() -> dismissWeekSwipeHint(hint), 4200L);
    }

    private void dismissWeekSwipeHint(View hint) {
        if (hint == null || weekSwipeHintView != hint) {
            return;
        }
        hint.animate()
                .alpha(0f)
                .translationY(dp(8))
                .setDuration(180L)
                .withEndAction(() -> {
                    if (hint.getParent() instanceof ViewGroup) {
                        ((ViewGroup) hint.getParent()).removeView(hint);
                    }
                    if (weekSwipeHintView == hint) {
                        weekSwipeHintView = null;
                    }
                })
                .start();
    }

    private void showCourseSavedUndo(List<StructuredCourse> previousCourses) {
        if (rootView == null || previousCourses == null) {
            return;
        }
        if (courseSaveUndoView != null && courseSaveUndoView.getParent() instanceof ViewGroup) {
            ((ViewGroup) courseSaveUndoView.getParent()).removeView(courseSaveUndoView);
        }
        if (weekSwipeHintView != null) {
            dismissWeekSwipeHint(weekSwipeHintView);
        }

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), 0, dp(8), 0);
        bar.setContentDescription("课程已保存，可以撤销本次保存");
        GradientDrawable background = new GradientDrawable();
        background.setColor(isDarkModeActive() ? color("#EE1F2D43") : color("#EE172033"));
        background.setCornerRadius(dp(18));
        bar.setBackground(background);

        TextView saved = new TextView(this);
        saved.setText(R.string.course_saved);
        saved.setTextColor(Color.WHITE);
        saved.setTextSize(15);
        saved.setTypeface(Typeface.DEFAULT_BOLD);
        saved.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(saved, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView undo = new TextView(this);
        undo.setText(R.string.undo);
        undo.setTextColor(color("#8CC8FF"));
        undo.setTextSize(15);
        undo.setTypeface(Typeface.DEFAULT_BOLD);
        undo.setGravity(Gravity.CENTER);
        undo.setMinWidth(dp(64));
        undo.setContentDescription("撤销本次课程保存");
        undo.setOnClickListener(v -> restoreCoursesFromUndo(previousCourses, bar));
        bar.addView(undo, new LinearLayout.LayoutParams(dp(72), dp(48)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(52),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.setMargins(dp(16), 0, dp(16), bottomContentInset() + dp(18));
        bar.setAlpha(0f);
        bar.setTranslationY(dp(8));
        rootView.addView(bar, params);
        courseSaveUndoView = bar;
        bar.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        bar.announceForAccessibility("课程已保存，可以撤销");
        bar.postDelayed(() -> dismissCourseSaveUndo(bar), 6000L);
    }

    private void restoreCoursesFromUndo(
            List<StructuredCourse> previousCourses, View undoBar) {
        if (courseSaveUndoView != undoBar) {
            return;
        }
        structuredCourses.clear();
        structuredCourses.addAll(previousCourses);
        refreshCourseView();
        scheduleRepository.saveStructuredCourses(activeScheduleId, structuredCourses);
        rescheduleCourseReminders();
        renderSchedule();
        updateEmptyScheduleView();
        refreshCourseManageList();
        dismissCourseSaveUndo(undoBar);
        Toast.makeText(this, R.string.course_save_undone, Toast.LENGTH_SHORT).show();
    }

    private void dismissCourseSaveUndo(View undoBar) {
        if (undoBar == null || courseSaveUndoView != undoBar) {
            return;
        }
        undoBar.animate()
                .alpha(0f)
                .translationY(dp(8))
                .setDuration(160L)
                .withEndAction(() -> {
                    if (undoBar.getParent() instanceof ViewGroup) {
                        ((ViewGroup) undoBar.getParent()).removeView(undoBar);
                    }
                    if (courseSaveUndoView == undoBar) {
                        courseSaveUndoView = null;
                    }
                    scheduleWeekSwipeHintIfNeeded();
                })
                .start();
    }

    private void showActionPanel(View anchor) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(6), dp(6), dp(6), dp(6));
        panel.setBackground(dialogGlassBg(18, ACTION_PANEL_OPACITY_PERCENT));

        PopupWindow popup = new PopupWindow();
        panel.addView(popupMenuAction("导入 PDF", v -> {
            popup.dismiss();
            openPdfPicker();
        }));
        panel.addView(popupMenuAction("导入课表图片", v -> {
            popup.dismiss();
            openScheduleImagePicker();
        }));
        panel.addView(popupMenuAction("AI 识别导入", v -> {
            popup.dismiss();
            showAiImportDialog();
        }));
        panel.addView(popupMenuAction("导入分享链接", v -> {
            popup.dismiss();
            showSharedLinkInputDialog();
        }));
        panel.addView(popupMenuAction("手动添加课程", v -> {
            popup.dismiss();
            showCourseEditor(new Course(0, 1, 2, "", defaultWeeks(), "", "", ""));
        }));
        panel.addView(popupMenuAction("分享课表链接", v -> {
            popup.dismiss();
            shareScheduleLink();
        }));
        panel.addView(popupMenuAction("导出课表", v -> {
            popup.dismiss();
            showWeekExportDialog();
        }));
        popup.setContentView(glassDialogContent(
                panel, 18, ACTION_PANEL_OPACITY_PERCENT));
        popup.setWidth(dp(224));
        popup.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(8));
        popup.setAnimationStyle(android.R.style.Animation_Dialog);
        popup.showAtLocation(rootView == null ? anchor : rootView,
                Gravity.TOP | Gravity.RIGHT, dp(14), statusBarHeight() + dp(48));
    }

    private TextView popupMenuAction(String text, View.OnClickListener listener) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setContentDescription(text);
        action.setTextColor(inkColor());
        action.setTextSize(16);
        action.setTypeface(Typeface.DEFAULT_BOLD);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(dp(14), 0, dp(14), 0);
        action.setMinHeight(dp(50));
        action.setBackgroundColor(Color.TRANSPARENT);
        action.setOnClickListener(listener);
        attachRowPressFeedback(action);
        action.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
        return action;
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

    private void showWeekExportDialog() {
        if (scheduleExportInProgress) {
            Toast.makeText(this, "课表文件正在生成，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        int maxWeek = Math.max(1, semesterWeeks);
        final int[] selectedWeek = {
                Math.max(1, Math.min(maxWeek, currentWeek))
        };
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("导出课表");

        TextView message = new TextView(this);
        message.setText("选择周次导出单周课表，或把全学期课程合并到一张课表中导出。");
        message.setTextColor(mutedColor());
        message.setTextSize(14);
        message.setPadding(0, 0, 0, dp(8));
        panel.addView(message);

        EditText weekInput = stepInput(String.valueOf(selectedWeek[0]));
        weekInput.setContentDescription("导出周次");
        LinearLayout weekRow = new LinearLayout(this);
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        weekRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = stepButton("−");
        TextView plus = stepButton("+");
        minus.setContentDescription("上一周");
        plus.setContentDescription("下一周");
        minus.setOnClickListener(v -> {
            selectedWeek[0] = Math.max(1, parseBounded(
                    weekInput.getText().toString(), 1, maxWeek, selectedWeek[0]) - 1);
            weekInput.setText(String.valueOf(selectedWeek[0]));
            weekInput.setSelection(weekInput.getText().length());
        });
        plus.setOnClickListener(v -> {
            selectedWeek[0] = Math.min(maxWeek, parseBounded(
                    weekInput.getText().toString(), 1, maxWeek, selectedWeek[0]) + 1);
            weekInput.setText(String.valueOf(selectedWeek[0]));
            weekInput.setSelection(weekInput.getText().length());
        });
        weekRow.addView(minus);
        weekRow.addView(weekInput, new LinearLayout.LayoutParams(0, dp(46), 1f));
        weekRow.addView(plus);
        panel.addView(weekRow);

        panel.addView(dialogAction("导出指定周图片", v -> {
            int week = parseBounded(weekInput.getText().toString(),
                    1, maxWeek, selectedWeek[0]);
            dialog.dismiss();
            exportWeekImage(week);
        }));
        panel.addView(dialogAction("导出指定周 PDF", v -> {
            int week = parseBounded(weekInput.getText().toString(),
                    1, maxWeek, selectedWeek[0]);
            dialog.dismiss();
            exportWeekPdf(week);
        }));
        panel.addView(dialogAction("导出整学期 PDF", v -> {
            dialog.dismiss();
            exportSemesterPdf();
        }));
        panel.addView(dialogAction("取消", v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void exportWeekImage(int week) {
        if (scheduleExportInProgress) {
            Toast.makeText(this, "课表文件正在生成，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        ScheduleImageExporter.Request request = scheduleExportRequest(week);
        if (!ScheduleImageExporter.hasExportableContent(request)) {
            Toast.makeText(this, "本周没有课程，请切换到有课周后再导出",
                    Toast.LENGTH_LONG).show();
            return;
        }
        scheduleExportInProgress = true;
        Toast.makeText(this, "正在生成第 " + request.week + " 周课表图片…",
                Toast.LENGTH_SHORT).show();
        try {
            scheduleExportExecutor.execute(() -> {
                try {
                    File image = ScheduleImageExporter.exportToCache(
                            getApplicationContext(), request);
                    runOnUiThread(() -> {
                        scheduleExportInProgress = false;
                        if (!isFinishing() && !isDestroyed()) {
                            shareExportedScheduleImage(image, request.week);
                        }
                    });
                } catch (Exception exception) {
                    handleScheduleExportFailure(exception, "导出图片失败：");
                }
            });
        } catch (RuntimeException exception) {
            scheduleExportInProgress = false;
            Toast.makeText(this, "导出任务启动失败，请重试", Toast.LENGTH_LONG).show();
        }
    }

    private ScheduleImageExporter.Request scheduleExportRequest(int week) {
        return new ScheduleImageExporter.Request(
                courses,
                courseTimeSettings(),
                scheduleName,
                semesterName,
                schoolName,
                week,
                courseSectionCount,
                showSaturday,
                showSunday,
                showPracticeBanner,
                firstWeekStartMillis());
    }

    private void exportWeekPdf(int week) {
        if (scheduleExportInProgress) {
            Toast.makeText(this, "课表文件正在生成，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        ScheduleImageExporter.Request request = scheduleExportRequest(week);
        if (!ScheduleImageExporter.hasExportableContent(request)) {
            Toast.makeText(this, "本周没有课程，请切换到有课周后再导出",
                    Toast.LENGTH_LONG).show();
            return;
        }
        scheduleExportInProgress = true;
        Toast.makeText(this, "正在生成第 " + request.week + " 周课表 PDF…",
                Toast.LENGTH_SHORT).show();
        try {
            scheduleExportExecutor.execute(() -> {
                try {
                    File pdf = SchedulePdfExporter.exportToCache(
                            getApplicationContext(), request);
                    runOnUiThread(() -> {
                        scheduleExportInProgress = false;
                        if (!isFinishing() && !isDestroyed()) {
                            shareExportedSchedulePdf(pdf, request.week);
                        }
                    });
                } catch (Exception exception) {
                    handleScheduleExportFailure(exception, "导出 PDF 失败：");
                }
            });
        } catch (RuntimeException exception) {
            scheduleExportInProgress = false;
            Toast.makeText(this, "导出任务启动失败，请重试", Toast.LENGTH_LONG).show();
        }
    }

    private void exportSemesterPdf() {
        if (scheduleExportInProgress) {
            Toast.makeText(this, "课表文件正在生成，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (courses.isEmpty()) {
            Toast.makeText(this, "当前课表没有课程，请先导入或添加课程",
                    Toast.LENGTH_LONG).show();
            return;
        }
        ScheduleImageExporter.Request request = scheduleExportRequest(currentWeek);
        scheduleExportInProgress = true;
        Toast.makeText(this, "正在生成整学期合并课表 PDF…", Toast.LENGTH_SHORT).show();
        try {
            scheduleExportExecutor.execute(() -> {
                try {
                    File pdf = SemesterPdfExporter.exportToCache(
                            getApplicationContext(), request, semesterWeeks);
                    runOnUiThread(() -> {
                        scheduleExportInProgress = false;
                        if (!isFinishing() && !isDestroyed()) {
                            shareExportedSemesterPdf(pdf);
                        }
                    });
                } catch (Exception exception) {
                    handleScheduleExportFailure(exception, "导出整学期 PDF 失败：");
                }
            });
        } catch (RuntimeException exception) {
            scheduleExportInProgress = false;
            Toast.makeText(this, "导出任务启动失败，请重试", Toast.LENGTH_LONG).show();
        }
    }

    private void handleScheduleExportFailure(Exception exception, String prefix) {
        String reason = exception.getMessage() == null
                ? "未知原因" : exception.getMessage();
        runOnUiThread(() -> {
            scheduleExportInProgress = false;
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, prefix + reason, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void shareExportedScheduleImage(File image, int week) {
        try {
            Uri uri = ExportFileProvider.uriForFile(this, image);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("image/png");
            share.putExtra(Intent.EXTRA_SUBJECT, scheduleName + " · 第 " + week + " 周");
            share.putExtra(Intent.EXTRA_TEXT, "Polaris课程表 · 第 " + week + " 周");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri("Polaris课表图片", uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享本周课表图片"));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "图片已生成，但无法打开系统分享面板",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareExportedSchedulePdf(File pdf, int week) {
        try {
            Uri uri = ExportFileProvider.uriForFile(this, pdf);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_SUBJECT, scheduleName + " · 第 " + week + " 周");
            share.putExtra(Intent.EXTRA_TEXT, "Polaris课程表 · 第 " + week + " 周");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri("Polaris课表 PDF", uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享本周课表 PDF"));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "PDF 已生成，但无法打开系统分享面板",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareExportedSemesterPdf(File pdf) {
        try {
            Uri uri = ExportFileProvider.uriForFile(this, pdf);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_SUBJECT, scheduleName + " · 整学期合并课表");
            share.putExtra(Intent.EXTRA_TEXT, "Polaris课程表 · 整学期合并课表");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri("Polaris整学期课表 PDF", uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享整学期课表 PDF"));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "整学期 PDF 已生成，但无法打开系统分享面板",
                    Toast.LENGTH_LONG).show();
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
        replaceCanonicalCourses(scheduleRepository.replaceFromLegacyCourses(
                activeScheduleId, sharedCourses));
        scheduleName = targetScheduleName;
        semesterName = SemesterStartDateDefaults.resolveSemesterName(Calendar.getInstance());
        if (selectedParserModel != null) {
            schoolName = selectedParserModel.label;
        }
        semesterWeeks = inferSemesterWeeks(courses);
        courseSectionCount = inferSectionCount(courses);
        firstWeekDay = SemesterStartDateDefaults.resolve(Calendar.getInstance());
        currentWeek = currentWeekFromDate();
        updateVisibleDayCount();
        saveConfig();
        updateHeader();
        renderSchedule();
        updateEmptyScheduleView();
        refreshMyPage();
        Toast.makeText(this, "已导入分享课表：" + courses.size() + " 门课程", Toast.LENGTH_LONG).show();
        showImportedFirstWeekDayDialog();
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
        if (courseSaveUndoView != null) {
            dismissCourseSaveUndo(courseSaveUndoView);
        }
        new CourseEditorDialog(
                this,
                course,
                courses,
                courseSectionCount,
                courseTimeSettings(),
                backgroundColor(),
                inkColor(),
                mutedColor(),
                isDarkModeActive(),
                new CourseEditorDialog.Listener() {
                    @Override
                    public void onCourseSaved(Course original, Course edited) {
                        List<StructuredCourse> previousCourses = new ArrayList<>(structuredCourses);
                        if (!CourseEditManager.applyStructuredEdit(
                                structuredCourses, original, edited)) {
                            Toast.makeText(MainActivity.this,
                                    "课程已发生变化，请重新打开后编辑", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        refreshCourseView();
                        scheduleRepository.saveStructuredCourses(
                                activeScheduleId, structuredCourses);
                        rescheduleCourseReminders();
                        renderSchedule();
                        updateEmptyScheduleView();
                        showCourseSavedUndo(previousCourses);
                        switchTab(0);
                    }

                    @Override
                    public void onCourseDeleteRequested(Course original, CourseDeletionScope scope) {
                        int deleted = CourseDeletionManager.deleteStructured(
                                structuredCourses, original, scope, currentWeek, semesterWeeks);
                        if (deleted <= 0) {
                            Toast.makeText(MainActivity.this,
                                    "本周没有这节课程，无需删除", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        refreshCourseView();
                        scheduleRepository.saveStructuredCourses(
                                activeScheduleId, structuredCourses);
                        rescheduleCourseReminders();
                        renderSchedule();
                        updateEmptyScheduleView();
                        refreshCourseManageList();
                        switchTab(0);
                        Toast.makeText(MainActivity.this,
                                deletionSuccessMessage(scope), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onEditorDismissed() {
                        applyShellAppearance();
                    }
                })
                .show();
    }

    private String deletionSuccessMessage(CourseDeletionScope scope) {
        if (scope == CourseDeletionScope.CURRENT_WEEK) {
            return "已删除本周此节课程";
        }
        if (scope == CourseDeletionScope.CURRENT_MEETING) {
            return "已删除每周此节课程";
        }
        return "已删除该课程全部节次";
    }

    private void showPracticeCourses(List<Course> practiceCourses) {
        if (practiceCourses == null || practiceCourses.isEmpty()) {
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("本周实践");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(practiceCourses.size() > 5);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        for (Course course : practiceCourses) {
            String time = course.isBannerOnlyCourse()
                    ? "集中实践" : courseTimeInlineText(course);
            String name = course.name == null || course.name.trim().isEmpty()
                    ? "未命名实践" : course.name.trim();
            TextView item = dialogAction(name + " · " + time, v -> {
                dialog.dismiss();
                new CourseDetailDialog(this, isDarkModeActive(), dialogBlurSource(), courseTimeSettings())
                        .show(course, this::showCourseEditor);
            });
            item.setSingleLine(false);
            item.setMaxLines(2);
            item.setEllipsize(TextUtils.TruncateAt.END);
            list.addView(item);
        }
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.min(dp(320), Math.max(dp(60), practiceCourses.size() * dp(60))));
        panel.addView(scroll, scrollParams);
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
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
                params.setMargins(0, 0, -extra, bottomMargin);
            } else {
                params.setMargins(-extra, 0, 0, bottomMargin);
            }
            return params;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(navVisualHeight()), Gravity.BOTTOM);
        int side = "分散".equals(bottomNavShape)
                ? dp(Math.max(0, bottomNavSideMargin))
                : dp(tablet ? 16 : 10);
        params.setMargins(side, 0, side, bottomMargin);
        return params;
    }

    private int navVisualHeight() {
        return Math.max(56, Math.min(120, bottomNavHeight));
    }

    private int bottomContentInset() {
        return dp(navVisualHeight() + 12);
    }

    private void handleScheduleVerticalScroll(int scrollY, int deltaY, boolean atBottom) {
        if (activeTab != 0 || settingsPage != null && settingsPage.getVisibility() == View.VISIBLE) {
            return;
        }
        if (!"矩形".equals(bottomNavShape) && !"分散".equals(bottomNavShape)) {
            showBottomNav(false);
            return;
        }
        if (scrollY <= 0) {
            bottomNavScrollAccumulator = 0;
            showBottomNav(true);
            return;
        }
        if (atBottom) {
            bottomNavScrollAccumulator = 0;
            hideBottomNav();
            return;
        }
        if (deltaY > 0) {
            bottomNavScrollAccumulator = Math.max(0, bottomNavScrollAccumulator) + deltaY;
        } else if (deltaY < 0) {
            bottomNavScrollAccumulator = Math.min(0, bottomNavScrollAccumulator) + deltaY;
        }
        int threshold = dp(BOTTOM_NAV_SCROLL_THRESHOLD_DP);
        if (bottomNavScrollAccumulator >= threshold) {
            bottomNavScrollAccumulator = 0;
            hideBottomNav();
        } else if (bottomNavScrollAccumulator <= -threshold) {
            bottomNavScrollAccumulator = 0;
            showBottomNav(true);
        }
    }

    private void hideBottomNav() {
        if (bottomNavView == null || bottomNavHidden) {
            return;
        }
        bottomNavHidden = true;
        bottomNavView.animate().cancel();
        bottomNavView.animate()
                .translationY(dp(navVisualHeight() + 18))
                .setDuration(BOTTOM_NAV_HIDE_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void showBottomNav(boolean animate) {
        bottomNavHidden = false;
        bottomNavScrollAccumulator = 0;
        if (bottomNavView == null) {
            return;
        }
        bottomNavView.animate().cancel();
        bottomNavView.setVisibility(View.VISIBLE);
        if (!animate) {
            bottomNavView.setTranslationY(0f);
            return;
        }
        bottomNavView.animate()
                .translationY(0f)
                .setDuration(BOTTOM_NAV_SHOW_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
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
            bottomNavView.setLayoutParams(bottomNavLayoutParams());
            showBottomNav(false);
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
            scheduleWeekSwipeHintIfNeeded();
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
        if (bottomNavHidden && ("矩形".equals(bottomNavShape) || "分散".equals(bottomNavShape))) {
            bottomNavView.setTranslationY(dp(navVisualHeight() + 18));
        } else {
            bottomNavHidden = false;
            bottomNavView.setTranslationY(0f);
        }
    }

    private ScrollView buildMyPage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(pageSurfaceColor());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, statusBarHeight() + dp(34), 0, bottomContentInset() + dp(48));
        scrollView.addView(page);
        if (isMinimalVisualTheme()) {
            page.addView(profileHeader());
            page.addView(mySettingCard("课表设置", "schedule", v -> showScheduleSettings()));
            page.addView(mySettingCard("全局设置", "settings", v -> showGlobalSettings()));
            page.addView(mySettingCard("安全设置", "shield", v -> showSecuritySettings()));
        } else {
            page.addView(themedProfileHeader());
            page.addView(themedMySettingCard("课表设置", "schedule", v -> showScheduleSettings()));
            page.addView(themedMySettingCard("全局设置", "settings", v -> showGlobalSettings()));
            page.addView(themedMySettingCard("安全设置", "shield", v -> showSecuritySettings()));
        }
        return scrollView;
    }

    private View themedProfileHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        header.setBackground(roundedBg(cardColorHex(), 26));
        applyThemeElevation(header, 4);

        CircleAvatarView avatar = new CircleAvatarView(this);
        avatar.setProfile(accountName, avatarImageUri, avatarImageCrop);
        avatar.setPlaceholderColor(PolarisVisualTheme.accentColor(
                visualTheme, isDarkModeActive()));
        avatar.setContentDescription("修改账户名称和头像");
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setOnClickListener(v -> showAccountProfileEditor());
        header.addView(avatar, new LinearLayout.LayoutParams(dp(78), dp(78)));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        identityParams.leftMargin = dp(18);
        header.addView(identity, identityParams);

        TextView name = new TextView(this);
        name.setText(accountName);
        name.setTextColor(inkColor());
        name.setTextSize(22);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        identity.addView(name);

        TextView school = themedProfileLine(displaySchoolName());
        LinearLayout.LayoutParams schoolParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        schoolParams.topMargin = dp(6);
        identity.addView(school, schoolParams);

        TextView semester = themedProfileLine(displaySemesterName());
        LinearLayout.LayoutParams semesterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        semesterParams.topMargin = dp(3);
        identity.addView(semester, semesterParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, dp(8), 0, dp(18));
        header.setLayoutParams(params);
        return header;
    }

    private TextView themedProfileLine(String value) {
        TextView line = new TextView(this);
        line.setText(value);
        line.setTextColor(mutedColor());
        line.setTextSize(13);
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        return line;
    }

    private View themedMySettingCard(String titleText, String iconType,
                                     View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(10), dp(14), dp(10));
        card.setBackground(roundedBg(cardColorHex(), 22));
        applyThemeElevation(card, 3);
        card.setOnClickListener(listener);
        attachCardPressFeedback(card, 22);

        FrameLayout iconSurface = new FrameLayout(this);
        iconSurface.setBackground(roundedBg(PolarisVisualTheme.hex(
                PolarisVisualTheme.accentSurfaceColor(visualTheme, isDarkModeActive())), 16));
        applyThemeElevation(iconSurface, 2);
        MySettingIconView icon = new MySettingIconView(this, iconType,
                themedIconColor(iconType), mutedColor());
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dp(27), dp(27), Gravity.CENTER);
        iconSurface.addView(icon, iconParams);
        card.addView(iconSurface, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(16);
        card.addView(copy, copyParams);

        TextView label = new TextView(this);
        label.setText(titleText);
        label.setTextColor(inkColor());
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(label);

        TextView description = new TextView(this);
        description.setText(themedSettingDescription(iconType));
        description.setTextColor(mutedColor());
        description.setTextSize(12);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(3);
        copy.addView(description, descriptionParams);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(mutedColor());
        arrow.setTextSize(26);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(48)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.88f), dp(78));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private String themedSettingDescription(String iconType) {
        if ("schedule".equals(iconType)) {
            return "管理课表与上课时间";
        }
        if ("settings".equals(iconType)) {
            return "主题、通知与显示设置";
        }
        return "账户与本机数据保护";
    }

    private int themedIconColor(String iconType) {
        if ("settings".equals(iconType)) {
            return color(isDarkModeActive() ? "#70D8F0" : "#238AB4");
        }
        if ("shield".equals(iconType)) {
            return color(isDarkModeActive() ? "#7FE0B5" : "#2E8F68");
        }
        return PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive());
    }

    private View profileHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(14), 0, dp(20));

        CircleAvatarView avatar = new CircleAvatarView(this);
        avatar.setProfile(accountName, avatarImageUri, avatarImageCrop);
        avatar.setPlaceholderColor(isDarkModeActive() ? color("#31527D") : color("#172033"));
        avatar.setContentDescription("修改账户名称和头像");
        avatar.setClickable(true);
        avatar.setFocusable(true);
        avatar.setOnClickListener(v -> showAccountProfileEditor());
        header.addView(avatar, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView name = new TextView(this);
        name.setText(accountName);
        name.setTextColor(inkColor());
        name.setTextSize(20);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(10);
        header.addView(name, nameParams);

        TextView school = profileInfoLine(displaySchoolName());
        LinearLayout.LayoutParams schoolParams = new LinearLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        schoolParams.topMargin = dp(5);
        header.addView(school, schoolParams);

        TextView semester = profileInfoLine(displaySemesterName());
        LinearLayout.LayoutParams semesterParams = new LinearLayout.LayoutParams(
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        semesterParams.topMargin = dp(2);
        header.addView(semester, semesterParams);
        return header;
    }

    private TextView profileInfoLine(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(mutedColor());
        view.setTextSize(13);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private String displaySemesterName() {
        return semesterName.length() == 0 ? "未设置学期" : semesterName;
    }

    private String displaySchoolName() {
        return schoolName.length() == 0 ? "未选择学校" : schoolName;
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

    private void applyThemeElevation(View view, int elevationDp) {
        view.setElevation(dp(elevationDp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int accent = PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive());
            view.setOutlineAmbientShadowColor(Color.argb(isDarkModeActive() ? 62 : 42,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
            view.setOutlineSpotShadowColor(Color.argb(isDarkModeActive() ? 88 : 58,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
        }
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
        return PolarisVisualTheme.hex(
                PolarisVisualTheme.pressColor(visualTheme, isDarkModeActive()));
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
        group.setBackground(roundedBg(groupColorHex(), isMinimalVisualTheme() ? 18 : 22));
        if (!isMinimalVisualTheme()) {
            applyThemeElevation(group, 2);
        }
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
        labelView.setTag(TAG_SETTING_LABEL);
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
        view.setTag(TAG_SWITCH_THUMB);
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
        DatePicker picker = new DatePicker(themedControlContext());
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

    private void showImportedFirstWeekDayDialog() {
        String defaultDate = SemesterStartDateDefaults.resolve(Calendar.getInstance());
        firstWeekDay = defaultDate;
        currentWeek = currentWeekFromDate();
        saveConfig();
        updateHeader();
        renderSchedule();

        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("设置第一周的第一天");

        TextView message = new TextView(this);
        message.setText("请选择本学期第一周的第一天。若跳过，将使用默认日期 " + defaultDate + "。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, dp(8));
        panel.addView(message, messageParams);

        Calendar date = calendarFromText(defaultDate);
        DatePicker picker = new DatePicker(themedControlContext());
        picker.init(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), null);
        panel.addView(picker);

        panel.addView(dialogAction("使用此日期", v -> {
            firstWeekDay = picker.getYear() + "/" + (picker.getMonth() + 1) + "/" + picker.getDayOfMonth();
            currentWeek = currentWeekFromDate();
            saveConfig();
            updateHeader();
            renderSchedule();
            dialog.dismiss();
        }));
        panel.addView(dialogAction("跳过，使用默认日期", v -> dialog.dismiss()));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> scheduleWeekSwipeHintIfNeeded());
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void showTimeDialog(String titleText, String current, StringSetter setter) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(titleText);
        int[] time = timeFromText(current);
        TimePicker picker = new TimePicker(themedControlContext());
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

    private Context themedControlContext() {
        int theme = isDarkModeActive()
                ? android.R.style.Theme_Material
                : android.R.style.Theme_Material_Light;
        return new ContextThemeWrapper(this, theme);
    }

    private View glassDialogContent(LinearLayout panel, int radius) {
        return glassDialogContent(panel, radius, -1);
    }

    private View glassDialogContent(LinearLayout panel, int radius, int opacityPercent) {
        if (!shellBarsBlurEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return panel;
        }
        panel.setBackgroundColor(Color.TRANSPARENT);
        BackdropBlurView glass = new BackdropBlurView(this);
        glass.setSourceView(dialogBlurSource());
        glass.setGlassBackground(opacityPercent < 0
                ? dialogGlassBg(radius)
                : dialogGlassBg(radius, opacityPercent), dp(radius));
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

    private GradientDrawable dialogGlassBg(int radius, int opacityPercent) {
        GradientDrawable drawable = new GradientDrawable();
        int boundedOpacity = Math.max(0, Math.min(100, opacityPercent));
        int alpha = Math.round(255f * boundedOpacity / 100f);
        if (isDarkModeActive()) {
            drawable.setColor(Color.argb(alpha, 24, 34, 53));
            drawable.setStroke(dp(1), Color.argb(85, 255, 255, 255));
        } else {
            drawable.setColor(Color.argb(alpha, 248, 251, 255));
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
        panel.addView(sectionHeader("课程提醒"));
        LinearLayout reminderCard = settingsGroup();
        reminderCard.addView(settingSwitchRow("课程提醒", remindersEnabled,
                this::handleCourseReminderToggle));
        reminderCard.addView(settingValueRow("提前时间", reminderMinutesBefore + " 分钟",
                v -> showChoiceDialog(v, "提前时间",
                        new String[]{"5 分钟", "10 分钟", "15 分钟", "30 分钟"},
                        reminderMinutesBefore + " 分钟", value -> {
                            reminderMinutesBefore = Integer.parseInt(value.split(" ")[0]);
                            saveConfig();
                            refreshActiveSettingsPage();
                        })));
        reminderCard.addView(settingValueRow("提醒状态", courseReminderStatusText(),
                v -> handleCourseReminderStatusClick()));
        panel.addView(reminderCard);
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
        dataCard.addView(settingValueRow("解析诊断", lastParseDiagnosticsSummary,
                v -> showParseDiagnosticsDialog()));
        panel.addView(dataCard);
        showSettingsPage("课表设置", panel);
    }

    private void showParseDiagnosticsDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("最近一次解析诊断");
        if (lastParseDiagnosticsText.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有解析记录。导入一次 PDF 课表后，可在这里查看识别提示和详细日志。");
            empty.setTextColor(mutedColor());
            empty.setTextSize(15);
            empty.setLineSpacing(dp(4), 1f);
            empty.setPadding(0, dp(4), 0, dp(8));
            panel.addView(empty);
        } else {
            TextView report = new TextView(this);
            report.setText(lastParseDiagnosticsText);
            report.setTextColor(inkColor());
            report.setTextSize(13);
            report.setTypeface(Typeface.MONOSPACE);
            report.setTextIsSelectable(true);
            report.setLineSpacing(dp(3), 1f);
            report.setPadding(dp(12), dp(10), dp(12), dp(10));
            report.setBackground(roundedBg(cardColorHex(), 12));

            ScrollView reportScroll = new ScrollView(this);
            reportScroll.setFillViewport(false);
            reportScroll.setVerticalScrollBarEnabled(true);
            reportScroll.addView(report, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT));
            int lineCount = Math.max(1, lastParseDiagnosticsText.split("\\n", -1).length);
            int visibleLines = Math.min(16, lineCount);
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.min(dp(360), Math.max(dp(150), visibleLines * dp(22))));
            panel.addView(reportScroll, scrollParams);
            panel.addView(dialogAction("复制诊断日志", v -> copyParseDiagnostics()));
        }
        panel.addView(dialogAction("关闭", v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void copyParseDiagnostics() {
        if (lastParseDiagnosticsText.length() == 0) {
            Toast.makeText(this, "暂无可复制的解析诊断", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "无法访问系统剪贴板", Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "Polaris解析诊断", lastParseDiagnosticsText));
        Toast.makeText(this, "解析诊断已复制", Toast.LENGTH_SHORT).show();
    }

    private void showCourseManagePage() {
        LinearLayout panel = settingsPagePanel("查看所有课程");
        courseManageListContainer = new LinearLayout(this);
        courseManageListContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(courseManageListContainer);
        refreshCourseManageList();
        showSettingsPage("查看所有课程", panel, activeSettingsTitle);
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
        List<CourseGroup> groups = courseGroupsForManage();
        Collections.sort(groups, (first, second) -> Integer.compare(courseGroupTimeValue(first), courseGroupTimeValue(second)));
        for (CourseGroup group : groups) {
            list.addView(courseManageRow(group));
        }
        courseManageListContainer.addView(list);
    }

    private List<CourseGroup> courseGroupsForManage() {
        Map<String, CourseGroup> groups = new LinkedHashMap<>();
        for (Course course : courses) {
            String name = course.name == null || course.name.trim().length() == 0
                    ? "未命名课程" : course.name.trim();
            String groupKey = course.structuredCourseId == null
                    || course.structuredCourseId.trim().isEmpty()
                    ? "legacy|" + name + "|" + normalizeTeacherIdentity(course.teacher)
                    : "id|" + course.structuredCourseId;
            CourseGroup group = groups.get(groupKey);
            if (group == null) {
                group = new CourseGroup(name);
                groups.put(groupKey, group);
            }
            group.courses.add(course);
        }
        return new ArrayList<>(groups.values());
    }

    private String normalizeTeacherIdentity(String teacher) {
        return teacher == null ? "" : teacher.replaceAll("\\s+", "").trim();
    }

    private int courseTimeValue(Course course) {
        int day = course.day < 0 ? 99 : course.day;
        CourseTimeResolver.TimeRange range = CourseTimeResolver.timeRange(
                course, courseTimeSettings());
        return day * 24 * 60 + (range == null ? 0 : range.startMinutes);
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
        if (course.isBannerOnlyCourse()) {
            return "顶部横幅";
        }
        return dayName(course.day) + (course.hasExactTime()
                ? CourseTimeResolver.format(course, courseTimeSettings())
                : course.startSection + "-" + course.endSection + "节");
    }

    private String courseTimeText(Course course) {
        if (course.isBannerOnlyCourse()) {
            return "顶部横幅\n无固定节次";
        }
        return dayName(course.day) + "\n" + (course.hasExactTime()
                ? CourseTimeResolver.format(course, courseTimeSettings())
                : course.startSection + "-" + course.endSection + "节");
    }

    private String groupTimeText(CourseGroup group) {
        StringBuilder builder = new StringBuilder();
        List<Course> sorted = new ArrayList<>(group.courses);
        Collections.sort(sorted, (first, second) -> Integer.compare(courseTimeValue(first), courseTimeValue(second)));
        for (Course course : sorted) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            if (course.isBannerOnlyCourse()) {
                builder.append("顶部横幅 · 无固定节次");
            } else {
                builder.append(dayName(course.day)).append(' ')
                        .append(course.hasExactTime()
                                ? CourseTimeResolver.format(course, courseTimeSettings())
                                : course.startSection + "-" + course.endSection + "节");
            }
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
        scrollView.setId(View.generateViewId());
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
        save.setBackground(roundedBg(primaryActionFillHex(), 14));
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
        settingsPage.animate().cancel();
        settingsPage.clearAnimation();
        settingsPage.removeAllViews();
        settingsPage.setBackgroundColor(pageSurfaceColor());
        settingsPage.setAlpha(1f);
        if (suppressSettingsPageAnimation || settingsAlreadyVisible) {
            settingsPage.setTranslationX(0f);
        }

        LinearLayout fixedHeader = new LinearLayout(this);
        fixedHeader.setTag(TAG_SETTINGS_HEADER);
        fixedHeader.setOrientation(LinearLayout.HORIZONTAL);
        fixedHeader.setGravity(Gravity.CENTER_VERTICAL);
        fixedHeader.setPadding(0, statusBarHeight() + dp(8), 0, dp(8));
        fixedHeader.setBackgroundColor(settingsHeaderSurfaceColor());

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
        contentScroll.setBackgroundColor(pageSurfaceColor());
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
            myPage.animate().cancel();
            myPage.setClipBounds(null);
            myPage.setVisibility(View.GONE);
            myPage.setTranslationX(0f);
            myPage.setAlpha(1f);
        }
        if (bottomNavView != null) {
            bottomNavView.animate().cancel();
            bottomNavView.setClipBounds(null);
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
        settingsPage.setBackgroundColor(pageSurfaceColor());
        refreshSettingsThemeRecursive(settingsPage);
    }

    private void refreshSettingsThemeRecursive(View view) {
        Object tag = view.getTag();
        if (TAG_SETTINGS_GROUP.equals(tag)) {
            view.setBackground(roundedBg(groupColorHex(), isMinimalVisualTheme() ? 18 : 22));
        } else if (TAG_SECTION_HEADER.equals(tag) && view instanceof TextView) {
            ((TextView) view).setTextColor(mutedColor());
        } else if (TAG_SETTING_LABEL.equals(tag) && view instanceof TextView) {
            ((TextView) view).setTextColor(inkColor());
        } else if (TAG_SETTING_VALUE.equals(tag) && view instanceof TextView) {
            ((TextView) view).setTextColor(mutedColor());
        } else if (TAG_SWITCH_THUMB.equals(tag) && view instanceof SwitchThumbView) {
            ((SwitchThumbView) view).setDark(isDarkModeActive());
        } else if (TAG_SETTINGS_HEADER.equals(tag)) {
            view.setBackgroundColor(settingsHeaderSurfaceColor());
        } else if (TAG_SETTINGS_SCROLL.equals(tag)) {
            view.setBackgroundColor(pageSurfaceColor());
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
            myPage.animate().cancel();
            myPage.setVisibility(View.VISIBLE);
            myPage.setAlpha(1f);
        }
        int width = settingsPage.getWidth() > 0
                ? settingsPage.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        int revealOffset = dp(SETTINGS_PAGE_REVEAL_OFFSET_DP);
        int pageHeight = myPage == null ? 0 : Math.max(1, myPage.getHeight());
        if (myPage != null) {
            myPage.setTranslationX(-revealOffset);
            myPage.setClipBounds(new Rect(0, 0, revealOffset, pageHeight));
        }
        if (bottomNavView != null) {
            bottomNavView.animate().cancel();
            bottomNavView.setTranslationY(0f);
            bottomNavView.setAlpha(1f);
            bottomNavView.setClipBounds(new Rect(0, 0, 0,
                    Math.max(1, bottomNavView.getHeight())));
            bottomNavView.setVisibility(View.VISIBLE);
        }
        settingsPage.animate().cancel();
        settingsPage.animate()
                .translationX(width)
                .setDuration(SETTINGS_PAGE_EXIT_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .setUpdateListener(animation -> {
                    int revealedWidth = Math.max(0,
                            Math.min(width, Math.round(settingsPage.getTranslationX())));
                    float progress = width == 0 ? 1f : revealedWidth / (float) width;
                    if (myPage != null) {
                        float translation = -revealOffset * (1f - progress);
                        myPage.setTranslationX(translation);
                        int localRevealRight = Math.round(revealedWidth - translation);
                        myPage.setClipBounds(new Rect(0, 0,
                                Math.min(myPage.getWidth(), localRevealRight),
                                Math.max(1, myPage.getHeight())));
                    }
                    if (bottomNavView != null) {
                        int localRevealRight = Math.max(0, Math.min(bottomNavView.getWidth(),
                                revealedWidth - bottomNavView.getLeft()));
                        bottomNavView.setClipBounds(new Rect(0, 0, localRevealRight,
                                Math.max(1, bottomNavView.getHeight())));
                    }
                })
                .withEndAction(() -> {
                    settingsPage.animate().setUpdateListener(null);
                    settingsPage.setAlpha(1f);
                    settingsPage.setTranslationX(0f);
                    switchTab(1);
                    if (myPage != null) {
                        myPage.setVisibility(View.VISIBLE);
                        myPage.setTranslationX(0f);
                        myPage.setAlpha(1f);
                        myPage.setClipBounds(null);
                    }
                    if (bottomNavView != null) {
                        bottomNavView.setClipBounds(null);
                        bottomNavView.setTranslationY(0f);
                        bottomNavView.setAlpha(1f);
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
        save.setBackground(roundedBg(primaryActionFillHex(), 14));
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
        displayCard.addView(settingValueRow("视觉主题", visualTheme,
                v -> showChoiceDialog(v, "视觉主题", PolarisVisualTheme.NAMES,
                        visualTheme, this::applyVisualTheme)));
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
                                backgroundImageCrop = BackgroundImageCrop.full();
                                timetableBackground = "清爽蓝";
                                saveGlobalAppearance();
                                applyShellAppearance();
                                renderSchedule();
                                updateSettingValueRow(v, "未设置");
                                refreshVisibleSettingsTheme();
                            }
                        }));
        displayCard.addView(backgroundSettingRow);
        displayCard.addView(settingSwitchRow("显示本周实践横幅", showPracticeBanner, value -> {
            showPracticeBanner = value;
            saveGlobalAppearance();
            renderSchedule();
        }));
        displayCard.addView(settingSwitchRow("折叠午休时间", collapseLunchBreak, value -> {
            collapseLunchBreak = value;
            saveGlobalAppearance();
            renderSchedule();
        }));
        panel.addView(displayCard);

        panel.addView(sectionHeader("外观"));
        LinearLayout presetCard = settingsGroup();
        appearancePresetSettingRow = settingValueRow("外观预设", appearancePresetName(),
                v -> showChoiceDialog(v, "外观预设", APPEARANCE_PRESETS,
                        appearancePresetName(), this::applyAppearancePreset));
        presetCard.addView(appearancePresetSettingRow);

        LinearLayout advancedContainer = new LinearLayout(this);
        advancedContainer.setOrientation(LinearLayout.VERTICAL);
        advancedContainer.setVisibility(View.GONE);
        final boolean[] advancedExpanded = {false};
        final View[] advancedToggle = new View[1];
        advancedToggle[0] = settingValueRow("高级设置", "已收起", v -> {
            advancedExpanded[0] = !advancedExpanded[0];
            advancedContainer.setVisibility(advancedExpanded[0] ? View.VISIBLE : View.GONE);
            updateSettingValueRow(advancedToggle[0], advancedExpanded[0] ? "已展开" : "已收起");
        });
        presetCard.addView(advancedToggle[0]);
        panel.addView(presetCard);

        advancedContainer.addView(sectionHeader("界面细节"));
        advancedContainer.addView(buildAdvancedShellSettings());
        advancedContainer.addView(sectionHeader("课表框架"));
        advancedContainer.addView(buildAdvancedScheduleFrameSettings());
        panel.addView(advancedContainer);
        showSettingsPage("全局设置", panel);
    }

    private LinearLayout buildAdvancedShellSettings() {
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
                    updateSettingValueRow(v, timetableHeaderOpacity + "%");
                })));
        shellCard.addView(settingValueRow("课表底部透明度", bottomNavOpacity + "%",
                v -> showNumberDialog("课表底部透明度", 40, 100, bottomNavOpacity, value -> {
                    bottomNavOpacity = Math.max(40, Math.min(100, value));
                    saveGlobalAppearance();
                    applyShellAppearance();
                    updateSettingValueRow(v, bottomNavOpacity + "%");
                })));
        shellCard.addView(settingValueRow("底部切换栏高度", bottomNavHeight + " dp",
                v -> showNumberDialog("底部切换栏高度", 56, 120, bottomNavHeight, 1, value -> {
                    bottomNavHeight = Math.max(56, Math.min(120, value));
                    saveGlobalAppearance();
                    applyShellAppearance();
                    refreshMyPageBehindSettings();
                    renderSchedule();
                    updateSettingValueRow(v, bottomNavHeight + " dp");
                })));
        shellCard.addView(settingValueRow("底部切换栏形状", bottomNavShape,
                v -> showChoiceDialog(v, "底部切换栏形状",
                        new String[]{"矩形", "分散", "侧边"}, bottomNavShape, value -> {
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
        return shellCard;
    }

    private LinearLayout buildAdvancedScheduleFrameSettings() {
        LinearLayout frameCard = settingsGroup();
        frameCard.addView(settingValueRow("课程格子高度", courseCellHeight + " dp",
                v -> showNumberDialog("课程格子高度", 56, 120, courseCellHeight, value -> {
                    courseCellHeight = Math.max(56, Math.min(120, value));
                    saveGlobalAppearance();
                    renderSchedule();
                    updateSettingValueRow(v, courseCellHeight + " dp");
                })));
        frameCard.addView(settingValueRow("格子圆角半径", courseCornerRadius + " dp",
                v -> showNumberDialog("格子圆角半径", 0, 24, courseCornerRadius, value -> {
                    courseCornerRadius = Math.max(0, Math.min(24, value));
                    saveGlobalAppearance();
                    renderSchedule();
                    updateSettingValueRow(v, courseCornerRadius + " dp");
                })));
        frameCard.addView(settingValueRow("格子不透明度", courseBlockOpacity + "%",
                v -> showNumberDialog("格子不透明度", 45, 100, courseBlockOpacity, value -> {
                    courseBlockOpacity = Math.max(45, Math.min(100, value));
                    saveGlobalAppearance();
                    renderSchedule();
                    updateSettingValueRow(v, courseBlockOpacity + "%");
                })));
        return frameCard;
    }

    private String appearancePresetName() {
        if (matchesAppearancePreset(78, 86, 60, 58, 10, 76, 9, 70)) {
            return "标准";
        }
        if (matchesAppearancePreset(90, 94, 56, 22, 8, 64, 7, 88)) {
            return "紧凑";
        }
        if (matchesAppearancePreset(55, 64, 64, 32, 14, 82, 14, 62)) {
            return "沉浸";
        }
        return "自定义";
    }

    private boolean matchesAppearancePreset(
            int headerOpacity, int navOpacity, int navHeight, int navRadius,
            int navMargin, int cellHeight, int cellRadius, int cellOpacity) {
        return timetableHeaderOpacity == headerOpacity
                && bottomNavOpacity == navOpacity
                && bottomNavHeight == navHeight
                && bottomNavRadius() == navRadius
                && bottomNavSideMargin == navMargin
                && courseCellHeight == cellHeight
                && courseCornerRadius == cellRadius
                && courseBlockOpacity == cellOpacity;
    }

    private void applyAppearancePreset(String preset) {
        if ("紧凑".equals(preset)) {
            setAppearanceValues(90, 94, 56, 22, 8, 64, 7, 88);
        } else if ("沉浸".equals(preset)) {
            setAppearanceValues(55, 64, 64, 32, 14, 82, 14, 62);
        } else {
            setAppearanceValues(78, 86, 60, 58, 10, 76, 9, 70);
        }
        saveGlobalAppearance();
        applyShellAppearance();
        refreshMyPageBehindSettings();
        renderSchedule();
        refreshActiveSettingsPage();
        Toast.makeText(this, "已应用“" + appearancePresetName() + "”外观", Toast.LENGTH_SHORT).show();
    }

    private void applyVisualTheme(String value) {
        String nextTheme = PolarisVisualTheme.normalize(value);
        if (nextTheme.equals(visualTheme)) {
            return;
        }
        visualTheme = nextTheme;
        if (!PolarisVisualTheme.MINIMAL.equals(visualTheme)) {
            darkMode = PolarisVisualTheme.defaultDark(visualTheme) ? "深色" : "浅色";
            scheduleRepository.saveGlobalDarkMode(darkMode);
        }
        saveGlobalAppearance();
        applyShellAppearance();
        refreshMyPageBehindSettings();
        renderSchedule();
        refreshActiveSettingsPage();
        Toast.makeText(this, "已切换为“" + visualTheme + "”", Toast.LENGTH_SHORT).show();
    }

    private void setAppearanceValues(
            int headerOpacity, int navOpacity, int navHeight, int navRadius,
            int navMargin, int cellHeight, int cellRadius, int cellOpacity) {
        timetableHeaderOpacity = headerOpacity;
        bottomNavOpacity = navOpacity;
        bottomNavHeight = navHeight;
        bottomNavRectCornerRadius = navRadius;
        bottomNavSplitCornerRadius = navRadius;
        bottomNavSideCornerRadius = navRadius;
        bottomNavSideMargin = navMargin;
        courseCellHeight = cellHeight;
        courseCornerRadius = cellRadius;
        courseBlockOpacity = cellOpacity;
    }

    private void applyShellAppearance() {
        if (getWindow() != null) {
            getWindow().setStatusBarColor(backgroundColor());
            getWindow().setNavigationBarColor(backgroundColor());
            updateSystemBarAppearance(getWindow());
        }
        if (rootView != null) {
            rootView.setBackgroundColor(backgroundColor());
        }
        if (themeBackgroundView != null) {
            themeBackgroundView.setVisualTheme(visualTheme, isDarkModeActive());
        }
        if (scheduleBoard != null) {
            scheduleBoard.setVisualTheme(visualTheme);
            scheduleBoard.setDarkMode(isDarkModeActive());
        }
        if (topPanelGlassLayer != null) {
            updateGlassLayer(topPanelGlassLayer, liquidGlassBg(timetableHeaderOpacity), 24);
        }
        if (topPanelContainer != null) {
            topPanelContainer.setElevation(0f);
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
        if (overflowMenuButton != null) {
            overflowMenuButton.setTextColor(inkColor());
        }
        if (returnCurrentWeekButton != null) {
            returnCurrentWeekButton.setTextColor(inkColor());
            returnCurrentWeekButton.setBackground(roundedBg(cardColorHex(), 14));
        }
        updateTodayOverview();
        updateConflictSummary();
        if (scheduleNav != null) {
            boolean active = activeTab == 0;
            scheduleNav.setTextColor(active ? inkColor() : mutedColor());
        }
        if (myNav != null) {
            boolean active = activeTab == 1;
            myNav.setTextColor(active ? inkColor() : mutedColor());
        }
        if (settingsPage != null) {
            settingsPage.setBackgroundColor(pageSurfaceColor());
        }
        refreshEmptyScheduleAppearance();
    }

    private void updateSystemBarAppearance(Window window) {
        if (window == null) {
            return;
        }
        boolean lightIcons = isDarkModeActive();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightIcons ? 0 : mask, mask);
            }
            return;
        }
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags = lightIcons
                ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                : flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = lightIcons
                    ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    : flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private void refreshEmptyScheduleAppearance() {
        if (contentHost == null || emptyScheduleView == null) {
            return;
        }
        int index = contentHost.indexOfChild(emptyScheduleView);
        if (index < 0) {
            return;
        }
        int visibility = emptyScheduleView.getVisibility();
        contentHost.removeView(emptyScheduleView);
        emptyScheduleView = buildEmptyScheduleView();
        emptyScheduleView.setVisibility(visibility);
        contentHost.addView(emptyScheduleView, index, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
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
        loadActiveCourses();
        rescheduleCourseReminders();
        updateHeader();
        renderSchedule();
        updateEmptyScheduleView();
        refreshMyPage();
        Toast.makeText(this, "已删除课表", Toast.LENGTH_SHORT).show();
    }

    private void switchSchedule(String scheduleId) {
        saveConfig();
        scheduleRepository.saveStructuredCourses(activeScheduleId, structuredCourses);
        activeScheduleId = scheduleId;
        scheduleRepository.setActiveScheduleId(scheduleId);
        applyConfig(scheduleRepository.loadConfig(activeScheduleId));
        currentWeek = currentWeekFromDate();
        loadActiveCourses();
        rescheduleCourseReminders();
        updateHeader();
        renderSchedule();
        updateEmptyScheduleView();
        refreshMyPage();
    }

    private void loadActiveCourses() {
        replaceCanonicalCourses(scheduleRepository.loadStructuredCourses(activeScheduleId));
    }

    private void replaceCanonicalCourses(List<StructuredCourse> source) {
        structuredCourses.clear();
        if (source != null) {
            structuredCourses.addAll(source);
        }
        refreshCourseView();
    }

    private void refreshCourseView() {
        courses.clear();
        courses.addAll(courseStructureMapper.toLegacyCourses(structuredCourses));
    }

    private void showSecuritySettings() {
        LinearLayout panel = settingsPagePanel("安全设置");
        panel.addView(sectionHeader("课表信息"));
        LinearLayout scheduleInfoCard = settingsGroup();
        scheduleInfoCard.addView(settingValueRow("学期", displaySemesterName(),
                v -> showSemesterNameDialog()));
        scheduleInfoCard.addView(settingValueRow("学校", displaySchoolName(),
                v -> showParserModelDialog()));
        panel.addView(scheduleInfoCard);

        panel.addView(sectionHeader("账号"));
        LinearLayout accountCard = settingsGroup();
        accountCard.addView(settingValueRow("登录方式", accountName + " · 无需账号登录", v -> {}));
        accountCard.addView(settingValueRow("本机数据", "课程保存在本机", v -> {}));
        panel.addView(accountCard);
        showSettingsPage("安全设置", panel);
    }

    private void showSemesterNameDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("修改学期");
        EditText semesterInput = input("例如 2025-2026学年第2学期", semesterName);
        semesterInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(48)});
        panel.addView(semesterInput);
        panel.addView(pageSaveButton(() -> {
            String value = semesterInput.getText().toString().trim();
            if (value.length() == 0) {
                Toast.makeText(this, "学期不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            semesterName = value;
            saveConfig();
            refreshMyPage();
            dialog.dismiss();
            refreshActiveSettingsPage();
        }));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private void changeWeek(int delta) {
        int nextWeek = Math.max(1, Math.min(semesterWeeks, currentWeek + delta));
        if (nextWeek == currentWeek) {
            return;
        }
        if (scheduleBoard != null) {
            scheduleBoard.captureCurrentBoardForTransition();
        }
        currentWeek = nextWeek;
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

    private void applyAccountProfile(ScheduleRepository.AccountProfile profile) {
        ScheduleRepository.AccountProfile safeProfile = profile == null
                ? new ScheduleRepository.AccountProfile() : profile;
        String savedName = safeProfile.name == null ? "" : safeProfile.name.trim();
        accountName = savedName.length() == 0 ? "管理员" : savedName;
        avatarImageUri = safeProfile.avatarUri == null ? "" : safeProfile.avatarUri;
        avatarImageCrop = BackgroundImageCrop.of(
                safeProfile.cropLeft,
                safeProfile.cropTop,
                safeProfile.cropRight,
                safeProfile.cropBottom);
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
        timetableBackground = config.timetableBackground;
        visualTheme = PolarisVisualTheme.normalize(config.visualTheme);
        backgroundImageUri = config.backgroundImageUri;
        backgroundImageCrop = BackgroundImageCrop.of(
                config.backgroundCropLeft,
                config.backgroundCropTop,
                config.backgroundCropRight,
                config.backgroundCropBottom);
        courseSectionCount = Math.max(1, Math.min(20, config.sectionCount));
        semesterWeeks = Math.max(1, Math.min(20, config.semesterWeeks));
        remindersEnabled = config.remindersEnabled;
        reminderMinutesBefore = normalizedReminderMinutes(config.reminderMinutesBefore);
        showSaturday = config.showSaturday;
        showSunday = config.showSunday;
        showOutOfWeekCourses = config.showOutOfWeekCourses;
        showPracticeBanner = config.showPracticeBanner;
        collapseLunchBreak = config.collapseLunchBreak;
        courseCellHeight = Math.max(56, Math.min(120, config.courseCellHeight));
        courseCornerRadius = Math.max(0, Math.min(24, config.courseCornerRadius));
        courseBlockOpacity = Math.max(45, Math.min(100, config.courseBlockOpacity));
        selectedParserModel = parserModelFromConfig(config.parserModel);
        semesterName = config.semesterName.length() > 0
                ? config.semesterName
                : SemesterStartDateDefaults.resolveSemesterName(calendarFromText(firstWeekDay));
        schoolName = selectedParserModel == null ? config.schoolName : selectedParserModel.label;
        applySchoolTimeDefaults(selectedParserModel);
        collapseXautMiddleSections = config.collapseXautMiddleSections;
        timetableHeaderOpacity = Math.max(40, Math.min(100, config.timetableHeaderOpacity));
        bottomNavOpacity = Math.max(40, Math.min(100, config.bottomNavOpacity));
        bottomNavShape = normalizeBottomNavShape(config.bottomNavShape);
        // 72 dp was the old fixed visual height. Migrate that legacy default once to the
        // compact 60 dp bar; all other user-selected heights remain unchanged.
        bottomNavHeight = config.bottomNavHeight == 72
                ? 60 : Math.max(56, Math.min(120, config.bottomNavHeight));
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
        config.semesterName = semesterName;
        config.schoolName = schoolName;
        config.timetableBackground = timetableBackground;
        config.visualTheme = visualTheme;
        config.backgroundImageUri = backgroundImageUri;
        config.backgroundCropLeft = backgroundImageCrop.left;
        config.backgroundCropTop = backgroundImageCrop.top;
        config.backgroundCropRight = backgroundImageCrop.right;
        config.backgroundCropBottom = backgroundImageCrop.bottom;
        config.darkMode = darkMode;
        config.sectionCount = courseSectionCount;
        config.semesterWeeks = semesterWeeks;
        config.remindersEnabled = remindersEnabled;
        config.reminderMinutesBefore = reminderMinutesBefore;
        config.showSaturday = showSaturday;
        config.showSunday = showSunday;
        config.showOutOfWeekCourses = showOutOfWeekCourses;
        config.showPracticeBanner = showPracticeBanner;
        config.collapseLunchBreak = collapseLunchBreak;
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
        rescheduleCourseReminders();
    }

    private void handleCourseReminderToggle(boolean enabled) {
        if (!enabled) {
            remindersEnabled = false;
            saveConfig();
            refreshActiveSettingsPage();
            Toast.makeText(this, "课程提醒已关闭", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
            return;
        }
        if (!CourseReminderScheduler.hasNotificationPermission(this)) {
            openNotificationSettings();
            refreshActiveSettingsPage();
            Toast.makeText(this, "请先在系统设置中允许 Polaris 发送通知", Toast.LENGTH_LONG).show();
            return;
        }
        enableCourseReminders();
    }

    private void enableCourseReminders() {
        remindersEnabled = true;
        saveConfig();
        refreshActiveSettingsPage();
        if (CourseReminderScheduler.canScheduleExactAlarms(this)) {
            Toast.makeText(this, "课程提醒已开启", Toast.LENGTH_SHORT).show();
        } else {
            showExactReminderAccessDialog();
        }
    }

    private void showExactReminderAccessDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel("提高提醒准时性");
        TextView message = new TextView(this);
        message.setText("允许“闹钟和提醒”权限后，课程提醒可按设定时间准时触发。暂不允许也能使用普通提醒，但系统省电时可能延迟。");
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(0f, 1.2f);
        message.setPadding(0, 0, 0, dp(8));
        panel.addView(message);
        panel.addView(dialogAction("允许精确提醒", v -> {
            dialog.dismiss();
            openExactAlarmSettings();
        }));
        panel.addView(dialogAction("使用普通提醒", v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, 22));
        dialog.show();
        transparentDialog(dialog);
    }

    private String courseReminderStatusText() {
        if (!remindersEnabled) {
            return "未开启";
        }
        if (!CourseReminderScheduler.hasNotificationPermission(this)) {
            return "需要通知权限";
        }
        return CourseReminderScheduler.canScheduleExactAlarms(this)
                ? "精确提醒" : "普通提醒（可能延迟）";
    }

    private void handleCourseReminderStatusClick() {
        if (!remindersEnabled) {
            Toast.makeText(this, "请先开启课程提醒", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!CourseReminderScheduler.hasNotificationPermission(this)) {
            openNotificationSettings();
            return;
        }
        if (!CourseReminderScheduler.canScheduleExactAlarms(this)) {
            openExactAlarmSettings();
            return;
        }
        Toast.makeText(this, "当前已使用精确提醒", Toast.LENGTH_SHORT).show();
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException exception) {
            openAppDetailsSettings();
        }
    }

    private void openNotificationSettings() {
        try {
            CourseReminderScheduler.createNotificationChannel(this);
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                        .putExtra(Settings.EXTRA_CHANNEL_ID,
                                CourseReminderScheduler.CHANNEL_ID);
            } else {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            }
            startActivity(intent);
        } catch (RuntimeException exception) {
            openAppDetailsSettings();
        }
    }

    private void openAppDetailsSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private int normalizedReminderMinutes(int value) {
        int[] choices = {5, 10, 15, 30};
        int closest = choices[0];
        for (int choice : choices) {
            if (Math.abs(choice - value) < Math.abs(closest - value)) {
                closest = choice;
            }
        }
        return closest;
    }

    private void rescheduleCourseReminders() {
        if (scheduleRepository != null) {
            CourseReminderScheduler.reschedule(this);
        }
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
        if (appearancePresetSettingRow != null) {
            updateSettingValueRow(appearancePresetSettingRow, appearancePresetName());
        }
    }

    private void copyGlobalAppearanceToSchedule(String scheduleId) {
        ScheduleRepository.Config config = scheduleRepository.loadConfig(scheduleId);
        config.timetableBackground = timetableBackground;
        config.visualTheme = visualTheme;
        config.backgroundImageUri = backgroundImageUri;
        config.backgroundCropLeft = backgroundImageCrop.left;
        config.backgroundCropTop = backgroundImageCrop.top;
        config.backgroundCropRight = backgroundImageCrop.right;
        config.backgroundCropBottom = backgroundImageCrop.bottom;
        config.courseCellHeight = courseCellHeight;
        config.courseCornerRadius = courseCornerRadius;
        config.courseBlockOpacity = courseBlockOpacity;
        config.showPracticeBanner = showPracticeBanner;
        config.collapseLunchBreak = collapseLunchBreak;
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
        return CourseTimeResolver.inferSemesterWeeks(source, 20, 20);
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
        if (!isMinimalVisualTheme()) {
            return PolarisVisualTheme.pageColor(visualTheme, isDarkModeActive());
        }
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
        return PolarisVisualTheme.inkColor(visualTheme, isDarkModeActive());
    }

    private int mutedColor() {
        return PolarisVisualTheme.mutedColor(visualTheme, isDarkModeActive());
    }

    private String cardColorHex() {
        return PolarisVisualTheme.hex(
                PolarisVisualTheme.cardColor(visualTheme, isDarkModeActive()));
    }

    private String groupColorHex() {
        return PolarisVisualTheme.hex(
                PolarisVisualTheme.groupColor(visualTheme, isDarkModeActive()));
    }

    private String selectedFillHex() {
        if (!isMinimalVisualTheme()) {
            return PolarisVisualTheme.hex(
                    PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive()));
        }
        return isDarkModeActive() ? "#FFFFFF" : "#172033";
    }

    private String primaryActionFillHex() {
        return PolarisVisualTheme.hex(
                PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive()));
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
        if (!isMinimalVisualTheme()) {
            return Color.WHITE;
        }
        return isDarkModeActive() ? color("#172033") : Color.WHITE;
    }

    private boolean isMinimalVisualTheme() {
        return PolarisVisualTheme.MINIMAL.equals(
                PolarisVisualTheme.normalize(visualTheme));
    }

    private int pageSurfaceColor() {
        return isMinimalVisualTheme() ? backgroundColor() : Color.TRANSPARENT;
    }

    private int settingsHeaderSurfaceColor() {
        if (isMinimalVisualTheme()) {
            return backgroundColor();
        }
        int surface = PolarisVisualTheme.cardColor(visualTheme, isDarkModeActive());
        int alpha = isDarkModeActive() ? 220 : 226;
        return Color.argb(Math.min(alpha, Color.alpha(surface)),
                Color.red(surface), Color.green(surface), Color.blue(surface));
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

    private void updateTopPanelLayout(int contentHeight) {
        syncTopGlassHeight(contentHeight);
        if (scheduleBoard == null || contentHeight <= 0) {
            return;
        }
        int top = topPanelContainer == null ? statusBarHeight() : topPanelContainer.getTop();
        scheduleBoard.setOverlayInsets(top + contentHeight + dp(8), bottomContentInset());
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
        boolean dark = isDarkModeActive();
        int neutralSurface = dark ? Color.rgb(24, 34, 53) : Color.rgb(248, 251, 255);
        int neutralStroke = dark
                ? Color.argb(shellBarsBlurEnabled ? 90 : 130, 255, 255, 255)
                : Color.argb(shellBarsBlurEnabled ? 150 : 190, 103, 116, 138);
        if (isMinimalVisualTheme()) {
            drawable.setColor(Color.argb(alpha,
                    Color.red(neutralSurface), Color.green(neutralSurface), Color.blue(neutralSurface)));
            drawable.setStroke(dp(1), neutralStroke);
        } else {
            int[] themeTints = PolarisVisualTheme.glassTintColors(
                    visualTheme, dark, neutralSurface);
            drawable.setColors(new int[]{
                    Color.argb(alpha, Color.red(themeTints[0]),
                            Color.green(themeTints[0]), Color.blue(themeTints[0])),
                    Color.argb(alpha, Color.red(themeTints[1]),
                            Color.green(themeTints[1]), Color.blue(themeTints[1]))
            });
            drawable.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            drawable.setStroke(dp(1),
                    PolarisVisualTheme.glassStrokeColor(visualTheme, dark, neutralStroke));
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
        if (isMinimalVisualTheme()) {
            drawable.setStroke(dp(1), isDarkModeActive()
                    ? Color.argb(42, 255, 255, 255)
                    : Color.argb(130, 255, 255, 255));
        } else {
            drawable.setStroke(dp(1),
                    PolarisVisualTheme.outlineColor(visualTheme, isDarkModeActive()));
        }
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
        int week = CourseTimeResolver.weekForDate(
                firstWeekStartMillis(), Calendar.getInstance());
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
        private boolean dark;
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

        void setDark(boolean dark) {
            if (this.dark == dark) {
                return;
            }
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
