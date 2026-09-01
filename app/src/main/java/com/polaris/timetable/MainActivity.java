package com.polaris.timetable;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Insets;
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
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
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
import android.view.WindowInsets;
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
import com.polaris.timetable.importer.ScheduleImportConfirmation;
import com.polaris.timetable.importer.ScheduleImportPreviewData;
import com.polaris.timetable.importer.ai.AiImportIssueFormatter;
import com.polaris.timetable.importer.ai.AiExternalImportReturnController;
import com.polaris.timetable.importer.ai.AiScheduleImportWorkflow;
import com.polaris.timetable.importer.ai.PolarisAiPromptV1;
import com.polaris.timetable.export.ExportFileProvider;
import com.polaris.timetable.export.ScheduleCalendarExporter;
import com.polaris.timetable.export.ScheduleImageExporter;
import com.polaris.timetable.export.SchedulePdfExporter;
import com.polaris.timetable.export.SemesterPdfExporter;
import com.polaris.timetable.model.ParseError;
import com.polaris.timetable.model.ParseResult;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseTimeMode;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StableMeetingId;
import com.polaris.timetable.model.StructuredCourse;
import com.polaris.timetable.model.StudyPlan;
import com.polaris.timetable.parser.SchoolParserModel;
import com.polaris.timetable.parser.ParseDiagnosticsReport;
import com.polaris.timetable.DialogKit.BooleanSetter;
import com.polaris.timetable.DialogKit.IntSetter;
import com.polaris.timetable.DialogKit.StringSetter;
import com.polaris.timetable.reminder.CourseReminderScheduler;
import com.polaris.timetable.reminder.PlanReminderScheduler;
import com.polaris.timetable.sharing.ScheduleShareCodec;
import com.polaris.timetable.sharing.ScheduleShareFile;
import com.polaris.timetable.statistics.ScheduleStatistics;
import com.polaris.timetable.storage.PlanRepository;
import com.polaris.timetable.storage.ScheduleBackupManager;
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
import com.polaris.timetable.ui.dialog.GlassDialogFactory;
import com.polaris.timetable.ui.DesignTokens;
import com.polaris.timetable.ui.PolarisThemeBackgroundView;
import com.polaris.timetable.ui.PolarisVisualTheme;
import com.polaris.timetable.ui.ScheduleBoardView;
import com.polaris.timetable.ui.TodayOverviewView;
import com.polaris.timetable.ui.page.MyPageBuilder;
import com.polaris.timetable.ui.page.SettingsPageBuilder;
import com.polaris.timetable.ui.shell.BottomNavView;
import com.polaris.timetable.ui.WeekdayLabels;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements BottomNavView.Host, MyPageBuilder.Host, SettingsPageBuilder.Host {
    private static final String TAG = "MainActivity";


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
    private static final int PICK_AI_RECOGNITION_IMAGE = 1004;
    private static final int PICK_SHARED_SCHEDULE_FILE = 1005;
    private static final int PICK_BACKUP_FILE = 1006;
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
    private static final String CONTACT_EMAIL = "polaris_io@163.com";
    private static final String PROJECT_HOME_URL = "https://github.com/Jockjrop/PolarisTimetable";
    private static final String[] APPEARANCE_PRESETS = {"标准", "紧凑", "沉浸"};
    private final List<StructuredCourse> structuredCourses = new ArrayList<>();
    private final List<Course> courses = new ArrayList<>();
    private final CourseStructureMapper courseStructureMapper = new CourseStructureMapper();
    private final AiScheduleImportWorkflow aiScheduleImportWorkflow =
            new AiScheduleImportWorkflow();
    private final AiExternalImportReturnController aiExternalImportReturnController =
            new AiExternalImportReturnController();
    private final ExecutorService scheduleExportExecutor = Executors.newSingleThreadExecutor();
    ScheduleRepository scheduleRepository;
    private PlanRepository planRepository;
    private final List<StudyPlan> studyPlans = new ArrayList<>();
    private LinearLayout planListContainer;
    private PdfImportCoordinator importCoordinator;
    private ScheduleBoardView scheduleBoard;
    private FrameLayout contentHost;
    FrameLayout rootView;
    private PolarisThemeBackgroundView themeBackgroundView;
    private FrameLayout topPanelContainer;
    private LinearLayout topPanel;
    private View topPanelGlassLayer;
    private BottomNavView bottomNavView;
    private View myPage;
    private ScrollView planPage;
    private FrameLayout settingsPage;
    private View emptyScheduleView;
    private TextView title;
    private TextView subtitle;
    private TextView returnCurrentWeekButton;
    private Button overflowMenuButton;
    private TodayOverviewView todayOverviewView;
    private CourseConflictSummaryView conflictSummaryView;
    /** 横屏平板顶栏的周选择下拉按钮（替代周 chip 行）。 */
    private TextView weekSelectorButton;
    /** 横屏平板：设置面板是否打开（我的页处于左侧侧栏模式）。 */
    private boolean tabletSettingsOpen;
    /** 横屏平板双栏模式的分隔线（仅侧栏模式可见）。 */
    private View paneDivider;
    /** 横屏平板：课表右侧本周实践列表面板（右侧空间充足时显示）。 */
    private FrameLayout practiceSidePanel;
    private LinearLayout practiceSidePanelContent;
    /** 横屏平板：右侧顶部的今日概览独立面板（右侧空间充足时显示）。 */
    private View todayOverviewPanel;
    /** 横屏平板：右侧下方剩余空间的本周计划面板。 */
    private FrameLayout planSidePanel;
    private LinearLayout planSidePanelContent;
    /** 平板横屏：左侧滑出的手机宽度计划管理浮层（遮罩 + 面板）。 */
    private FrameLayout planManageOverlay;
    private LinearLayout planManagePanel;
    /** 计划管理浮层头部（主题切换时刷新背景色）。 */
    private LinearLayout planManageHeader;
    /** 新建计划按钮（手机=计划页按钮，平板=管理浮层按钮；主题切换时刷新底色）。 */
    private TextView planAddButton;
    int currentWeek = 18;
    int visibleDayCount = 7;
    private final CourseScheduleDialogs scheduleDialogs = new CourseScheduleDialogs(this);
    private final AppearanceDialogs appearanceDialogs = new AppearanceDialogs(this);
    private final ReminderDialogs reminderDialogs = new ReminderDialogs(this);

    boolean isImportLink(Uri uri) {
        return ScheduleShareCodec.isImportLink(uri);
    }

    private int activeTab = 0;
    String semesterName = "";
    String schoolName = "";
    String scheduleName = "默认课表";
    private String classTimeConfig = "08:00 开始";
    private String firstClassStartTime = "08:00";
    private int classDurationMinutes = 50;
    private int classBreakMinutes = 10;
    private int classBigBreakMinutes = 30;
    private String afternoonStartTime = "14:30";
    private String lateAfternoonStartTime = "16:35";
    String firstWeekDay = "2026/3/3";
    private int courseSectionCount = 11;
    int semesterWeeks = 20;
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
    private int timetableHeaderOpacity = DesignTokens.GLASS_OPACITY_HEADER_DEFAULT;
    private int bottomNavOpacity = DesignTokens.GLASS_OPACITY_NAV_DEFAULT;
    private int bottomNavHeight = DesignTokens.NAV_HEIGHT_DEFAULT;
    private int bottomNavRectCornerRadius = 58;
    // 真实系统栏 inset（API 30+ 由 WindowInsets 驱动；-1 表示尚未捕获，回退反射值）。
    private int systemTopInset = -1;
    private int systemBottomInset = -1;
    private int systemLeftInset = 0;
    private int systemRightInset = 0;
    // 上次 buildLayout 实际采用的 inset，用于判断首次捕获后是否需要重建布局。
    private int builtTopInset = -1;
    private int builtBottomInset = 0;
    private int builtLeftInset = 0;
    private int builtRightInset = 0;
    private boolean insetsRebuildPending = false;
    boolean shellBarsBlurEnabled = true;
    private String timetableBackground = "清爽蓝";
    String visualTheme = PolarisVisualTheme.MINIMAL;
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
    String activeScheduleId = "default";
    private boolean suppressSettingsPageAnimation = false;
    SchoolParserModel selectedParserModel;
    private boolean collapseXautMiddleSections = false;
    private static final Pattern CLASS_TIME_LINE_PATTERN = Pattern.compile(
            "^(?:第?(\\d{1,2})\\s*节?\\s*[.、．:：]?\\s*)?"
                    + "(\\d{1,2})[:：](\\d{1,2})\\s*[-~～至]\\s*(\\d{1,2})[:：](\\d{1,2})\\s*$");
    private LinearLayout courseManageListContainer;
    private EditText courseManageSearchInput;
    private LinearLayout courseManageStatsCard;
    private TextView courseManageStatsText;
    private LinearLayout courseManageActionBar;
    private TextView courseManageSelectionCount;
    private boolean courseManageSelectionMode;
    final Set<String> courseManageSelectedIds = new HashSet<>();
    static final String[] BATCH_COLOR_VALUES = {
            "#4FA4F3", "#5AC8A6", "#F3A14F", "#E56A6A",
            "#9B7EDE", "#4FC3E0", "#7CB342", "#E57373",
            "#F06292", "#90A4AE"
    };
    private View backgroundSettingRow;
    private View appearancePresetSettingRow;
    private boolean bottomNavHidden;
    boolean scheduleExportInProgress;
    private boolean pdfImportInProgress;
    private String lastParseDiagnosticsSummary = "暂无导入记录";
    String lastParseDiagnosticsText = "";
    private int bottomNavScrollAccumulator;
    private boolean weekSwipeHintScheduled;
    private View weekSwipeHintView;
    private View courseSaveUndoView;
    private EditText activeAiImportInput;
    private TextView activeAiImportErrorView;
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
        applyEdgeToEdgeWindow(getWindow());
        scheduleRepository = new ScheduleRepository(this);
        planRepository = new PlanRepository(this);
        importCoordinator = new PdfImportCoordinator(this);
        activeScheduleId = scheduleRepository.activeScheduleId();
        darkMode = scheduleRepository.loadGlobalDarkMode();
        applyAccountProfile(scheduleRepository.loadAccountProfile());
        applyConfig(scheduleRepository.loadConfig(activeScheduleId));
        currentWeek = currentWeekFromDate();
        loadActiveCourses();
        reloadStudyPlans();
        buildLayout();
        renderSchedule();
        Intent launchIntent = getIntent();
        Uri launchUri = launchIntent == null ? null : launchIntent.getData();
        if (ScheduleShareCodec.isImportLink(launchUri)) {
            startSharedScheduleImportFlow(launchUri);
        } else if (isScheduleShareFile(
                launchUri, launchIntent == null ? null : launchIntent.getType())) {
            startSharedScheduleFileImportFlow(launchUri);
        } else if (launchUri != null) {
            startPdfImportFlow(launchUri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rootView != null) {
            rootView.post(this::handleExternalAiReturn);
        }
        maybeWarnAboutMissedReminders();
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
            PlanReminderScheduler.reschedule(this);
        } else {
            remindersEnabled = false;
            CourseReminderScheduler.cancelAll(this);
            refreshActiveSettingsPage();
            Toast.makeText(this, getString(R.string.reminder_perm_denied), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        aiExternalImportReturnController.onHostPaused();
        todayOverviewHandler.removeCallbacks(todayOverviewTicker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        scheduleExportExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (planManageOverlay != null && planManageOverlay.getVisibility() == View.VISIBLE) {
            closePlanManagePanel();
            return;
        }
        if (settingsPage != null && settingsPage.getVisibility() == View.VISIBLE) {
            closeSettingsPage();
            return;
        }
        super.onBackPressed();
    }

    private void buildLayout() {
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        // 横屏平板：顶栏采用横向并排布局，压缩垂直占用。
        boolean wideTopPanel = isLandscapeTablet();
        // 布局重建（旋转等）后回到「我的页居中」初始模式，侧栏状态不跨重建保留。
        tabletSettingsOpen = false;
        paneDivider = null;
        weekSelectorButton = null;
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

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);

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
        if (wideTopPanel) {
            // 横屏平板：周选择做成顶部下拉按钮，置于操作区最前。
            weekSelectorButton = buildWeekSelectorButton();
            actions.addView(weekSelectorButton);
        }
        returnCurrentWeekButton = topHeaderAction(
                getString(R.string.return_to_current_week), v -> returnToCurrentWeek());
        returnCurrentWeekButton.setContentDescription(
                getString(R.string.return_to_current_week));
        returnCurrentWeekButton.setVisibility(View.GONE);
        actions.addView(returnCurrentWeekButton);
        overflowMenuButton = transparentTopButton("···", v -> {
            v.animate().rotationBy(90f).scaleX(0.88f).scaleY(0.88f).setDuration(90)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                    .start();
            showActionPanel(v);
        });
        overflowMenuButton.setContentDescription(getString(R.string.more_actions_cd));
        actions.addView(overflowMenuButton);

        LinearLayout headline = new LinearLayout(this);
        headline.setOrientation(LinearLayout.HORIZONTAL);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        // 横屏平板且右侧空间充足：今日概览独立到右侧顶部面板，顶栏不再内嵌。
        boolean separateTodayPanel = isLandscapeTablet() && rightPanelSpace() >= dp(DesignTokens.TABLET_SEPARATE_TODAY_MIN);
        if (wideTopPanel) {
            if (separateTodayPanel) {
                // 横屏平板：标题区占满剩余空间，操作按钮置于右侧。
                LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                headingParams.rightMargin = dp(14);
                headline.addView(heading, headingParams);
            } else {
                // 横屏平板（空间不足）：标题区按内容自适应，今日概览占据剩余空间。
                LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                headingParams.rightMargin = dp(14);
                headline.addView(heading, headingParams);
                todayOverviewView = new TodayOverviewView(this);
                todayOverviewView.setVisualTheme(visualTheme);
                todayOverviewView.setOnCourseClickListener(this::showCourseDetail);
                headline.addView(todayOverviewView, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            headline.addView(actions);
        } else {
            headline.addView(heading, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            headline.addView(actions);
        }
        topPanel.addView(headline);

        scheduleBoard = new ScheduleBoardView(this);
        scheduleBoard.setOnCourseClickListener(this::showCourseDetail);
        scheduleBoard.setOnCourseLongClickListener(this::showCourseEditor);
        scheduleBoard.setOnCourseDragListener(this::handleCourseDragDrop);
        scheduleBoard.setOnPracticeBannerClickListener(this::showPracticeCourses);
        scheduleBoard.setOnVerticalScrollListener(this::handleScheduleVerticalScroll);
        scheduleBoard.setOnSlotLongClickListener((day, section) ->
                showCourseEditor(new Course(day, section, section, "", defaultWeeks(), "", "", "")));
        scheduleBoard.setOnWeekSwipeListener(this::onBoardWeekChanged);
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
        // 横屏平板右侧空间充足时，本周实践改由右侧面板展示，板内横幅关闭。
        scheduleBoard.setShowPracticeBanner(
                isLandscapeTablet() && hasPracticePanelSpace() ? false : showPracticeBanner);
        scheduleBoard.setCourseMetrics(courseCellHeight, courseCornerRadius);
        scheduleBoard.setCourseBlockOpacity(courseBlockOpacity);
        scheduleBoard.setOverlayInsets(scheduleOverlayTopInset(tablet), bottomContentInset());
        scheduleBoard.setBackgroundImage(backgroundImageUri, backgroundImageCrop);
        scheduleBoard.setCourses(courses);

        if (!wideTopPanel) {
            todayOverviewView = new TodayOverviewView(this);
            todayOverviewView.setVisualTheme(visualTheme);
            todayOverviewView.setOnCourseClickListener(this::showCourseDetail);
            LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            overviewParams.topMargin = dp(5);
            topPanel.addView(todayOverviewView, overviewParams);
        } else if (separateTodayPanel) {
            // 横屏平板：今日概览独立到右侧顶部面板（面板容器在 contentHost 构建时挂载）。
            todayOverviewView = new TodayOverviewView(this);
            todayOverviewView.setVisualTheme(visualTheme);
            todayOverviewView.setOnCourseClickListener(this::showCourseDetail);
        }
        updateTodayOverview();

        conflictSummaryView = new CourseConflictSummaryView(this);
        conflictSummaryView.setCompact(wideTopPanel);
        conflictSummaryView.setOnClickListener(v -> showCurrentWeekConflicts());
        LinearLayout.LayoutParams conflictParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        conflictParams.topMargin = dp(5);
        topPanel.addView(conflictSummaryView, conflictParams);
        updateConflictSummary();

        myPage = buildMyPage();
        planPage = buildPlanPage();
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
        contentHost.addView(planPage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (isLandscapeTablet()) {
            // 平板：我的页初始全宽居中；点击设置后收缩为左侧侧栏，
            // 右侧设置面板 + 分隔线（paneDivider 默认隐藏，侧栏模式才显示）。
            contentHost.addView(myPage, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            paneDivider = new View(this);
            paneDivider.setBackgroundColor(PolarisVisualTheme.outlineColor(
                    visualTheme, isDarkModeActive()));
            FrameLayout.LayoutParams dividerParams = new FrameLayout.LayoutParams(
                    dp(1), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT);
            dividerParams.leftMargin = dp(DesignTokens.TABLET_SETTINGS_SPLIT - 1);
            paneDivider.setVisibility(View.GONE);
            contentHost.addView(paneDivider, dividerParams);
            FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            settingsParams.leftMargin = dp(DesignTokens.TABLET_SETTINGS_SPLIT);
            contentHost.addView(settingsPage, settingsParams);
            // 课表右侧本周实践面板：毛玻璃容器（内容决定尺寸）+ 内容层，初始隐藏。
            practiceSidePanel = (FrameLayout) glassLayer(floatingPanelBg(bottomNavOpacity, DesignTokens.RADIUS_SIDE_PANEL), DesignTokens.RADIUS_SIDE_PANEL);
            practiceSidePanelContent = new LinearLayout(this);
            practiceSidePanelContent.setOrientation(LinearLayout.VERTICAL);
            practiceSidePanel.addView(practiceSidePanelContent,
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT));
            practiceSidePanel.setVisibility(View.GONE);
            contentHost.addView(practiceSidePanel, new FrameLayout.LayoutParams(
                    dp(DesignTokens.TABLET_PRACTICE_PANEL_WIDTH), FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.RIGHT | Gravity.TOP));
            if (separateTodayPanel) {
                // 今日概览独立面板：右侧顶部，实践面板上方。
                buildTodayOverviewPanel();
            }
            // 本周计划面板：毛玻璃容器（内容可滚动，占满右侧剩余空间）。
            planSidePanel = (FrameLayout) glassLayer(floatingPanelBg(bottomNavOpacity, DesignTokens.RADIUS_SIDE_PANEL), DesignTokens.RADIUS_SIDE_PANEL);
            ScrollView planScroll = new ScrollView(this);
            planScroll.setFillViewport(true);
            planScroll.setVerticalScrollBarEnabled(false);
            planScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            planSidePanel.addView(planScroll, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            planSidePanelContent = new LinearLayout(this);
            planSidePanelContent.setOrientation(LinearLayout.VERTICAL);
            // 水平内边距让内部计划卡片缩进到圆角内侧，避免白底超出玻璃框。
            planSidePanelContent.setPadding(dp(8), 0, dp(8), dp(8));
            planScroll.addView(planSidePanelContent);
            planSidePanel.setVisibility(View.GONE);
            contentHost.addView(planSidePanel, new FrameLayout.LayoutParams(
                    dp(DesignTokens.TABLET_PLAN_PANEL_WIDTH), LinearLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.RIGHT | Gravity.TOP));
        } else {
            contentHost.addView(myPage, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            contentHost.addView(settingsPage, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            practiceSidePanel = null;
        }
        rootView.addView(contentHost, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams topParams;
        if (isLandscapeTablet()) {
            // 横屏平板：顶栏左贴边、宽度与课表网格一致（两侧对齐）。
            topParams = new FrameLayout.LayoutParams(
                    tabletGridWidth(), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP);
            topParams.setMargins(0, statusBarHeight() + dp(8), 0, 0);
        } else {
            topParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP);
            topParams.setMargins(dp(tablet ? DesignTokens.MARGIN_PAGE_TABLET : DesignTokens.MARGIN_PAGE_PHONE)
                            + systemLeftInset, statusBarHeight() + dp(DesignTokens.GAP_SHELL),
                    dp(tablet ? DesignTokens.MARGIN_PAGE_TABLET : DesignTokens.MARGIN_PAGE_PHONE)
                            + systemRightInset, 0);
        }
        topPanelContainer = new FrameLayout(this);
        topPanelGlassLayer = glassLayer(liquidGlassBg(timetableHeaderOpacity), DesignTokens.RADIUS_TOP_PANEL);
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
        if (isLandscapeTablet()) {
            // 平板横屏：计划管理浮层（遮罩 + 右侧手机宽面板），盖在最上层。
            buildPlanManageOverlay();
        }
        recordBuiltInsets();
        attachWindowInsetsListener();
        setContentView(rootView);
        updateSystemBarAppearance(getWindow());
        if (isLandscapeTablet()) {
            // 我的页初始为居中内容列模式。
            applyMyPageMode(false);
        }
        updateTodayOverviewPanel();
        updatePracticeSidePanel();
        updatePlanSidePanel();
        switchTab(activeTab);
        scheduleBoard.post(this::renderSchedule);
        scheduleWeekSwipeHintIfNeeded();
    }

    private void openPdfPicker() {
        if (selectedParserModel == null) {
            scheduleDialogs.showParserModelDialog(this::launchPdfPicker);
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

    private void showAiImportDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(getString(R.string.import_ai_title));

        TextView instruction = new TextView(this);
        instruction.setText(getString(R.string.import_ai_instruction));


        instruction.setTextColor(mutedColor());
        instruction.setTextSize(14);
        instruction.setLineSpacing(dp(3), 1f);
        instruction.setPadding(0, 0, 0, dp(6));
        panel.addView(instruction);

        panel.addView(dialogAction(getString(R.string.import_ai_copy_prompt), v -> copyAiRecognitionPrompt()));
        panel.addView(dialogAction(getString(R.string.import_ai_pick_image),
                v -> openAiRecognitionImagePicker()));

        EditText input = new EditText(this);
        input.setHint(getString(R.string.import_ai_paste_hint));
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
        input.setBackground(roundedBg(groupColorHex(), DesignTokens.RADIUS_CHIP));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200_000)});
        input.setContentDescription(getString(R.string.import_ai_input_cd));
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
        errorView.setBackground(roundedBg(groupColorHex(), DesignTokens.RADIUS_CHIP));
        errorView.setTextIsSelectable(true);
        errorView.setVisibility(View.GONE);
        panel.addView(errorView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        activeAiImportInput = input;
        activeAiImportErrorView = errorView;

        panel.addView(dialogAction(getString(R.string.import_ai_paste),
                v -> pasteAiTextFromClipboard(input, errorView)));

        TextView parse = dialogAction(getString(R.string.import_ai_parse), v -> {
            String aiText = input.getText() == null
                    ? "" : input.getText().toString();
            if (aiText.trim().isEmpty()) {
                showAiImportErrors(errorView,
                        Collections.singletonList(getString(R.string.import_ai_error_empty)));
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
        parse.setBackground(roundedBg(primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        parse.setContentDescription(getString(R.string.import_ai_parse_cd));
        panel.addView(parse);
        panel.addView(dialogAction(getString(R.string.editor_action_cancel), v -> dialog.dismiss()));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnDismissListener(ignored -> {
            if (activeAiImportInput == input) {
                activeAiImportInput = null;
                activeAiImportErrorView = null;
                aiExternalImportReturnController.cancel();
            }
        });
        dialog.setContentView(glassDialogContent(scroll, panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void copyAiRecognitionPrompt() {
        if (copyAiRecognitionPromptToClipboard()) {
            Toast.makeText(this, getString(R.string.import_ai_prompt_copied), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean copyAiRecognitionPromptToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, getString(R.string.import_clipboard_unavailable), Toast.LENGTH_LONG).show();
            return false;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.import_clipboard_label), PolarisAiPromptV1.getPrompt()));
        return true;
    }

    private void openAiRecognitionImagePicker() {
        Intent pickImage = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pickImage.addCategory(Intent.CATEGORY_OPENABLE);
        pickImage.setType("image/*");
        try {
            startActivityForResult(pickImage, PICK_AI_RECOGNITION_IMAGE);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, getString(R.string.import_no_image_picker), Toast.LENGTH_LONG).show();
        }
    }

    private void launchExternalAiRecognition(Uri scheduleImageUri) {
        if (!copyAiRecognitionPromptToClipboard()) {
            return;
        }

        Intent sendImage = new Intent(Intent.ACTION_SEND);
        sendImage.setType("image/*");
        sendImage.putExtra(Intent.EXTRA_TEXT, PolarisAiPromptV1.getPrompt());
        sendImage.putExtra(Intent.EXTRA_STREAM, scheduleImageUri);
        sendImage.setClipData(ClipData.newRawUri(
                getString(R.string.import_ai_share_image_label), scheduleImageUri));
        sendImage.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        aiExternalImportReturnController.begin(readClipboardText());
        Toast.makeText(this, getString(R.string.import_ai_send_hint),
                Toast.LENGTH_LONG).show();
        try {
            startActivity(Intent.createChooser(
                    sendImage, getString(R.string.import_ai_chooser_title)));
        } catch (ActivityNotFoundException exception) {
            aiExternalImportReturnController.cancel();
            Toast.makeText(this, getString(R.string.import_no_ai_app),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void handleExternalAiReturn() {
        if (!aiExternalImportReturnController.shouldCheckClipboardOnResume()) {
            return;
        }
        AiExternalImportReturnController.ReturnResult result =
                aiExternalImportReturnController.onHostResumed(readClipboardText());
        if (!result.returnedFromExternalApp) {
            return;
        }
        if (!result.hasNewClipboardText()) {
            Toast.makeText(this, getString(R.string.import_ai_return_none),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (activeAiImportInput == null || activeAiImportErrorView == null) {
            return;
        }
        activeAiImportInput.setText(result.newClipboardText);
        activeAiImportInput.setSelection(activeAiImportInput.length());
        activeAiImportErrorView.setVisibility(View.GONE);
        activeAiImportInput.announceForAccessibility(getString(R.string.import_ai_return_read_cd));
        Toast.makeText(this, getString(R.string.import_ai_return_read_cd),
                Toast.LENGTH_SHORT).show();
    }

    private String readClipboardText() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        try {
            ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
            CharSequence text = clip == null || clip.getItemCount() == 0
                    ? null : clip.getItemAt(0).coerceToText(this);
            return text == null ? "" : text.toString();
        } catch (SecurityException exception) {
            Log.w(TAG, "Clipboard access was denied", exception);
            return "";
        }
    }

    void pasteAiTextFromClipboard(EditText input, TextView errorView) {
        String text = readClipboardText();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.import_clipboard_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        input.setText(text);
        input.setSelection(input.length());
        errorView.setVisibility(View.GONE);
        input.announceForAccessibility(getString(R.string.import_ai_paste_done_cd));
    }

    private void showAiImportErrors(TextView errorView, List<String> messages) {
        StringBuilder text = new StringBuilder(getString(R.string.import_ai_errors_header));
        if (messages == null || messages.isEmpty()) {
            text.append("\n").append(getString(R.string.import_ai_parse_failed));
        } else {
            for (String message : messages) {
                text.append("\n\n").append(message);
            }
        }
        errorView.setText(text.toString());
        errorView.setContentDescription(getString(R.string.import_ai_errors_cd, text));
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
        } else if (requestCode == PICK_SHARED_SCHEDULE_FILE) {
            startSharedScheduleFileImportFlow(data.getData());
        } else if (requestCode == PICK_BACKUP_FILE) {
            startBackupRestoreFlow(data.getData());
        } else if (requestCode == PICK_AI_RECOGNITION_IMAGE) {
            Uri scheduleImageUri = data.getData();
            if (rootView != null) {
                rootView.post(() -> {
                    if (activeAiImportInput != null) {
                        launchExternalAiRecognition(scheduleImageUri);
                    }
                });
            }
        } else if (requestCode == PICK_BACKGROUND_IMAGE) {
            previewBackgroundImage(data.getData());
        } else if (requestCode == PICK_AVATAR_IMAGE) {
            if (accountProfileDialog == null || !accountProfileDialog.isShowing()) {
                showAccountProfileEditor();
            }
            previewAvatarImage(data.getData());
        }
    }

    private void showAiImportPreview(AiScheduleImportWorkflow.PrepareSuccess prepared) {
        ScheduleImportPreviewData preview = prepared.preview;
        if (preview == null || preview.isEmpty()) {
            Toast.makeText(this, getString(R.string.import_ai_no_courses), Toast.LENGTH_LONG).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(getString(R.string.import_ai_review_title));
        addScheduleImportCandidatePreview(panel, preview);

        boolean replacingExisting = !structuredCourses.isEmpty();
        TextView replacementNotice = new TextView(this);
        replacementNotice.setText(replacingExisting
                ? getString(R.string.import_ai_note_overwrite)
                : getString(R.string.import_ai_note_import));
        replacementNotice.setTextColor(replacingExisting
                ? Color.parseColor(isDarkModeActive() ? "#FFC266" : "#8A4B00")
                : mutedColor());
        replacementNotice.setTextSize(14);
        replacementNotice.setLineSpacing(dp(3), 1f);
        replacementNotice.setPadding(0, dp(12), 0, dp(4));
        panel.addView(replacementNotice);

        ScheduleImportConfirmation confirmation = new ScheduleImportConfirmation();
        String confirmText = replacingExisting ? getString(R.string.import_confirm_overwrite) : getString(R.string.import_confirm_short);
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
                        getString(R.string.import_ai_summary, courseCount,
                                preview.meetingCount()),
                        getString(R.string.import_ai_detail, courseCount,
                                /* 协议与写入说明在资源内 */


                                preview.meetingCount()),
                        getString(R.string.import_ai_success_toast, courseCount));
            });
        });
        confirm.setTextColor(Color.WHITE);
        confirm.setBackground(roundedBg(primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        confirm.setContentDescription(confirmText + getString(R.string.import_confirm_cd_overwrite));
        panel.addView(confirm);
        panel.addView(dialogAction(getString(R.string.editor_action_cancel), v -> {
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
        dialog.setContentView(glassDialogContent(scroll, panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    private void addScheduleImportCandidatePreview(
            LinearLayout panel, ScheduleImportPreviewData preview) {
        panel.addView(importReviewHeading(getString(R.string.import_candidates_heading)));
        panel.addView(importReviewLine(getString(R.string.import_review_recognized),
                getString(R.string.import_candidates_count, preview.regularCourseCount(),
                        preview.practiceCourseCount()),
                false));
        if (preview.semester != null && !preview.semester.trim().isEmpty()) {
            panel.addView(importReviewLine(getString(R.string.import_review_semester), preview.semester, false));
        }

        for (StructuredCourse course : preview.courses) {
            if (course != null) {
                panel.addView(scheduleImportCourseCard(course, preview));
            }
        }

        if (!preview.warnings.isEmpty()) {
            panel.addView(importReviewHeading(getString(R.string.import_review_needs_check)));
            for (String warning : preview.warnings) {
                panel.addView(importReviewLine(getString(R.string.import_review_hint), warning, true));
            }
        }
    }

    private View scheduleImportCourseCard(StructuredCourse course,
                                          ScheduleImportPreviewData preview) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedBg(groupColorHex(), DesignTokens.RADIUS_CARD));
        card.setContentDescription(getString(R.string.import_course_cd, course.name));

        TextView name = new TextView(this);
        name.setText(course.name);
        name.setTextColor(inkColor());
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(false);
        card.addView(name);

        if (!course.teacher.trim().isEmpty()) {
            card.addView(scheduleImportDetail(getString(R.string.import_detail_teacher, course.teacher), false));
        }
        if (!course.credit.trim().isEmpty()) {
            card.addView(scheduleImportDetail(getString(R.string.import_detail_credit, course.credit), false));
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
                ? getString(R.string.editor_week_unknown_full) : meeting.weekRule.displayText();
        if (course.courseType == CourseType.PRACTICE
                && meeting.timeMode == CourseTimeMode.NONE) {
            return weeks + "\n" + getString(R.string.import_no_fixed_time);
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
                    .append(meeting.endSection).append(getString(R.string.import_section_suffix));
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
            Toast.makeText(this, getString(R.string.import_ai_no_courses), Toast.LENGTH_LONG).show();
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
            scheduleDialogs.showParserModelDialog(() -> startPdfImportFlow(uri));
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
        LinearLayout panel = dialogPanel(getString(R.string.import_overwrite_title));
        final SchoolParserModel[] importParserModel = {selectedParserModel};
        TextView message = new TextView(this);
        message.setText(getString(R.string.import_overwrite_message));
        message.setTextColor(mutedColor());
        message.setTextSize(15);
        message.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.setMargins(0, 0, 0, dp(8));
        panel.addView(message, messageParams);
        final TextView[] parserChoice = new TextView[1];
        parserChoice[0] = dialogAction(importParserModel[0] == null
                ? getString(R.string.import_pick_model)
                : getString(R.string.import_school_value, importParserModel[0].label), v -> {
            Dialog chooser = new Dialog(this);
            LinearLayout chooserPanel = dialogPanel(getString(R.string.import_school_chooser_title));
            for (SchoolParserModel model : SchoolParserModel.values()) {
                chooserPanel.addView(dialogAction(model.label, item -> {
                    importParserModel[0] = model;
                    parserChoice[0].setText(getString(R.string.import_school_value, model.label));
                    chooser.dismiss();
                }));
            }
            chooser.setContentView(glassDialogContent(chooserPanel, DesignTokens.RADIUS_DIALOG_SHEET));
            chooser.show();
            transparentDialog(chooser);
        });
        panel.addView(parserChoice[0]);
        TextView cover = dialogAction(getString(R.string.import_parse_and_check), v -> {
            if (importParserModel[0] == null) {
                Toast.makeText(this, getString(R.string.import_error_pick_model), Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            loadPdf(uri, new ImportDestination(activeScheduleId, scheduleName,
                    importParserModel[0], false, courses));
        });
        TextView create = dialogAction(getString(R.string.import_create_and_check), v -> {
            if (importParserModel[0] == null) {
                Toast.makeText(this, getString(R.string.import_error_create_pick_model), Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            showImportNameDialog(uri, true, importParserModel[0]);
        });
        TextView cancel = dialogAction(getString(R.string.editor_action_cancel), v -> {
            dialog.dismiss();
        });
        panel.addView(cover);
        panel.addView(create);
        panel.addView(cancel);
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
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
        item.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CARD));
        item.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(6), 0, dp(6));
        item.setLayoutParams(params);
        return item;
    }

    private void showImportNameDialog(Uri uri, boolean createNewSchedule, SchoolParserModel parserModel) {
        if (parserModel == null) {
            Toast.makeText(this, getString(R.string.import_error_create_pick_model), Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(getString(R.string.import_name_title));
        EditText nameInput = input(getString(R.string.settings_row_schedule_name), createNewSchedule ? nextScheduleName()
                : scheduleName.length() == 0 ? nextScheduleName() : scheduleName);
        panel.addView(nameInput);
        TextView startImport = dialogAction(getString(R.string.import_start_parse), v -> {
            String name = nameInput.getText().toString().trim();
            if (name.length() == 0) {
                Toast.makeText(this, getString(R.string.import_error_name_empty), Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            loadPdf(uri, new ImportDestination(activeScheduleId, name,
                    parserModel, createNewSchedule,
                    createNewSchedule ? Collections.<Course>emptyList() : courses));
        });
        startImport.setTextColor(Color.WHITE);
        startImport.setBackground(roundedBg(primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        panel.addView(startImport);
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    private String nextScheduleName() {
        int index = scheduleRepository.loadSchedules().size() + 1;
        return "新课表" + index;
    }

    private void loadPdf(Uri uri, ImportDestination destination) {
        if (pdfImportInProgress) {
            Toast.makeText(this, getString(R.string.import_parsing_busy), Toast.LENGTH_SHORT).show();
            return;
        }
        pdfImportInProgress = true;
        try {
            importCoordinator.importPdf(uri, destination.parserModel,
                    new PdfImportCoordinator.Callback() {
            @Override
            public void onImportStarted(String displayName) {
                setHeader(displayName, getString(R.string.import_parsing_header));
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
                        ? getString(R.string.import_reason_unknown) : exception.getMessage();
                lastParseDiagnosticsSummary = getString(R.string.import_diag_failed);
                lastParseDiagnosticsText = getString(R.string.import_diag_failure, reason);
                setHeader(currentTitle, getString(R.string.import_failed_header));
                Toast.makeText(MainActivity.this, getString(R.string.import_pdf_failed, reason),
                        Toast.LENGTH_LONG).show();
            }
            });
        } catch (RuntimeException exception) {
            pdfImportInProgress = false;
            String reason = exception.getMessage() == null
                    ? getString(R.string.import_reason_unknown) : exception.getMessage();
            setHeader(currentTitle, getString(R.string.import_failed_header));
            Toast.makeText(this, getString(R.string.import_launch_failed, reason),
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
        LinearLayout panel = dialogPanel(getString(R.string.import_review_title));
        TextView summary = new TextView(this);
        summary.setText(review.canImport()
                ? getString(R.string.import_review_count, review.courseCount,
                        review.meetingCount)
                : getString(R.string.import_review_empty));
        summary.setTextColor(inkColor());
        summary.setTextSize(18);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(0, 0, 0, dp(10));
        panel.addView(summary);

        String importedSemester = result.semesterName.length() > 0
                ? result.semesterName
                : SemesterStartDateDefaults.resolveSemesterName(Calendar.getInstance());
        panel.addView(importReviewLine(getString(R.string.import_review_label_school), destination.parserModel.label, false));
        panel.addView(importReviewLine(getString(R.string.import_review_label_semester), importedSemester, false));
        panel.addView(importReviewLine(getString(R.string.import_review_pages), getString(R.string.import_pages_value, Math.max(0, result.pageCount)), false));
        panel.addView(importReviewLine(getString(R.string.import_review_start_date), getString(R.string.import_review_date_pending), false));

        panel.addView(importReviewHeading(destination.createNewSchedule
                ? getString(R.string.import_review_destination_new)
                : replacingExisting ? getString(R.string.import_review_destination_compare) : getString(R.string.import_review_destination_current)));
        panel.addView(importReviewLine(getString(R.string.import_review_added), getString(R.string.import_count_value, review.addedCount), false));
        panel.addView(importReviewLine(getString(R.string.import_review_modified), getString(R.string.import_count_value, review.modifiedCount),
                review.modifiedCount > 0));
        panel.addView(importReviewLine(getString(R.string.import_review_removed), getString(R.string.import_count_value, review.removedCount),
                review.removedCount > 0));

        panel.addView(importReviewHeading(getString(R.string.import_review_needs_check)));
        if (!review.hasIssues()) {
            panel.addView(importReviewLine(getString(R.string.import_review_check_result), getString(R.string.import_review_no_issues), false));
        } else {
            if (review.errorCount > 0) {
                panel.addView(importReviewLine(getString(R.string.import_review_parse_errors), getString(R.string.import_count_value, review.errorCount), true));
            }
            if (review.warningCount > 0) {
                panel.addView(importReviewLine(getString(R.string.import_review_warnings), getString(R.string.import_count_value, review.warningCount), true));
            }
            if (review.unknownWeekCount > 0) {
                panel.addView(importReviewLine(getString(R.string.import_review_unknown_weeks),
                        getString(R.string.import_arrangements_value, review.unknownWeekCount), true));
            }
            if (review.missingLocationCount > 0) {
                panel.addView(importReviewLine(getString(R.string.import_review_missing_location),
                        getString(R.string.import_arrangements_value, review.missingLocationCount), true));
            }
            if (review.missingTeacherCount > 0) {
                panel.addView(importReviewLine(getString(R.string.import_review_missing_teacher),
                        getString(R.string.import_arrangements_value, review.missingTeacherCount), true));
            }
            if (review.conflictCount > 0) {
                panel.addView(importReviewLine(getString(R.string.import_review_conflicts),
                        getString(R.string.import_conflict_value, review.conflictCount), true));
            }
        }

        if (lastParseDiagnosticsText.length() > 0) {
            panel.addView(dialogAction(getString(R.string.import_review_diagnostics), v -> scheduleDialogs.showParseDiagnosticsDialog()));
        }
        if (review.canImport()) {
            String confirmText = destination.createNewSchedule
                    ? getString(R.string.import_confirm_new)
                    : replacingExisting ? getString(R.string.import_confirm_overwrite) : getString(R.string.import_confirm_import_pdf);
            TextView confirm = dialogAction(confirmText, v -> {
                dialog.dismiss();
                applyReviewedImport(result, destination, importedSemesterWeeks);
            });
            confirm.setTextColor(Color.WHITE);
            confirm.setBackground(roundedBg(primaryActionFillHex(), DesignTokens.RADIUS_CARD));
            confirm.setContentDescription(confirmText + getString(R.string.import_confirm_cd_review));
            panel.addView(confirm);
        } else {
            TextView blocked = new TextView(this);
            blocked.setText(getString(R.string.import_blocked_message));
            blocked.setTextColor(mutedColor());
            blocked.setTextSize(14);
            blocked.setLineSpacing(dp(3), 1f);
            blocked.setPadding(0, dp(8), 0, dp(4));
            panel.addView(blocked);
        }
        panel.addView(dialogAction(review.canImport() ? getString(R.string.import_cancel_import) : getString(R.string.import_close),
                v -> dialog.dismiss()));

        ScrollView reviewScroll = new ScrollView(this);
        reviewScroll.setFillViewport(false);
        reviewScroll.setVerticalScrollBarEnabled(true);
        reviewScroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(glassDialogContent(reviewScroll, panel, DesignTokens.RADIUS_DIALOG_SHEET));
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
                ? getString(R.string.review_needs_check_line, label, value) : getString(R.string.review_line, label, value));
        return line;
    }

    private void applyReviewedImport(ParseResult result,
                                     ImportDestination destination,
                                     int importedSemesterWeeks) {
        if (!result.courses.isEmpty() && result.structuredCourses.isEmpty()) {
            Toast.makeText(this,
                    getString(R.string.structured_data_missing), Toast.LENGTH_LONG).show();
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
        scheduleDialogs.showImportedFirstWeekDayDialog();
    }

    private String parseSubtitle(ParseResult result) {
        if (result.courses.isEmpty()) {
            String reason = firstParseMessage(result);
            return reason.length() == 0 ? getString(R.string.parse_no_courses_hint) : reason;
        }
        if (result.errors.isEmpty()) {
            return getString(R.string.parse_ok_summary, result.courses.size(), result.pageCount);
        }
        return getString(R.string.parse_ok_summary_warnings, result.courses.size(), result.errors.size());
    }

    private String parseToast(ParseResult result) {
        String message = firstParseMessage(result);
        if (message.length() > 0) {
            return message;
        }
        if (!result.success) {
            return getString(R.string.parse_failed_hint);
        }
        return getString(R.string.parse_partial_hint);
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

    void renderSchedule() {
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
            // 横屏平板右侧空间充足时，本周实践改由右侧面板展示，板内横幅关闭。
            scheduleBoard.setShowPracticeBanner(
                    isLandscapeTablet() && hasPracticePanelSpace() ? false : showPracticeBanner);
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
        // 横屏平板：顶栏宽度与课表网格一致——周六/周日开关变化后即时重算。
        if (isLandscapeTablet() && topPanelContainer != null) {
            FrameLayout.LayoutParams topParams =
                    (FrameLayout.LayoutParams) topPanelContainer.getLayoutParams();
            int gridWidth = tabletGridWidth();
            if (topParams.width != gridWidth) {
                topParams.width = gridWidth;
                topPanelContainer.setLayoutParams(topParams);
            }
        }
        updateTodayOverviewPanel();
        updatePracticeSidePanel();
        updatePlanSidePanel();
    }

    private void showCourseDetail(Course course) {
        if (course == null) {
            return;
        }
        new CourseDetailDialog(this, isDarkModeActive(), dialogBlurSource(), courseTimeSettings())
                .show(course, this::showCourseEditor);
    }

    private void handleCourseDragDrop(Course course, int day, int section) {
        if (course == null || !StableMeetingId.isValid(course.meetingId)
                || !StableCourseId.isValid(course.structuredCourseId)) {
            Toast.makeText(this, getString(R.string.drag_not_allowed), Toast.LENGTH_SHORT).show();
            return;
        }
        int courseIndex = -1;
        int meetingIndex = -1;
        for (int i = 0; i < structuredCourses.size(); i++) {
            StructuredCourse candidate = structuredCourses.get(i);
            if (candidate == null
                    || !candidate.id.equalsIgnoreCase(course.structuredCourseId)) {
                continue;
            }
            for (int j = 0; j < candidate.meetings.size(); j++) {
                CourseMeeting meeting = candidate.meetings.get(j);
                if (meeting != null && meeting.id.equalsIgnoreCase(course.meetingId)) {
                    courseIndex = i;
                    meetingIndex = j;
                    break;
                }
            }
            if (courseIndex >= 0) {
                break;
            }
        }
        if (courseIndex < 0 || meetingIndex < 0) {
            Toast.makeText(this, getString(R.string.drag_stale_retry), Toast.LENGTH_SHORT).show();
            return;
        }
        CourseMeeting original = structuredCourses.get(courseIndex)
                .meetings.get(meetingIndex);
        int newStart = original.startSection;
        int newEnd = original.endSection;
        if (original.timeMode == CourseTimeMode.SECTION) {
            int span = Math.max(1, original.endSection - original.startSection + 1);
            newStart = Math.max(1, Math.min(section, courseSectionCount));
            newEnd = Math.min(courseSectionCount, newStart + span - 1);
        }
        if (original.day == day && original.startSection == newStart
                && original.endSection == newEnd) {
            return;
        }
        boolean conflict = hasMeetingConflict(structuredCourses,
                course.structuredCourseId, original.id, day, newStart, newEnd);
        CourseMeeting edited = new CourseMeeting(
                original.id, day, newStart, newEnd, original.weekRule,
                original.location, original.teacher, original.rawText,
                original.timeMode, original.startMinuteOfDay, original.endMinuteOfDay);
        List<StructuredCourse> previous = new ArrayList<>(structuredCourses);
        if (!CourseEditManager.updateMeeting(structuredCourses,
                course.structuredCourseId, original.id, edited)) {
            Toast.makeText(this, getString(R.string.drag_move_failed), Toast.LENGTH_SHORT).show();
            return;
        }
        refreshCourseView();
        scheduleRepository.saveStructuredCourses(activeScheduleId, structuredCourses);
        rescheduleCourseReminders();
        renderSchedule();
        updateEmptyScheduleView();
        updateConflictSummary();
        showCourseSavedUndo(previous);
        if (conflict) {
            Toast.makeText(this, getString(R.string.drag_overlap_warning), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasMeetingConflict(List<StructuredCourse> source,
                                       String structuredCourseId, String meetingId,
                                       int day, int startSection, int endSection) {
        if (day < 0 || day > 6 || startSection > endSection) {
            return false;
        }
        for (StructuredCourse candidate : source) {
            if (candidate == null) {
                continue;
            }
            for (CourseMeeting other : candidate.meetings) {
                if (other == null || other.timeMode != CourseTimeMode.SECTION
                        || other.day != day) {
                    continue;
                }
                if (candidate.id.equalsIgnoreCase(structuredCourseId)
                        && other.id.equalsIgnoreCase(meetingId)) {
                    continue;
                }
                if (other.startSection <= endSection
                        && startSection <= other.endSection) {
                    return true;
                }
            }
        }
        return false;
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

    /**
     * 横屏平板课表网格宽度：委托 ScheduleBoardView 的统一计算，
     * 与板内布局共用同一组常数，避免两处算法漂移导致顶栏与网格错位。
     */
    private int tabletGridWidth() {
        return ScheduleBoardView.gridContentWidth(this,
                getResources().getDisplayMetrics().widthPixels, visibleDayCount);
    }

    /** 横屏平板课表右侧可用空间（减去左右边距，px）。 */
    private int rightPanelSpace() {
        return Math.max(0, getResources().getDisplayMetrics().widthPixels
                - tabletGridWidth() - dp(24));
    }

    /** 右侧实践面板是否需要至少 150dp 空间。 */
    private boolean hasPracticePanelSpace() {
        return isLandscapeTablet() && rightPanelSpace() >= dp(DesignTokens.TABLET_PRACTICE_MIN_WIDTH);
    }

    private int practicePanelWidth() {
        if (todayOverviewPanel != null && todayOverviewPanel.getVisibility() == View.VISIBLE) {
            // 与今日概览面板同宽。
            return todayOverviewPanel.getWidth() > 0
                    ? todayOverviewPanel.getWidth()
                    : Math.min(dp(DesignTokens.PANEL_MAX_WIDTH), rightPanelSpace());
        }
        return Math.max(dp(DesignTokens.TABLET_PRACTICE_MIN_WIDTH), Math.min(dp(DesignTokens.TABLET_PRACTICE_MAX_WIDTH), rightPanelSpace()));
    }

    private List<Course> practiceCoursesForCurrentWeek() {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (course != null && (course.courseType == CourseType.PRACTICE
                    || course.isBannerOnlyCourse())
                    && CourseTimeResolver.isActiveInWeek(course, currentWeek)) {
                result.add(course);
            }
        }
        return result;
    }

    /**
     * 刷新课表右侧的本周实践面板：仅横屏平板且右侧空间充足、
     * 本周有实践、课表 tab 且设置面板未打开时显示。
     */
    private void updatePracticeSidePanel() {
        if (practiceSidePanel == null) {
            return;
        }
        boolean panelEnabled = isLandscapeTablet() && showPracticeBanner
                && hasPracticePanelSpace();
        boolean visible = panelEnabled && activeTab == 0
                && (settingsPage == null
                || settingsPage.getVisibility() != View.VISIBLE);
        List<Course> practices = panelEnabled ? practiceCoursesForCurrentWeek()
                : new ArrayList<>();
        if (!visible || practices.isEmpty()) {
            practiceSidePanel.setVisibility(View.GONE);
            return;
        }
        practiceSidePanelContent.removeAllViews();

        TextView title = new TextView(this);
        title.setText(getString(R.string.board_practice_title));
        title.setTextColor(inkColor());
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(14), dp(10), dp(14), dp(6));
        practiceSidePanelContent.addView(title);

        for (Course course : practices) {
            practiceSidePanelContent.addView(buildPracticePanelItem(course));
        }

        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) practiceSidePanel.getLayoutParams();
        params.width = practicePanelWidth();
        int topInset = scheduleOverlayTopInset(
                getResources().getConfiguration().smallestScreenWidthDp >= 600);
        if (todayOverviewPanel != null && todayOverviewPanel.getVisibility() == View.VISIBLE) {
            // 实践面板排在今日概览面板下方；未布局时用估算高度兜底。
            int todayBottom;
            if (todayOverviewPanel.getHeight() > 0) {
                todayBottom = todayOverviewPanel.getTop() + todayOverviewPanel.getHeight();
            } else {
                FrameLayout.LayoutParams todayParams =
                        (FrameLayout.LayoutParams) todayOverviewPanel.getLayoutParams();
                todayBottom = todayParams.topMargin + dp(90);
            }
            topInset = todayBottom + dp(10);
        }
        params.topMargin = topInset;
        params.rightMargin = dp(12);
        practiceSidePanel.setLayoutParams(params);
        practiceSidePanel.setVisibility(View.VISIBLE);
    }

    /**
     * 刷新右侧顶部的今日概览独立面板：仅横屏平板且右侧空间充足、
     * 课表 tab 且设置面板未打开时显示。
     */
    private void updateTodayOverviewPanel() {
        if (todayOverviewPanel == null) {
            return;
        }
        boolean visible = isLandscapeTablet() && rightPanelSpace() >= dp(DesignTokens.TABLET_SEPARATE_TODAY_MIN)
                && activeTab == 0
                && (settingsPage == null
                || settingsPage.getVisibility() != View.VISIBLE);
        if (!visible) {
            todayOverviewPanel.setVisibility(View.GONE);
            return;
        }
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) todayOverviewPanel.getLayoutParams();
        params.width = Math.min(dp(DesignTokens.PANEL_MAX_WIDTH), rightPanelSpace());
        // 与左侧顶栏顶部平齐。
        params.topMargin = statusBarHeight() + dp(8);
        params.rightMargin = dp(12);
        todayOverviewPanel.setLayoutParams(params);
        todayOverviewPanel.setVisibility(View.VISIBLE);
    }

    private View buildTodayOverviewPanel() {
        todayOverviewView.setLarge(true);
        // 毛玻璃容器（BackdropBlurView 按内容定尺寸）+ 大号今日概览内容。
        FrameLayout host = (FrameLayout) glassLayer(floatingPanelBg(bottomNavOpacity, DesignTokens.RADIUS_SIDE_PANEL), DesignTokens.RADIUS_SIDE_PANEL);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10), dp(10), dp(10), dp(10));
        content.addView(todayOverviewView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        host.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        todayOverviewPanel = host;
        contentHost.addView(host, new FrameLayout.LayoutParams(
                dp(360), LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT | Gravity.TOP));
        host.setVisibility(View.GONE);
        return host;
    }

    /**
     * 刷新右侧下方的本周计划面板：仅横屏平板且右侧空间充足、
     * 课表 tab 且设置面板未打开时显示；排在实践面板下方。
     */
    private void updatePlanSidePanel() {
        if (planSidePanel == null) {
            return;
        }
        boolean visible = isLandscapeTablet() && rightPanelSpace() >= dp(DesignTokens.TABLET_SEPARATE_TODAY_MIN)
                && activeTab == 0
                && (settingsPage == null
                || settingsPage.getVisibility() != View.VISIBLE);
        if (!visible) {
            planSidePanel.setVisibility(View.GONE);
            return;
        }
        planSidePanelContent.removeAllViews();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        // 内容层已有 8dp 水平 padding，这里与「本周实践」面板标题的缩进保持一致。
        header.setPadding(dp(6), dp(10), dp(6), dp(4));
        TextView title = new TextView(this);
        title.setText(getString(R.string.side_panel_plan_title));
        title.setTextColor(inkColor());
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView manage = new TextView(this);
        manage.setText(getString(R.string.common_manage));
        manage.setTextSize(13);
        manage.setTypeface(Typeface.DEFAULT_BOLD);
        manage.setTextColor(PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive()));
        manage.setGravity(Gravity.CENTER_VERTICAL);
        manage.setPadding(dp(8), 0, dp(8), 0);
        manage.setMinHeight(dp(32));
        manage.setOnClickListener(v -> showPlanPage());
        header.addView(manage);
        planSidePanelContent.addView(header);

        List<StudyPlan> weekPlans = new ArrayList<>();
        for (StudyPlan plan : studyPlans) {
            if (plan.week == currentWeek) {
                weekPlans.add(plan);
            }
        }
        Collections.sort(weekPlans, (a, b) -> {
            int doneCompare = Boolean.compare(a.done, b.done);
            return doneCompare != 0 ? doneCompare : a.dayOfWeek - b.dayOfWeek;
        });
        if (weekPlans.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.side_panel_plan_empty));
            empty.setTextColor(mutedColor());
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(18), 0, dp(18));
            empty.setOnClickListener(v -> showPlanPage());
            planSidePanelContent.addView(empty);
        } else {
            for (StudyPlan plan : weekPlans) {
                planSidePanelContent.addView(planRow(plan));
            }
        }

        planSidePanel.setVisibility(View.VISIBLE);
        // 布局完成后定位：实践面板高度（内容数量）变化后，计划面板
        // 自动占据「实践面板下方 → 屏幕底部」的剩余空间。
        planSidePanel.post(this::layoutPlanSidePanel);
    }

    /**
     * 计划面板定位：宽度随右侧空间，顶部排在实践/今日概览面板下方，
     * 高度占满到屏幕底部（底部预留导航栏空间），内部内容可滚动。
     */
    private void layoutPlanSidePanel() {
        if (planSidePanel == null || planSidePanel.getVisibility() != View.VISIBLE) {
            return;
        }
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) planSidePanel.getLayoutParams();
        params.width = practicePanelWidth();
        int topInset = scheduleOverlayTopInset(
                getResources().getConfiguration().smallestScreenWidthDp >= 600);
        View anchor = null;
        if (practiceSidePanel != null
                && practiceSidePanel.getVisibility() == View.VISIBLE
                && practiceSidePanel.getHeight() > 0) {
            anchor = practiceSidePanel;
        } else if (todayOverviewPanel != null
                && todayOverviewPanel.getVisibility() == View.VISIBLE
                && todayOverviewPanel.getHeight() > 0) {
            anchor = todayOverviewPanel;
        }
        if (anchor != null) {
            topInset = anchor.getTop() + anchor.getHeight() + dp(10);
        }
        params.topMargin = topInset;
        params.rightMargin = dp(12);
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int bottomReserve = bottomContentInset() + dp(30);
        params.height = Math.max(dp(140), screenHeight - topInset - bottomReserve);
        planSidePanel.setLayoutParams(params);
    }

    private View buildPracticePanelItem(Course course) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        card.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CARD));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> new CourseDetailDialog(
                this, isDarkModeActive(), dialogBlurSource(), courseTimeSettings())
                .show(course, this::showCourseEditor));

        TextView name = new TextView(this);
        name.setText(course.name == null || course.name.trim().isEmpty()
                ? getString(R.string.board_practice_unnamed) : course.name.trim());
        name.setTextColor(inkColor());
        name.setTextSize(14);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name);

        StringBuilder meta = new StringBuilder(course.isBannerOnlyCourse()
                ? getString(R.string.board_practice_concentrated) : courseTimeInlineText(course));
        if (course.location != null && !course.location.trim().isEmpty()) {
            meta.append(" · ").append(course.location.trim());
        }
        if (course.teacher != null && !course.teacher.trim().isEmpty()) {
            meta.append(getString(R.string.practice_meta_teacher, course.teacher.trim()));
        }
        TextView metaView = new TextView(this);
        metaView.setText(meta.toString());
        metaView.setTextColor(mutedColor());
        metaView.setTextSize(13);
        metaView.setSingleLine(false);
        metaView.setMaxLines(2);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(2);
        card.addView(metaView, metaParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(8), 0, dp(8), dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private void showCurrentWeekConflicts() {
        List<CourseConflictDetector.Conflict> conflicts = CourseConflictDetector.forWeek(
                courses, semesterWeeks, currentWeek, courseTimeSettings());
        if (conflicts.isEmpty()) {
            updateConflictSummary();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(getString(R.string.conflict_week_title, currentWeek));

        TextView explanation = new TextView(this);
        explanation.setText(getString(R.string.conflict_explanation));
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
        panel.addView(dialogAction(getString(R.string.import_close), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
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
        item.setContentDescription(getString(R.string.conflict_item_cd, firstName, secondName,
                dayText(conflict.day) + section + "，" + conflict.commonWeeksText()
                ));
        return item;
    }

    private void showConflictCourseChoice(Dialog conflictDialog,
                                          CourseConflictDetector.Conflict conflict) {
        Dialog chooser = new Dialog(this);
        LinearLayout panel = dialogPanel(getString(R.string.conflict_edit_prefix));
        TextView firstAction = dialogAction(getString(R.string.conflict_edit_prefix, courseChoiceText(conflict.first)), v -> {
            chooser.dismiss();
            conflictDialog.dismiss();
            showCourseEditor(conflict.first);
        });
        firstAction.setSingleLine(true);
        firstAction.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(firstAction);
        TextView secondAction = dialogAction(getString(R.string.conflict_edit_prefix, courseChoiceText(conflict.second)), v -> {
            chooser.dismiss();
            conflictDialog.dismiss();
            showCourseEditor(conflict.second);
        });
        secondAction.setSingleLine(true);
        secondAction.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(secondAction);
        panel.addView(dialogAction(getString(R.string.editor_action_cancel), v -> chooser.dismiss()));
        chooser.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
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
            return getString(R.string.course_unnamed);
        }
        return course.name.trim();
    }

    private String dayText(int day) {

        return day >= 0 && day < WeekdayLabels.count() ? WeekdayLabels.label(this, day)
                : getString(R.string.weekday_undetermined);
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
        titleView.setText(getString(R.string.empty_courses_title));
        titleView.setTextColor(inkColor());
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setGravity(Gravity.CENTER);
        card.addView(titleView);

        TextView message = new TextView(this);
        message.setText(getString(R.string.empty_courses_message));
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
        importButton.setBackground(roundedBg(primaryActionFillHex(), DesignTokens.RADIUS_SMALL));
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


    void showClassTimeTableEditor() {
        showClassTimeTableEditor(null);
    }

    void showClassTimeTableEditor(final Runnable onClosed) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(getString(R.string.classtime_title));
        TextView hint = new TextView(this);
        hint.setText(getString(R.string.classtime_hint));
        hint.setTextColor(mutedColor());
        hint.setTextSize(13);
        hint.setLineSpacing(dp(3), 1f);
        panel.addView(hint);

        final List<int[]> rows = new ArrayList<>(loadClassTimeRows());
        final LinearLayout rowsContainer = new LinearLayout(this);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        final ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(rowsContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(296));
        scrollParams.setMargins(0, dp(4), 0, 0);
        panel.addView(scroll, scrollParams);

        renderClassTimeRows(rowsContainer, rows, dialog, scroll, false);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView example = compactDialogAction(getString(R.string.classtime_fill_example), v -> appearanceDialogs.showChoiceDialog(v, getString(R.string.classtime_fill_example_title),
                new String[]{SchoolParserModel.XUPT.label,
                        SchoolParserModel.XAUT.label, SchoolParserModel.HDU.label},
                "", value -> {
                    for (SchoolParserModel model : SchoolParserModel.values()) {
                        if (model.label.equals(value)) {
                            rows.clear();
                            rows.addAll(rowsFromSchoolModel(model));
                            renderClassTimeRows(rowsContainer, rows, dialog, scroll, true);
                            break;
                        }
                    }
                }));
        TextView paste = compactDialogAction(getString(R.string.classtime_paste_action), v ->
                scheduleDialogs.showClassTimePasteDialog(rows, rowsContainer, dialog, scroll));
        TextView addRow = compactDialogAction(getString(R.string.classtime_add_row), v -> {
            rows.add(nextClassTimeRow(rows));
            renderClassTimeRows(rowsContainer, rows, dialog, scroll, true);
        });
        actions.addView(example);
        actions.addView(paste);
        actions.addView(addRow);
        panel.addView(actions);

        panel.addView(pageSaveButton(() -> {
            if (!validateClassTimeRows(rows)) {
                return;
            }
            applyClassTimeRows(rows);
            saveConfig();
            dialog.dismiss();
            renderSchedule();
            refreshActiveSettingsPage();
            Toast.makeText(this, getString(R.string.classtime_saved_toast), Toast.LENGTH_SHORT).show();
        }));
        if (onClosed != null) {
            dialog.setOnDismissListener(ignored -> onClosed.run());
        }
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }


    void renderClassTimeRows(LinearLayout container, List<int[]> rows,
                                     Dialog owner, ScrollView scroll, boolean scrollToBottom) {
        container.removeAllViews();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            final int index = rowIndex;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = new TextView(this);
            label.setText(getString(R.string.classtime_section_ordinal, index + 1));
            label.setTextColor(mutedColor());
            label.setTextSize(14);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            row.addView(label, new LinearLayout.LayoutParams(dp(54), dp(42)));

            TextView startPill = classTimePill(timeTextMinute(rows.get(index)[0]), v ->
                    appearanceDialogs.showTimeDialog(getString(R.string.classtime_section_start_title, index + 1),
                            timeTextMinute(rows.get(index)[0]), value -> {
                                rows.get(index)[0] = minutesFromTimeText(value);
                                renderClassTimeRows(container, rows, owner, scroll, false);
                            }));
            row.addView(startPill, new LinearLayout.LayoutParams(0, dp(42), 1f));

            TextView dash = new TextView(this);
            dash.setText("–");
            dash.setTextColor(mutedColor());
            dash.setTextSize(16);
            dash.setGravity(Gravity.CENTER);
            row.addView(dash, new LinearLayout.LayoutParams(dp(22), dp(42)));

            TextView endPill = classTimePill(timeTextMinute(rows.get(index)[1]), v ->
                    appearanceDialogs.showTimeDialog(getString(R.string.classtime_section_end_title, index + 1),
                            timeTextMinute(rows.get(index)[1]), value -> {
                                rows.get(index)[1] = minutesFromTimeText(value);
                                renderClassTimeRows(container, rows, owner, scroll, false);
                            }));
            row.addView(endPill, new LinearLayout.LayoutParams(0, dp(42), 1f));

            TextView remove = new TextView(this);
            remove.setText("✕");
            remove.setTextColor(rows.size() > 1 ? mutedColor() : Color.TRANSPARENT);
            remove.setTextSize(15);
            remove.setGravity(Gravity.CENTER);
            remove.setClickable(rows.size() > 1);
            remove.setOnClickListener(v -> {
                if (rows.size() <= 1) {
                    return;
                }
                rows.remove(index);
                renderClassTimeRows(container, rows, owner, scroll, true);
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(34), dp(42)));

            container.addView(row);
        }
        if (scrollToBottom) {
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }

    private TextView classTimePill(String text, View.OnClickListener listener) {
        TextView pill = new TextView(this);
        pill.setText(text);
        pill.setTextColor(inkColor());
        pill.setTextSize(15);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(roundedBg(groupColorHex(), DesignTokens.RADIUS_CHIP));
        pill.setOnClickListener(listener);
        return pill;
    }

    private TextView compactDialogAction(String text, View.OnClickListener listener) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setGravity(Gravity.CENTER);
        item.setTextSize(14);
        item.setTypeface(Typeface.DEFAULT_BOLD);
        item.setTextColor(inkColor());
        item.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CHIP));
        item.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.setMargins(dp(3), dp(8), dp(3), dp(4));
        item.setLayoutParams(params);
        return item;
    }

    List<int[]> parseClassTimeText(String text) {
        Map<Integer, int[]> bySection = new LinkedHashMap<>();
        int sequential = 1;
        if (text != null) {
            String[] lines = text.split("\\r?\\n");
            for (String line : lines) {
                Matcher matcher = CLASS_TIME_LINE_PATTERN.matcher(line.trim());
                if (!matcher.matches()) {
                    continue;
                }
                int startHour = Integer.parseInt(matcher.group(2));
                int startMinute = Integer.parseInt(matcher.group(3));
                int endHour = Integer.parseInt(matcher.group(4));
                int endMinute = Integer.parseInt(matcher.group(5));
                if (startHour > 23 || endHour > 23 || startMinute > 59 || endMinute > 59) {
                    continue;
                }
                int start = startHour * 60 + startMinute;
                int end = endHour * 60 + endMinute;
                if (end <= start) {
                    continue;
                }
                int section = matcher.group(1) == null
                        ? sequential++ : Integer.parseInt(matcher.group(1));
                if (section < 1 || section > 40) {
                    continue;
                }
                bySection.put(section, new int[]{start, end});
            }
        }
        if (bySection.isEmpty()) {
            return new ArrayList<>();
        }
        int count = Collections.max(bySection.keySet());
        List<int[]> rows = new ArrayList<>();
        for (int section = 1; section <= count; section++) {
            int[] row = bySection.get(section);
            if (row == null) {
                int[] previous = rows.get(rows.size() - 1);
                int duration = Math.max(20, previous[1] - previous[0]);
                rows.add(new int[]{previous[1] + 10, previous[1] + 10 + duration});
            } else {
                rows.add(new int[]{row[0], row[1]});
            }
        }
        return rows;
    }

    private List<int[]> loadClassTimeRows() {
        List<int[]> rows = new ArrayList<>();
        Map<Integer, int[]> anchors = CourseTimeResolver.parseSectionAnchors(classTimeConfig);
        CourseTimeResolver.Settings settings = courseTimeSettings();
        int count = Math.max(1, Math.min(20, courseSectionCount));
        for (int section = 1; section <= count; section++) {
            int[] anchored = anchors.get(section);
            if (anchored != null) {
                rows.add(new int[]{anchored[0], anchored[1]});
                continue;
            }
            CourseTimeResolver.TimeRange range =
                    CourseTimeResolver.sectionTimeRange(settings, section);
            if (range == null) {
                continue;
            }
            rows.add(new int[]{range.startMinutes, range.endMinutes});
        }
        if (rows.isEmpty()) {
            rows.add(new int[]{8 * 60, 8 * 60 + 50});
        }
        return rows;
    }

    private List<int[]> rowsFromSchoolModel(SchoolParserModel model) {
        List<int[]> rows = new ArrayList<>();
        if (model != null) {
            Map<Integer, int[]> anchors =
                    CourseTimeResolver.parseSectionAnchors(model.defaultClassTimeConfig);
            int count = model.defaultSectionCount();
            for (int section = 1; section <= count; section++) {
                int[] anchored = anchors.get(section);
                rows.add(anchored == null
                        ? new int[]{8 * 60 + (section - 1) * 60, 8 * 60 + (section - 1) * 60 + 50}
                        : new int[]{anchored[0], anchored[1]});
            }
        }
        if (rows.isEmpty()) {
            rows.add(new int[]{8 * 60, 8 * 60 + 50});
        }
        return rows;
    }

    private int[] nextClassTimeRow(List<int[]> rows) {
        int[] last = rows.get(rows.size() - 1);
        int duration = Math.max(20, last[1] - last[0]);
        int start = last[1] + 10;
        return new int[]{start, start + duration};
    }

    private boolean validateClassTimeRows(List<int[]> rows) {
        if (rows.isEmpty()) {
            Toast.makeText(this, getString(R.string.classtime_error_min_one), Toast.LENGTH_SHORT).show();
            return false;
        }
        boolean overlap = false;
        for (int i = 0; i < rows.size(); i++) {
            int[] row = rows.get(i);
            if (row[1] <= row[0]) {
                Toast.makeText(this, getString(R.string.classtime_error_end_before_start, i + 1),
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            if (i > 0 && row[0] < rows.get(i - 1)[1]) {
                overlap = true;
            }
        }
        if (overlap) {
            Toast.makeText(this, getString(R.string.classtime_warn_overlap_saved),
                    Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private void applyClassTimeRows(List<int[]> rows) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                text.append("\n");
            }
            text.append(i + 1).append(" ")
                    .append(timeTextMinute(rows.get(i)[0])).append("-")
                    .append(timeTextMinute(rows.get(i)[1]));
        }
        classTimeConfig = text.toString();
        firstClassStartTime = timeTextMinute(rows.get(0)[0]);
        classDurationMinutes = Math.max(20, Math.min(120, rows.get(0)[1] - rows.get(0)[0]));
        courseSectionCount = rows.size();
    }

    private String classTimeTableSummary() {
        List<int[]> rows = loadClassTimeRows();
        if (rows.isEmpty()) {
            return getString(R.string.classtime_summary_unset);
        }
        return getString(R.string.classtime_summary_value, rows.size(),
                timeTextMinute(rows.get(0)[0]) + "–" + timeTextMinute(rows.get(0)[1]));
    }

    private boolean hasClassTimeTable(String value) {
        return value != null && !CourseTimeResolver.parseSectionAnchors(value).isEmpty();
    }

    private int minutesFromTimeText(String value) {
        int[] time = timeFromText(value);
        return time[0] * 60 + time[1];
    }

    private String timeTextMinute(int minutes) {
        int bounded = Math.max(0, Math.min(24 * 60 - 1, minutes));
        return twoDigits(bounded / 60) + ":" + twoDigits(bounded % 60);
    }

    void applySchoolTimeDefaults(SchoolParserModel model) {
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
            Toast.makeText(this, getString(R.string.image_read_failed), Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, getString(R.string.profile_preview_image_failed), Toast.LENGTH_LONG).show();
            return;
        } catch (OutOfMemoryError error) {
            Log.e(TAG, "Insufficient memory for background preview", error);
            Toast.makeText(this, getString(R.string.profile_background_too_large), Toast.LENGTH_LONG).show();
            return;
        }
        if (previewBitmap == null) {
            Log.w(TAG, "Background preview decoder returned no drawable");
            Toast.makeText(this, getString(R.string.profile_preview_image_failed), Toast.LENGTH_LONG).show();
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(getString(R.string.profile_crop_area_title));

        TextView instruction = new TextView(this);
        instruction.setText(getString(R.string.profile_crop_instruction));
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
        cropView.setBackground(roundedBg("#101827", DesignTokens.RADIUS_LARGE));
        cropView.setClipToOutline(true);
        int previewHeight = Math.min(dp(420), Math.max(dp(200),
                getResources().getDisplayMetrics().heightPixels - dp(260)));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, previewHeight);
        imageParams.setMargins(0, 0, 0, dp(12));
        panel.addView(cropView, imageParams);

        Button use = new Button(this);
        use.setText(getString(R.string.profile_apply_area));
        use.setContentDescription(getString(R.string.profile_apply_area_cd));
        use.setTextColor(Color.WHITE);
        use.setTypeface(Typeface.DEFAULT_BOLD);
        use.setBackground(roundedBg(primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        use.setOnClickListener(v -> {
            BackgroundImageCrop selection = cropView.getCropSelection();
            dialog.dismiss();
            applyBackgroundImage(uri, selection);
        });
        panel.addView(use, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        Button chooseAgain = new Button(this);
        chooseAgain.setText(getString(R.string.profile_choose_again));
        chooseAgain.setTextColor(inkColor());
        chooseAgain.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CARD));
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
        dialog.setContentView(glassDialogContent(dialogScroll, panel, DesignTokens.RADIUS_DIALOG_SHEET));
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
        LinearLayout panel = dialogPanel(getString(R.string.profile_editor_title));

        accountAvatarPreview = new CircleAvatarView(this);
        accountAvatarPreview.setProfile(accountName, draftAvatarImageUri, draftAvatarImageCrop);
        accountAvatarPreview.setPlaceholderColor(
                isDarkModeActive() ? color("#31527D") : color("#172033"));
        accountAvatarPreview.setContentDescription(getString(R.string.profile_choose_avatar_cd));
        accountAvatarPreview.setClickable(true);
        accountAvatarPreview.setFocusable(true);
        accountAvatarPreview.setOnClickListener(v -> openAvatarPicker());
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        avatarParams.bottomMargin = dp(8);
        panel.addView(accountAvatarPreview, avatarParams);

        TextView avatarHint = new TextView(this);
        avatarHint.setText(getString(R.string.profile_avatar_hint));
        avatarHint.setTextColor(mutedColor());
        avatarHint.setTextSize(13);
        avatarHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.bottomMargin = dp(12);
        panel.addView(avatarHint, hintParams);

        accountNameInput = input(getString(R.string.profile_name_hint), accountName);
        accountNameInput.setSingleLine(true);
        accountNameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        panel.addView(accountNameInput);

        Button chooseAvatar = new Button(this);
        chooseAvatar.setText(getString(R.string.profile_choose_avatar));
        chooseAvatar.setTextColor(inkColor());
        chooseAvatar.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CARD));
        chooseAvatar.setOnClickListener(v -> openAvatarPicker());
        LinearLayout.LayoutParams chooseParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        chooseParams.topMargin = dp(10);
        panel.addView(chooseAvatar, chooseParams);

        panel.addView(pageSaveButton(() -> saveAccountProfile(dialog)));

        Button cancel = new Button(this);
        cancel.setText(getString(R.string.editor_action_cancel));
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
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
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
            Toast.makeText(this, getString(R.string.profile_avatar_preview_failed), Toast.LENGTH_LONG).show();
            return;
        } catch (OutOfMemoryError error) {
            Log.e(TAG, "Insufficient memory for avatar preview", error);
            Toast.makeText(this, getString(R.string.profile_avatar_too_large), Toast.LENGTH_LONG).show();
            return;
        }
        if (previewBitmap == null) {
            Toast.makeText(this, getString(R.string.profile_avatar_preview_failed), Toast.LENGTH_LONG).show();
            return;
        }

        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(getString(R.string.avatar_crop_title));

        TextView instruction = new TextView(this);
        instruction.setText(getString(R.string.avatar_crop_instruction));
        instruction.setTextColor(mutedColor());
        instruction.setTextSize(14);
        instruction.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        instructionParams.bottomMargin = dp(10);
        panel.addView(instruction, instructionParams);

        BackgroundCropView cropView = new BackgroundCropView(this, previewBitmap, 1f, true);
        cropView.setContentDescription(getString(R.string.avatar_crop_cd));
        cropView.setBackground(roundedBg("#101827", DesignTokens.RADIUS_LARGE));
        cropView.setClipToOutline(true);
        LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(310));
        cropParams.bottomMargin = dp(12);
        panel.addView(cropView, cropParams);

        Button use = new Button(this);
        use.setText(getString(R.string.avatar_use));
        use.setTextColor(Color.WHITE);
        use.setTypeface(Typeface.DEFAULT_BOLD);
        use.setBackground(roundedBg(primaryActionFillHex(), DesignTokens.RADIUS_CARD));
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
        cancel.setText(getString(R.string.editor_action_cancel));
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
        dialog.setContentView(glassDialogContent(dialogScroll, panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    private void saveAccountProfile(Dialog dialog) {
        String nextName = accountNameInput == null
                ? accountName : accountNameInput.getText().toString().trim();
        if (nextName.length() == 0) {
            Toast.makeText(this, getString(R.string.profile_name_empty), Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show();
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
                view.setBackground(roundedBg(pressColorHex(), DesignTokens.RADIUS_SMALL));
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
        action.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CARD));
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

    void scheduleWeekSwipeHintIfNeeded() {
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
        bar.setContentDescription(getString(R.string.save_undo_cd));
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
        undo.setContentDescription(getString(R.string.save_undo_button_cd));
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
        bar.announceForAccessibility(getString(R.string.save_undo_announce));
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
        panel.addView(popupMenuAction(getString(R.string.action_import_pdf), v -> {
            popup.dismiss();
            openPdfPicker();
        }));
        panel.addView(popupMenuAction(getString(R.string.action_import_ai), v -> {
            popup.dismiss();
            showAiImportDialog();
        }));
        panel.addView(popupMenuAction(getString(R.string.action_import_file), v -> {
            popup.dismiss();
            openSharedScheduleFilePicker();
        }));
        panel.addView(popupMenuAction(getString(R.string.action_add_course), v -> {
            popup.dismiss();
            showCourseEditor(new Course(0, 1, 2, "", defaultWeeks(), "", "", ""));
        }));
        panel.addView(popupMenuAction(getString(R.string.share_chooser_file), v -> {
            popup.dismiss();
            shareScheduleFile();
        }));
        panel.addView(popupMenuAction(getString(R.string.export_dialog_title), v -> {
            popup.dismiss();
            scheduleDialogs.showWeekExportDialog();
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

    private void openSharedScheduleFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(ScheduleShareFile.MIME_TYPE);
        try {
            startActivityForResult(intent, PICK_SHARED_SCHEDULE_FILE);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, getString(R.string.import_no_file_picker), Toast.LENGTH_LONG).show();
        }
    }

    private void shareScheduleFile() {
        if (courses.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_no_courses), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String link = ScheduleShareCodec.encodeLink(courses);
            File directory = new File(getCacheDir(), ExportFileProvider.EXPORT_DIRECTORY);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException(getString(R.string.share_dir_failed));
            }
            File file = new File(directory, "Polaris课表" + ScheduleShareFile.EXTENSION);
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(ScheduleShareFile.encode(link));
            }
            Uri uri = ExportFileProvider.uriForFile(this, file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(ScheduleShareFile.MIME_TYPE);
            share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_file_subject, scheduleName));
            share.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_file_text));
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri(getString(R.string.share_clip_label), uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.share_chooser_file)));
        } catch (Exception exception) {
            Toast.makeText(this, getString(R.string.share_file_failed, exception.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void exportScheduleBackup() {
        try {
            ScheduleBackupManager.BackupBundle bundle =
                    ScheduleBackupManager.capture(scheduleRepository, appVersionName());
            byte[] bytes = ScheduleBackupManager.encode(bundle);
            File directory = new File(getCacheDir(), ExportFileProvider.EXPORT_DIRECTORY);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException(getString(R.string.backup_dir_failed));
            }
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT)
                    .format(new Date());
            File file = new File(directory,
                    "Polaris备份-" + stamp + ScheduleBackupManager.EXTENSION);
            try (FileOutputStream output = new FileOutputStream(file, false)) {
                output.write(bytes);
            }
            Uri uri = ExportFileProvider.uriForFile(this, file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(ScheduleBackupManager.MIME_TYPE);
            share.putExtra(Intent.EXTRA_SUBJECT,
                    getString(R.string.backup_share_subject, scheduleName));
            share.putExtra(Intent.EXTRA_TEXT,
                    getString(R.string.backup_share_text));
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri(getString(R.string.backup_clip_label), uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.backup_chooser_title)));
        } catch (Exception exception) {
            Toast.makeText(this, getString(R.string.backup_share_failed, exception.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openBackupFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, PICK_BACKUP_FILE);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, getString(R.string.backup_picker_missing), Toast.LENGTH_LONG).show();
        }
    }

    private void startBackupRestoreFlow(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            ScheduleBackupManager.BackupBundle bundle =
                    ScheduleBackupManager.read(input);
            scheduleDialogs.showBackupRestoreConfirmDialog(bundle);
        } catch (Exception exception) {
            String reason = exception.getMessage() == null
                    ? getString(R.string.backup_read_unknown) : exception.getMessage();
            Toast.makeText(this, getString(R.string.backup_read_failed, reason),
                    Toast.LENGTH_LONG).show();
        }
    }


    void applyBackupRestore(ScheduleBackupManager.BackupBundle bundle) {
        int restoredCourseCount;
        try {
            ScheduleBackupManager.restoreTo(scheduleRepository, bundle);
            restoredCourseCount = 0;
            for (ScheduleBackupManager.ScheduleBackup backup : bundle.schedules) {
                restoredCourseCount += backup.structuredCourses.size();
            }
        } catch (Exception exception) {
            Toast.makeText(this, getString(R.string.backup_restore_failed, exception.getMessage()),
                    Toast.LENGTH_LONG).show();
            return;
        }
        activeScheduleId = scheduleRepository.activeScheduleId();
        applyAccountProfile(scheduleRepository.loadAccountProfile());
        darkMode = scheduleRepository.loadGlobalDarkMode();
        applyConfig(scheduleRepository.loadConfig(activeScheduleId));
        currentWeek = currentWeekFromDate();
        loadActiveCourses();
        applyShellAppearance();
        rescheduleCourseReminders();
        updateHeader();
        renderSchedule();
        updateEmptyScheduleView();
        refreshCourseManageList();
        refreshMyPage();
        switchTab(0);
        Toast.makeText(this, getString(R.string.backup_restored_toast, bundle.schedules.size(),
                restoredCourseCount), Toast.LENGTH_LONG).show();
    }

    private String appVersionName() {
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            return versionName == null ? "" : versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            return "";
        }
    }


    void exportWeekImage(int week) {
        if (scheduleExportInProgress) {
            Toast.makeText(this, getString(R.string.export_busy), Toast.LENGTH_SHORT).show();
            return;
        }
        ScheduleImageExporter.Request request = scheduleExportRequest(week);
        if (!ScheduleImageExporter.hasExportableContent(request)) {
            Toast.makeText(this, getString(R.string.export_week_empty),
                    Toast.LENGTH_LONG).show();
            return;
        }
        scheduleExportInProgress = true;
        Toast.makeText(this, getString(R.string.export_generating_week_image, request.week),
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
                    handleScheduleExportFailure(exception, getString(R.string.export_failed_image));
                }
            });
        } catch (RuntimeException exception) {
            scheduleExportInProgress = false;
            Toast.makeText(this, getString(R.string.export_start_failed), Toast.LENGTH_LONG).show();
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

    void exportWeekPdf(int week) {
        if (scheduleExportInProgress) {
            Toast.makeText(this, getString(R.string.export_busy), Toast.LENGTH_SHORT).show();
            return;
        }
        ScheduleImageExporter.Request request = scheduleExportRequest(week);
        if (!ScheduleImageExporter.hasExportableContent(request)) {
            Toast.makeText(this, getString(R.string.export_week_empty),
                    Toast.LENGTH_LONG).show();
            return;
        }
        scheduleExportInProgress = true;
        Toast.makeText(this, getString(R.string.export_generating_week_pdf, request.week),
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
                    handleScheduleExportFailure(exception, getString(R.string.export_failed_pdf));
                }
            });
        } catch (RuntimeException exception) {
            scheduleExportInProgress = false;
            Toast.makeText(this, getString(R.string.export_start_failed), Toast.LENGTH_LONG).show();
        }
    }

    void exportSemesterPdf() {
        if (scheduleExportInProgress) {
            Toast.makeText(this, getString(R.string.export_busy), Toast.LENGTH_SHORT).show();
            return;
        }
        if (courses.isEmpty()) {
            Toast.makeText(this, getString(R.string.export_semester_empty),
                    Toast.LENGTH_LONG).show();
            return;
        }
        ScheduleImageExporter.Request request = scheduleExportRequest(currentWeek);
        scheduleExportInProgress = true;
        Toast.makeText(this, getString(R.string.export_generating_semester), Toast.LENGTH_SHORT).show();
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
                    handleScheduleExportFailure(exception, getString(R.string.export_failed_semester));
                }
            });
        } catch (RuntimeException exception) {
            scheduleExportInProgress = false;
            Toast.makeText(this, getString(R.string.export_start_failed), Toast.LENGTH_LONG).show();
        }
    }

    private void handleScheduleExportFailure(Exception exception, String prefix) {
        String reason = exception.getMessage() == null
                ? getString(R.string.import_reason_unknown) : exception.getMessage();
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
            share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_week_subject, scheduleName, week));
            share.putExtra(Intent.EXTRA_TEXT, getString(R.string.export_share_week_text, week));
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri(getString(R.string.export_clip_week_image), uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.export_share_week_image_chooser)));
        } catch (RuntimeException exception) {
            Toast.makeText(this, getString(R.string.export_share_panel_failed_image),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareExportedSchedulePdf(File pdf, int week) {
        try {
            Uri uri = ExportFileProvider.uriForFile(this, pdf);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_week_subject, scheduleName, week));
            share.putExtra(Intent.EXTRA_TEXT, getString(R.string.export_share_week_text, week));
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri(getString(R.string.export_clip_week_pdf), uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.export_share_week_pdf_chooser)));
        } catch (RuntimeException exception) {
            Toast.makeText(this, getString(R.string.export_share_panel_failed_pdf),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shareExportedSemesterPdf(File pdf) {
        try {
            Uri uri = ExportFileProvider.uriForFile(this, pdf);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject_semester, scheduleName));
            share.putExtra(Intent.EXTRA_TEXT, getString(R.string.export_share_text_semester));
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri(getString(R.string.export_clip_semester_pdf), uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.export_share_semester_chooser)));
        } catch (RuntimeException exception) {
            Toast.makeText(this, getString(R.string.export_share_panel_failed_semester),
                    Toast.LENGTH_LONG).show();
        }
    }

    void exportICal() {
        if (structuredCourses.isEmpty()) {
            Toast.makeText(this, getString(R.string.export_semester_empty),
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ScheduleCalendarExporter.ExportContext context = calendarExportContext();
            String content = ScheduleCalendarExporter.buildICal(context, structuredCourses);
            shareExportedCalendarFile(
                    writeCalendarExport(content.getBytes("UTF-8"),
                            ScheduleCalendarExporter.safeFileName(scheduleName) + getString(R.string.export_ics_suffix)),
                    "text/calendar", getString(R.string.export_ics_label));
        } catch (Exception exception) {
            Toast.makeText(this, getString(R.string.export_ics_failed, exception.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    void exportCsv() {
        if (structuredCourses.isEmpty()) {
            Toast.makeText(this, getString(R.string.export_semester_empty),
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            ScheduleCalendarExporter.ExportContext context = calendarExportContext();
            String content = ScheduleCalendarExporter.buildCsv(context, structuredCourses);
            shareExportedCalendarFile(
                    writeCalendarExport(content.getBytes("UTF-8"),
                            ScheduleCalendarExporter.safeFileName(scheduleName) + ".csv"),
                    "text/csv", "CSV");
        } catch (Exception exception) {
            Toast.makeText(this, getString(R.string.export_csv_failed, exception.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private ScheduleCalendarExporter.ExportContext calendarExportContext() {
        return new ScheduleCalendarExporter.ExportContext(
                scheduleName,
                semesterName,
                firstWeekDay,
                semesterWeeks,
                courseTimeSettings());
    }

    private File writeCalendarExport(byte[] bytes, String fileName) throws Exception {
        File directory = new File(getCacheDir(), ExportFileProvider.EXPORT_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException(getString(R.string.export_dir_failed));
        }
        File file = new File(directory, fileName);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
        }
        return file;
    }

    private void shareExportedCalendarFile(File file, String mimeType, String label) {
        try {
            Uri uri = ExportFileProvider.uriForFile(this, file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(mimeType);
            share.putExtra(Intent.EXTRA_SUBJECT, scheduleName + " · " + label);
            share.putExtra(Intent.EXTRA_TEXT,
                    getString(R.string.calendar_share_subject, label, file.getName()));
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(ClipData.newRawUri(getString(R.string.calendar_clip_label, label), uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.calendar_share_chooser, label)));
        } catch (RuntimeException exception) {
            Toast.makeText(this, getString(R.string.calendar_share_panel_failed, label),
                    Toast.LENGTH_LONG).show();
        }
    }

    private boolean isScheduleShareFile(Uri uri, String mimeType) {
        if (uri == null) {
            return false;
        }
        if (ScheduleShareFile.MIME_TYPE.equals(mimeType)) {
            return true;
        }
        String name = uri.getLastPathSegment();
        return name != null && name.toLowerCase(java.util.Locale.ROOT)
                .endsWith(ScheduleShareFile.EXTENSION);
    }

    private void startSharedScheduleFileImportFlow(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            String link = ScheduleShareFile.read(input);
            Uri shareUri = Uri.parse(link);
            if (!ScheduleShareCodec.isImportLink(shareUri)) {
                throw new IllegalArgumentException(getString(R.string.shared_file_invalid));
            }
            startSharedScheduleImportFlow(shareUri);
        } catch (Exception exception) {
            String reason = exception.getMessage() == null
                    ? getString(R.string.shared_file_read_failed) : exception.getMessage();
            Toast.makeText(this, getString(R.string.shared_file_import_failed, reason),
                    Toast.LENGTH_LONG).show();
        }
    }


    String extractSharedScheduleLink(String text) {
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

    void startSharedScheduleImportFlow(Uri uri) {
        try {
            List<Course> sharedCourses = ScheduleShareCodec.decodeLink(uri);
            if (sharedCourses.isEmpty()) {
                Toast.makeText(this, getString(R.string.shared_link_no_courses), Toast.LENGTH_LONG).show();
                return;
            }
            if (courses.isEmpty()) {
                scheduleDialogs.showSharedImportNameDialog(sharedCourses);
                return;
            }
            scheduleDialogs.showSharedImportOverwriteDialog(sharedCourses);
        } catch (Exception exception) {
            Toast.makeText(this, getString(R.string.shared_link_invalid_toast, exception.getMessage()), Toast.LENGTH_LONG).show();
        }
    }


    void applySharedCourses(List<Course> sharedCourses, String targetScheduleName) {
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
        Toast.makeText(this, getString(R.string.shared_import_done, courses.size()), Toast.LENGTH_LONG).show();
        scheduleDialogs.showImportedFirstWeekDayDialog();
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
        Toast.makeText(this, getString(R.string.days_visible_toast, visibleDayCount), Toast.LENGTH_SHORT).show();
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
                                    getString(R.string.editor_course_changed), Toast.LENGTH_SHORT).show();
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
                                    getString(R.string.delete_no_this_week), Toast.LENGTH_SHORT).show();
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
            return getString(R.string.delete_done_week);
        }
        if (scope == CourseDeletionScope.CURRENT_MEETING) {
            return getString(R.string.delete_done_meeting);
        }
        return getString(R.string.delete_done_all);
    }

    private void showPracticeCourses(List<Course> practiceCourses) {
        if (practiceCourses == null || practiceCourses.isEmpty()) {
            return;
        }
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(getString(R.string.board_practice_title));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(practiceCourses.size() > 5);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        for (Course course : practiceCourses) {
            String time = course.isBannerOnlyCourse()
                    ? getString(R.string.board_practice_concentrated) : courseTimeInlineText(course);
            String name = course.name == null || course.name.trim().isEmpty()
                    ? getString(R.string.board_practice_unnamed) : course.name.trim();
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
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    // ===== 学习计划 =====

    private void reloadStudyPlans() {
        studyPlans.clear();
        studyPlans.addAll(planRepository.loadPlans(activeScheduleId));
    }

    private void persistPlans() {
        planRepository.savePlans(activeScheduleId, studyPlans);
    }

    private int indexOfPlan(String id) {
        for (int i = 0; i < studyPlans.size(); i++) {
            if (studyPlans.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private int planIdCounter;

    private String newPlanId() {
        planIdCounter++;
        return "plan-" + System.currentTimeMillis() + "-" + planIdCounter;
    }

    private String remindTimeText(int minute) {
        return twoDigits(minute / 60) + ":" + twoDigits(minute % 60);
    }

    /**
     * 计划页入口：平板横屏从右侧计划面板「管理」进入 → 右侧手机宽管理浮层；
     * 手机/竖屏平板直接切到底部导航「计划」tab。
     */
    private void showPlanPage() {
        if (isLandscapeTablet()) {
            showPlanManagePanel();
        } else {
            switchTab(1);
            refreshPlanList();
        }
    }

    /** 平板横屏：全屏遮罩 + 右侧手机宽度计划管理浮层（内容与手机计划页一致）。 */
    private void buildPlanManageOverlay() {
        planManageOverlay = new FrameLayout(this);
        planManageOverlay.setBackgroundColor(Color.argb(130, 0, 0, 0));
        planManageOverlay.setVisibility(View.GONE);
        planManageOverlay.setOnClickListener(v -> closePlanManagePanel());
        rootView.addView(planManageOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int panelWidth = Math.min(dp(380),
                getResources().getDisplayMetrics().widthPixels - dp(24));
        planManagePanel = new LinearLayout(this);
        planManagePanel.setOrientation(LinearLayout.VERTICAL);
        planManagePanel.setBackgroundColor(pageSurfaceColor());
        planManagePanel.setOnClickListener(v -> {
            // 消费点击，阻止遮罩关闭。
        });
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        planManageOverlay.addView(planManagePanel, panelParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, statusBarHeight() + dp(8), 0, dp(8));
        header.setBackgroundColor(settingsHeaderSurfaceColor());
        planManageHeader = header;
        TextView close = new TextView(this);
        close.setText("×");
        close.setTextColor(inkColor());
        close.setTextSize(30);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(getString(R.string.plan_overlay_close_cd));
        close.setOnClickListener(v -> closePlanManagePanel());
        header.addView(close, new LinearLayout.LayoutParams(dp(52), dp(54)));
        TextView title = new TextView(this);
        title.setText(getString(R.string.plan_title));
        title.setTextColor(inkColor());
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        planManagePanel.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), bottomContentInset() + dp(48));
        scroll.addView(content);
        planManagePanel.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView addButton = new TextView(this);
        addButton.setText(getString(R.string.plan_action_new));
        addButton.setTextColor(Color.WHITE);
        addButton.setTextSize(15);
        addButton.setTypeface(Typeface.DEFAULT_BOLD);
        addButton.setGravity(Gravity.CENTER);
        addButton.setPadding(dp(14), 0, dp(14), 0);
        addButton.setMinHeight(dp(44));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive()));
        addBg.setCornerRadius(dp(18));
        addButton.setBackground(addBg);
        addButton.setOnClickListener(v -> showPlanEditor(null));
        attachPressFeedback(addButton);
        planAddButton = addButton;
        content.addView(addButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        planListContainer = new LinearLayout(this);
        planListContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(planListContainer);
    }

    private void showPlanManagePanel() {
        if (planManageOverlay == null) {
            return;
        }
        planManageOverlay.setAlpha(0f);
        planManagePanel.setTranslationX(dp(380));
        planManageOverlay.setVisibility(View.VISIBLE);
        planManagePanel.setVisibility(View.VISIBLE);
        refreshPlanList();
        planManageOverlay.animate().alpha(1f).setDuration(200).start();
        planManagePanel.animate().translationX(0f).setDuration(200).start();
    }

    private void closePlanManagePanel() {
        if (planManageOverlay == null || planManageOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        planManageOverlay.animate().alpha(0f).setDuration(180).start();
        planManagePanel.animate().translationX(dp(380)).setDuration(180)
                .withEndAction(() -> planManageOverlay.setVisibility(View.GONE)).start();
    }

    /** 手机/竖屏平板：底部导航「计划」tab 的独立页面。 */
    private ScrollView buildPlanPage() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(pageSurfaceColor());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, statusBarHeight() + dp(34), 0, bottomContentInset() + dp(48));
        if (!isLandscapeTablet()) {
            int columnWidth = contentColumnWidth();
            if (columnWidth < getResources().getDisplayMetrics().widthPixels) {
                page.setLayoutParams(new ScrollView.LayoutParams(columnWidth,
                        LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            }
        }
        scrollView.addView(page);

        TextView addButton = new TextView(this);
        addButton.setText(getString(R.string.plan_action_new));
        addButton.setTextColor(Color.WHITE);
        addButton.setTextSize(15);
        addButton.setTypeface(Typeface.DEFAULT_BOLD);
        addButton.setGravity(Gravity.CENTER);
        addButton.setPadding(dp(14), 0, dp(14), 0);
        addButton.setMinHeight(dp(44));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive()));
        addBg.setCornerRadius(dp(18));
        addButton.setBackground(addBg);
        addButton.setOnClickListener(v -> showPlanEditor(null));
        attachPressFeedback(addButton);
        planAddButton = addButton;
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        addParams.setMargins(dp(12), 0, dp(12), dp(6));
        page.addView(addButton, addParams);

        planListContainer = new LinearLayout(this);
        planListContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(planListContainer);
        return scrollView;
    }

    /** 平板横屏：右侧面板「管理」→ 全屏计划页（settingsPage 模式）。 */
    private void refreshPlanList() {
        if (planListContainer == null) {
            return;
        }
        planListContainer.removeAllViews();
        if (studyPlans.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.plan_empty_hint));
            empty.setTextColor(mutedColor());
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(42), 0, dp(42));
            planListContainer.addView(empty);
            return;
        }
        List<StudyPlan> pending = new ArrayList<>();
        List<StudyPlan> finished = new ArrayList<>();
        for (StudyPlan plan : studyPlans) {
            (plan.done ? finished : pending).add(plan);
        }
        Collections.sort(pending, (a, b) -> weekDayValue(a) - weekDayValue(b));
        Collections.sort(finished, (a, b) -> weekDayValue(b) - weekDayValue(a));
        if (!pending.isEmpty()) {
            planListContainer.addView(sectionHeader(getString(R.string.plan_section_pending)));
            LinearLayout group = settingsGroup();
            for (StudyPlan plan : pending) {
                group.addView(planRow(plan));
            }
            planListContainer.addView(group);
        }
        if (!finished.isEmpty()) {
            planListContainer.addView(sectionHeader(getString(R.string.plan_section_done)));
            LinearLayout group = settingsGroup();
            for (StudyPlan plan : finished) {
                group.addView(planRow(plan));
            }
            planListContainer.addView(group);
        }
    }

    /**
     * 主题/深色模式切换后刷新计划相关界面的固有色：
     * 计划页与浮层背景、浮层头部、新建按钮底色、右侧玻璃面板底色，并重建列表行。
     * 与 refreshMyPageBehindSettings 同一刷新链，保证计划界面与主题同步。
     */
    private void refreshPlanTheme() {
        if (planPage != null) {
            planPage.setBackgroundColor(pageSurfaceColor());
        }
        if (planManagePanel != null) {
            planManagePanel.setBackgroundColor(pageSurfaceColor());
        }
        if (planManageHeader != null) {
            planManageHeader.setBackgroundColor(settingsHeaderSurfaceColor());
        }
        if (planAddButton != null) {
            GradientDrawable addBg = new GradientDrawable();
            addBg.setColor(PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive()));
            addBg.setCornerRadius(dp(18));
            planAddButton.setBackground(addBg);
        }
        if (planSidePanel != null) {
            updateGlassLayer(planSidePanel, floatingPanelBg(bottomNavOpacity, 20), 20);
        }
        if (practiceSidePanel != null) {
            updateGlassLayer(practiceSidePanel, floatingPanelBg(bottomNavOpacity, 20), 20);
        }
        if (todayOverviewPanel != null) {
            updateGlassLayer(todayOverviewPanel, floatingPanelBg(bottomNavOpacity, 20), 20);
        }
        refreshPlanList();
    }

    private int weekDayValue(StudyPlan plan) {
        return plan.week * 7 + plan.dayOfWeek;
    }

    private View planRow(StudyPlan plan) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_MEDIUM));
        row.setOnClickListener(v -> showPlanEditor(plan));
        attachCardPressFeedback(row, 16);

        TextView check = new TextView(this);
        check.setText(plan.done ? "☑" : "☐");
        check.setTextSize(24);
        check.setGravity(Gravity.CENTER);
        check.setTextColor(plan.done
                ? PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive()) : mutedColor());
        check.setContentDescription(plan.done ? getString(R.string.plan_cd_mark_undone) : getString(R.string.plan_cd_mark_done));
        check.setOnClickListener(v -> togglePlanDone(plan));
        attachPressFeedback(check);
        row.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText(plan.title.length() == 0 ? getString(R.string.plan_unnamed) : plan.title);
        title.setTextColor(inkColor());
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (plan.done) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            title.setAlpha(0.55f);
        }
        content.addView(title);

        StringBuilder meta = new StringBuilder(getString(R.string.plan_meta_week, plan.week))
                .append(dayText(plan.dayOfWeek));
        if (plan.hasCourse()) {
            meta.append(" · ").append(plan.courseName);
        }
        if (plan.hasReminder()) {
            meta.append(getString(R.string.plan_meta_remind, remindTimeText(plan.remindMinute)));
        }
        TextView metaView = new TextView(this);
        metaView.setText(meta.toString());
        metaView.setTextColor(mutedColor());
        metaView.setTextSize(12);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(2);
        content.addView(metaView, metaParams);
        row.addView(content, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView edit = new TextView(this);
        edit.setText("✎");
        edit.setTextSize(18);
        edit.setGravity(Gravity.CENTER);
        edit.setTextColor(mutedColor());
        edit.setContentDescription(getString(R.string.plan_edit));
        edit.setOnClickListener(v -> showPlanEditor(plan));
        attachPressFeedback(edit);
        row.addView(edit, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(params);
        return row;
    }

    private void togglePlanDone(StudyPlan plan) {
        StudyPlan updated = plan.withDone(!plan.done);
        int index = indexOfPlan(plan.id);
        if (index >= 0) {
            studyPlans.set(index, updated);
        }
        persistPlans();
        refreshPlanList();
        updatePlanSidePanel();
        PlanReminderScheduler.reschedule(this);
    }

    private void deletePlan(StudyPlan plan) {
        Iterator<StudyPlan> planIterator = studyPlans.iterator();
        while (planIterator.hasNext()) {
            if (planIterator.next().id.equals(plan.id)) {
                planIterator.remove();
            }
        }
        persistPlans();
        refreshPlanList();
        updatePlanSidePanel();
        PlanReminderScheduler.reschedule(this);
    }

    private String[] courseNameChoices() {
        List<String> names = new ArrayList<>();
        for (Course course : courses) {
            String name = course.name == null ? "" : course.name.trim();
            if (name.length() > 0 && !names.contains(name)) {
                names.add(name);
            }
        }
        names.add(0, "不关联");
        return names.toArray(new String[0]);
    }

    private void showPlanEditor(StudyPlan existing) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = dialogPanel(existing == null ? getString(R.string.plan_editor_new) : getString(R.string.plan_edit));

        final String[] titleValue = {existing == null ? "" : existing.title};
        final String[] courseValue = {existing == null ? "" : existing.courseName};
        final int[] weekValue = {existing == null ? Math.max(1, currentWeek) : existing.week};
        final int[] dayValue = {existing == null ? 0 : existing.dayOfWeek};
        final boolean[] remindEnabledValue = {
                existing == null || existing.remindEnabled};
        final int[] remindMinuteValue = {
                existing == null ? StudyPlan.REMIND_DEFAULT_MINUTE : existing.remindMinute};

        EditText titleInput = input(getString(R.string.plan_editor_title_hint), titleValue[0]);
        panel.addView(titleInput);

        final String[] choices = courseNameChoices();
        String currentCourseLabel = courseValue[0].length() == 0 ? "不关联" : courseValue[0];
        View courseRow = settingValueRow(getString(R.string.plan_row_related_course), currentCourseLabel, v ->
                appearanceDialogs.showChoiceDialog(v, getString(R.string.plan_row_related_course), choices, currentCourseLabel, value -> {
                    courseValue[0] = "不关联".equals(value) ? "" : value;
                    updateSettingValueRow(v, "不关联".equals(value) ? "不关联" : value);
                }));
        panel.addView(courseRow);

        // 周次步进
        LinearLayout weekRow = new LinearLayout(this);
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        weekRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView weekLabel = new TextView(this);
        weekLabel.setText(getString(R.string.plan_row_week));
        weekLabel.setTextColor(inkColor());
        weekLabel.setTextSize(15);
        weekLabel.setTypeface(Typeface.DEFAULT_BOLD);
        weekRow.addView(weekLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        EditText weekInput = stepInput(String.valueOf(weekValue[0]));
        weekInput.setContentDescription(getString(R.string.plan_cd_week));
        TextView minus = stepButton("−");
        TextView plus = stepButton("+");
        minus.setOnClickListener(v -> {
            weekValue[0] = Math.max(1, parseBounded(
                    weekInput.getText().toString(), 1, semesterWeeks, weekValue[0]) - 1);
            weekInput.setText(String.valueOf(weekValue[0]));
            weekInput.setSelection(weekInput.getText().length());
        });
        plus.setOnClickListener(v -> {
            weekValue[0] = Math.min(semesterWeeks, parseBounded(
                    weekInput.getText().toString(), 1, semesterWeeks, weekValue[0]) + 1);
            weekInput.setText(String.valueOf(weekValue[0]));
            weekInput.setSelection(weekInput.getText().length());
        });
        weekRow.addView(minus);
        weekRow.addView(weekInput, new LinearLayout.LayoutParams(0, dp(44), 1f));
        weekRow.addView(plus);
        LinearLayout.LayoutParams weekRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        weekRowParams.topMargin = dp(10);
        weekRow.setLayoutParams(weekRowParams);
        panel.addView(weekRow);

        // 周几选择

        LinearLayout dayRow = new LinearLayout(this);
        dayRow.setOrientation(LinearLayout.HORIZONTAL);
        dayRow.setGravity(Gravity.CENTER);
        final TextView[] dayChips = new TextView[7];
        final int accent = PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive());
        for (int i = 0; i < 7; i++) {
            final int day = i;
            TextView chip = new TextView(this);
            chip.setText(WeekdayLabels.label(this, i));
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setTypeface(day == dayValue[0] ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            chip.setTextColor(day == dayValue[0] ? Color.WHITE : mutedColor());
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setCornerRadius(dp(16));
            chipBg.setColor(day == dayValue[0] ? accent : color(cardColorHex()));
            chip.setBackground(chipBg);
            chip.setClickable(true);
            chip.setOnClickListener(v -> {
                dayValue[0] = day;
                for (int j = 0; j < 7; j++) {
                    boolean selected = j == day;
                    dayChips[j].setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    dayChips[j].setTextColor(selected ? Color.WHITE : mutedColor());
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(dp(16));
                    bg.setColor(selected ? accent : color(cardColorHex()));
                    dayChips[j].setBackground(bg);
                }
            });
            dayChips[i] = chip;
            dayRow.addView(chip, new LinearLayout.LayoutParams(0, dp(36), 1f));
        }
        LinearLayout.LayoutParams dayRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        dayRowParams.topMargin = dp(10);
        dayRow.setLayoutParams(dayRowParams);
        panel.addView(dayRow);

        // 到期提醒开关
        LinearLayout remindRow = new LinearLayout(this);
        remindRow.setOrientation(LinearLayout.HORIZONTAL);
        remindRow.setGravity(Gravity.CENTER_VERTICAL);
        remindRow.setPadding(dp(2), dp(10), dp(2), dp(4));
        TextView remindLabel = new TextView(this);
        remindLabel.setText(getString(R.string.plan_row_reminder));
        remindLabel.setTextColor(inkColor());
        remindLabel.setTextSize(15);
        remindLabel.setTypeface(Typeface.DEFAULT_BOLD);
        remindRow.addView(remindLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final SwitchThumbView[] remindSwitch = {
                new SwitchThumbView(this, remindEnabledValue[0], isDarkModeActive())};
        remindSwitch[0].setOnClickListener(v -> {
            remindEnabledValue[0] = !remindEnabledValue[0];
            remindSwitch[0].setChecked(remindEnabledValue[0]);
        });
        remindRow.addView(remindSwitch[0], new LinearLayout.LayoutParams(dp(52), dp(30)));
        panel.addView(remindRow);

        // 提醒时间
        View timeRow = settingValueRow(getString(R.string.plan_row_remind_time),
                remindTimeText(remindMinuteValue[0]), v ->
                        appearanceDialogs.showTimeDialog(getString(R.string.plan_row_remind_time), remindTimeText(remindMinuteValue[0]), value -> {
                            int[] time = timeFromText(value);
                            remindMinuteValue[0] = time[0] * 60 + time[1];
                            updateSettingValueRow(v, remindTimeText(remindMinuteValue[0]));
                        }));
        panel.addView(timeRow);

        panel.addView(dialogAction(getString(R.string.editor_action_save), v -> {
            String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
            if (title.length() == 0) {
                Toast.makeText(this, getString(R.string.plan_error_title_required), Toast.LENGTH_SHORT).show();
                return;
            }
            savePlan(existing, title, courseValue[0], weekValue[0], dayValue[0],
                    remindEnabledValue[0], remindMinuteValue[0]);
            dialog.dismiss();
        }));
        if (existing != null) {
            panel.addView(dialogAction(getString(R.string.plan_action_delete), v -> {
                deletePlan(existing);
                dialog.dismiss();
            }));
        }
        panel.addView(dialogAction(getString(R.string.editor_action_cancel), v -> dialog.dismiss()));
        dialog.setContentView(glassDialogContent(panel, DesignTokens.RADIUS_DIALOG_SHEET));
        dialog.show();
        transparentDialog(dialog);
    }

    private void savePlan(StudyPlan existing, String title, String courseName,
                          int week, int dayOfWeek, boolean remindEnabled, int remindMinute) {
        String id = existing == null ? newPlanId() : existing.id;
        long createdAt = existing == null ? System.currentTimeMillis() : existing.createdAt;
        boolean done = existing != null && existing.done;
        StudyPlan plan = new StudyPlan(id, title, courseName, week, dayOfWeek,
                done, remindEnabled, remindMinute, createdAt);
        if (existing == null) {
            studyPlans.add(plan);
        } else {
            int index = indexOfPlan(existing.id);
            if (index >= 0) {
                studyPlans.set(index, plan);
            }
        }
        persistPlans();
        refreshPlanList();
        updatePlanSidePanel();
        if (remindEnabled) {
            int scheduled = PlanReminderScheduler.reschedule(this);
            if (scheduled == 0) {
                if (!PlanReminderScheduler.hasPermission(this)) {
                    Toast.makeText(this, getString(R.string.plan_toast_no_permission),
                            Toast.LENGTH_LONG).show();
                    openNotificationSettings();
                } else {
                    Toast.makeText(this, getString(R.string.plan_toast_remind_time_passed),
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            PlanReminderScheduler.reschedule(this);
        }
    }

    EditText input(String hint, String value) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setText(value);
        editText.setTextColor(inkColor());
        editText.setHintTextColor(mutedColor());
        editText.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CHIP));
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
        view.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CHIP));
        view.setOnClickListener(v -> target.setText(value));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
        params.setMargins(dp(3), dp(8), dp(3), 0);
        view.setLayoutParams(params);
        return view;
    }


    private BottomNavView bottomNav() {
        bottomNavView = new BottomNavView(this, this);
        return bottomNavView;
    }


    /**
     * 横屏平板底部导航：矩形悬浮条样式，宽度由 bottomNavLayoutParams
     * 限制为居中限宽（不横跨全屏）。
     */

    private FrameLayout.LayoutParams bottomNavLayoutParams() {
        if (isLandscapeTablet()) {
            // 底部导航：固定宽度、水平居中，不横跨全屏。
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    dp(DesignTokens.NAV_TABLET_WIDTH), dp(navVisualHeight()),
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            params.setMargins(0, 0, 0, dp(DesignTokens.NAV_FLOATING_MARGIN) + Math.max(systemBottomInset, 0));
            return params;
        }
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        int bottomMargin = dp(DesignTokens.NAV_FLOATING_MARGIN) + Math.max(systemBottomInset, 0);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(navVisualHeight()), Gravity.BOTTOM);
        int side = dp(tablet ? DesignTokens.MARGIN_PAGE_TABLET : DesignTokens.MARGIN_PAGE_PHONE);
        params.setMargins(side + systemLeftInset, 0, side + systemRightInset, bottomMargin);
        return params;
    }

    private int navVisualHeight() {
        return Math.max(DesignTokens.NAV_HEIGHT_MIN,
                Math.min(DesignTokens.NAV_HEIGHT_MAX, bottomNavHeight));
    }

    @Override
    public int bottomContentInset() {
        return dp(navVisualHeight() + 12) + Math.max(systemBottomInset, 0);
    }

    private void handleScheduleVerticalScroll(int scrollY, int deltaY, boolean atBottom) {
        if (activeTab != 0 || settingsPage != null && settingsPage.getVisibility() == View.VISIBLE) {
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
        boolean plan = tab == 1;
        boolean mine = tab == 2;
        if (scheduleBoard != null) {
            scheduleBoard.setVisibility(schedule ? View.VISIBLE : View.GONE);
        }
        if (planPage != null) {
            planPage.setVisibility(plan ? View.VISIBLE : View.GONE);
        }
        if (myPage != null) {
            myPage.setVisibility(mine ? View.VISIBLE : View.GONE);
        }
        if (settingsPage != null) {
            settingsPage.setVisibility(View.GONE);
        }
        if (isLandscapeTablet() && tabletSettingsOpen) {
            // 侧栏模式被 tab 切换打断时，恢复我的页居中模式。
            closeTabletSplit();
        }
        updateEmptyScheduleView();
        if (topPanelContainer != null) {
            topPanelContainer.setVisibility(schedule ? View.VISIBLE : View.GONE);
        }
        if (bottomNavView != null) {
            bottomNavView.setLayoutParams(bottomNavLayoutParams());
            showBottomNav(false);
        }
        if (bottomNavView != null) {
            bottomNavView.updateTabs(schedule, plan, mine);
        }
        if (schedule) {
            updateHeader();
            scheduleWeekSwipeHintIfNeeded();
        }
        if (plan) {
            refreshPlanList();
        }
        updateTodayOverviewPanel();
        updatePracticeSidePanel();
        updatePlanSidePanel();
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
        if (bottomNavHidden) {
            bottomNavView.setTranslationY(dp(navVisualHeight() + 18));
        } else {
            bottomNavView.setTranslationY(0f);
        }
    }

    private View buildMyPage() {
        // 阶段 5-3：ViewBinding 壳层 + Builder 委托，视觉零变化，myPage 升级为 View 以承载 Fragment 壳层
        com.polaris.timetable.databinding.FragmentMyBinding binding = com.polaris.timetable.databinding.FragmentMyBinding.inflate(getLayoutInflater());
        View builderView = new MyPageBuilder(this).build(this);
        binding.myPageContainer.removeAllViews();
        binding.myPageContainer.addView(builderView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        binding.myRoot.setBackgroundColor(pageSurfaceColor());
        // 同步状态栏与底部 inset：绑定壳层的 myPageContainer 顶部由 Builder 内部处理，此处仅保证根背景
        return binding.getRoot();
    }

    private String appVersionText() {
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            return getString(R.string.my_version_value, versionName == null ? "" : versionName);
        } catch (PackageManager.NameNotFoundException exception) {
            return getString(R.string.app_name);
        }
    }

    private String displaySemesterName() {
        return semesterName.length() == 0 ? getString(R.string.settings_semester_not_set) : semesterName;
    }

    private String displaySchoolName() {
        return schoolName.length() == 0 ? getString(R.string.settings_school_not_set) : schoolName;
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
        card.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_LARGE));
        card.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.round(menuCardWidth(0.86f)),
                LinearLayout.LayoutParams.WRAP_CONTENT);
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
        if (titleText.contains("更多")) {
            return "···";
        }
        return "•";
    }

    private String navText(String label, boolean active) {
        if ("课表".equals(label)) {
            return (active ? "▣" : "▦") + "\n" + label;
        }
        if ("计划".equals(label)) {
            return "✎\n" + label;
        }
        return (active ? "●" : "○") + "\n" + label;
    }

    @Override
    public boolean navDarkMode() {
        return isDarkModeActive();
    }

    @Override
    public boolean navMinimalTheme() {
        return isMinimalVisualTheme();
    }

    @Override
    public String navVisualTheme() {
        return visualTheme;
    }

    @Override
    public boolean navBlurEnabled() {
        return shellBarsBlurEnabled;
    }

    @Override
    public int navOpacity() {
        return bottomNavOpacity;
    }

    @Override
    public int navRadius() {
        return bottomNavRadius();
    }

    @Override
    public int navHeight() {
        return navVisualHeight();
    }

    @Override
    public int navBottomInset() {
        return Math.max(systemBottomInset, 0);
    }

    @Override
    public int navInkColor() {
        return inkColor();
    }

    @Override
    public int navMutedColor() {
        return mutedColor();
    }

    @Override
    public boolean navTabActive(int tab) {
        return activeTab == tab;
    }

    @Override
    public void onNavTabSelected(int tab) {
        switchTab(tab);
    }

    @Override
    public View navContentSource() {
        return contentHost;
    }

    @Override
    public CharSequence navLabel(int tab, boolean active) {
        String label = tab == 0 ? "课表" : tab == 1 ? "计划" : "我的";
        return styledNavText(navText(label, active));
    }

    @Override
    public void attachNavPressFeedback(View item) {
        attachPressFeedback(item);
    }

    // ===== MyPageBuilder.Host 实现:全部委托既有状态与方法,不新增状态 =====

    @Override
    public String visualTheme() {
        return visualTheme;
    }

    @Override
    public int contentColumnWidthPx() {
        return contentColumnWidth();
    }

    @Override
    public int menuCardWidthPx(float percent) {
        return menuCardWidth(percent);
    }

    @Override
    public int statusBarInsetPx() {
        return statusBarHeight();
    }

    @Override
    public int bottomContentInsetPx() {
        return bottomContentInset();
    }

    @Override
    public int colorValue(String hex) {
        return color(hex);
    }

    @Override
    public android.graphics.drawable.Drawable roundedCardBackground(String hex, int radiusDp) {
        return roundedBg(hex, radiusDp);
    }


    @Override
    public String accountName() {
        return accountName;
    }

    @Override
    public String avatarImageUri() {
        return avatarImageUri;
    }

    @Override
    public BackgroundImageCrop avatarImageCrop() {
        return avatarImageCrop;
    }

    @Override
    public String schoolDisplayName() {
        return displaySchoolName();
    }

    @Override
    public String semesterDisplayName() {
        return displaySemesterName();
    }

    @Override
    public String versionText() {
        return appVersionText();
    }

    @Override
    public void openScheduleSettings() {
        showScheduleSettings();
    }

    @Override
    public void openGlobalSettings() {
        showGlobalSettings();
    }

    @Override
    public void openSecuritySettings() {
        showSecuritySettings();
    }

    @Override
    public void openMoreSettings() {
        showMoreSettings();
    }

    @Override
    public void editAccountProfile() {
        showAccountProfileEditor();
    }

    // ===== SettingsPageBuilder.Host 实现:设置页数据与动作委托 =====

    @Override
    public String scheduleName() {
        return scheduleName;
    }

    @Override
    public String currentWeekValue() {
        return getString(R.string.settings_current_week_value, currentWeekFromDate());
    }

    @Override
    public boolean showSaturday() {
        return showSaturday;
    }

    @Override
    public boolean showSunday() {
        return showSunday;
    }

    @Override
    public boolean showOutOfWeek() {
        return showOutOfWeekCourses;
    }

    @Override
    public boolean remindersEnabled() {
        return remindersEnabled;
    }

    @Override
    public int reminderMinutesBefore() {
        return reminderMinutesBefore;
    }

    @Override
    public String reminderStatusText() {
        return courseReminderStatusText();
    }

    @Override
    public String classTimeSummary() {
        return classTimeTableSummary();
    }

    @Override
    public String firstWeekDay() {
        return firstWeekDay;
    }

    @Override
    public int semesterWeeks() {
        return semesterWeeks;
    }

    @Override
    public int coursesSize() {
        return courses.size();
    }

    @Override
    public String parseDiagnosticsSummary() {
        return lastParseDiagnosticsSummary;
    }

    @Override
    public void onShowSaturdayChanged(boolean value) {
        showSaturday = value;
        updateVisibleDayCount();
        saveConfig();
        renderSchedule();
    }

    @Override
    public void onShowSundayChanged(boolean value) {
        showSunday = value;
        updateVisibleDayCount();
        saveConfig();
        renderSchedule();
    }

    @Override
    public void onShowOutOfWeekChanged(boolean value) {
        showOutOfWeekCourses = value;
        saveConfig();
        renderSchedule();
    }

    @Override
    public void onReminderEnabledChanged(boolean value) {
        handleCourseReminderToggle(value);
    }

    @Override
    public void onReminderLeadClicked(View anchor) {
        appearanceDialogs.showChoiceDialog(anchor, getString(R.string.settings_row_reminder_lead),
                new String[]{getString(R.string.settings_minutes_5), getString(R.string.settings_minutes_10), getString(R.string.settings_minutes_15), getString(R.string.settings_minutes_30)},
                getString(R.string.settings_minutes_value, reminderMinutesBefore), value -> {
                    reminderMinutesBefore = Integer.parseInt(value.split(" ")[0]);
                    saveConfig();
                    refreshActiveSettingsPage();
                });
    }

    @Override
    public void onReminderStatusClicked() {
        handleCourseReminderStatusClick();
    }

    @Override
    public void onClassTimeClicked() {
        showClassTimeTableEditor();
    }

    @Override
    public void onFirstWeekDayClicked(View anchor) {
        appearanceDialogs.showDateDialog(getString(R.string.settings_row_first_week_day), firstWeekDay, value -> {
            firstWeekDay = value;
            currentWeek = currentWeekFromDate();
            saveConfig();
            renderSchedule();
            refreshActiveSettingsPage();
        });
    }

    @Override
    public void onSemesterWeeksClicked(View anchor) {
        appearanceDialogs.showNumberDialog(getString(R.string.settings_row_semester_weeks), 1, 20, semesterWeeks, 1, value -> {
            semesterWeeks = value;
            currentWeek = Math.max(1, Math.min(semesterWeeks, currentWeekFromDate()));
            saveConfig();
            renderSchedule();
            updateHeader();
            refreshActiveSettingsPage();
        });
    }

    @Override
    public void onViewAllCoursesClicked() {
        showCourseManagePage();
    }

    @Override
    public void onParseDiagnosticsClicked() {
        scheduleDialogs.showParseDiagnosticsDialog();
    }

    @Override
    public String darkMode() {
        return darkMode;
    }

    @Override
    public String backgroundDisplayValue() {
        return backgroundImageUri.length() == 0 ? getString(R.string.settings_common_not_set) : getString(R.string.settings_album_value);
    }

    @Override
    public boolean showPracticeBanner() {
        return showPracticeBanner;
    }

    @Override
    public boolean collapseLunchBreak() {
        return collapseLunchBreak;
    }

    @Override
    public boolean shellBlurEnabled() {
        return shellBarsBlurEnabled;
    }

    @Override
    public int headerOpacity() {
        return timetableHeaderOpacity;
    }

    @Override
    public int cellHeight() {
        return courseCellHeight;
    }

    @Override
    public int cellRadius() {
        return courseCornerRadius;
    }

    @Override
    public int cellOpacity() {
        return courseBlockOpacity;
    }

    @Override
    public void onScheduleSwitchClicked(View anchor) {
        scheduleDialogs.showScheduleSwitchDialog();
    }

    @Override
    public void onVisualThemeClicked(View anchor) {
        appearanceDialogs.showChoiceDialog(anchor, getString(R.string.settings_row_visual_theme), PolarisVisualTheme.NAMES,
                visualTheme, this::applyVisualTheme);
    }

    @Override
    public void onDarkModeClicked(View anchor) {
        appearanceDialogs.showChoiceDialog(anchor, getString(R.string.settings_row_dark_mode), new String[]{getString(R.string.settings_dark_follow_system), getString(R.string.settings_dark_light), getString(R.string.settings_dark_dark)}, darkMode,
                value -> {
                    darkMode = value;
                    scheduleRepository.saveGlobalDarkMode(darkMode);
                    saveConfig();
                    applyShellAppearance();
                    refreshMyPageBehindSettings();
                    refreshPlanTheme();
                    renderSchedule();
                    SettingsPageBuilder.updateSettingValueRow(anchor, darkMode);
                    refreshVisibleSettingsTheme();
                });
    }

    @Override
    public void onBoardBackgroundClicked(View anchor) {
        appearanceDialogs.showChoiceDialog(anchor, getString(R.string.settings_row_board_background), new String[]{"从系统相册选择", "清除背景"}, backgroundImageUri.length() == 0 ? "清除背景" : "从系统相册选择",
                value -> {
                    if (value.startsWith("从")) {
                        openBackgroundPicker();
                    } else {
                        backgroundImageUri = "";
                        backgroundImageCrop = BackgroundImageCrop.full();
                        timetableBackground = "清爽蓝";
                        saveGlobalAppearance();
                        applyShellAppearance();
                        refreshPlanTheme();
                        renderSchedule();
                        SettingsPageBuilder.updateSettingValueRow(anchor, getString(R.string.settings_common_not_set));
                        refreshVisibleSettingsTheme();
                    }
                });
    }

    @Override
    public void onShowPracticeChanged(boolean value) {
        showPracticeBanner = value;
        saveGlobalAppearance();
        renderSchedule();
        updatePracticeSidePanel();
    }

    @Override
    public void onCollapseLunchChanged(boolean value) {
        collapseLunchBreak = value;
        saveGlobalAppearance();
        renderSchedule();
    }

    @Override
    public void onAppearancePresetClicked(View anchor) {
        appearanceDialogs.showChoiceDialog(anchor, getString(R.string.settings_row_appearance_preset), APPEARANCE_PRESETS,
                appearancePresetName(), this::applyAppearancePreset);
    }

    @Override
    public void onShellBlurChanged(boolean value) {
        shellBarsBlurEnabled = value;
        saveGlobalAppearance();
        applyShellAppearance();
    }

    @Override
    public void onHeaderOpacityClicked(View anchor) {
        appearanceDialogs.showNumberDialog(getString(R.string.settings_row_header_opacity), 40, 100, timetableHeaderOpacity, value -> {
            timetableHeaderOpacity = Math.max(40, Math.min(100, value));
            saveGlobalAppearance();
            applyShellAppearance();
            SettingsPageBuilder.updateSettingValueRow(anchor, timetableHeaderOpacity + "%");
        });
    }

    @Override
    public void onNavOpacityClicked(View anchor) {
        appearanceDialogs.showNumberDialog(getString(R.string.settings_row_nav_opacity), 40, 100, bottomNavOpacity, value -> {
            bottomNavOpacity = Math.max(40, Math.min(100, value));
            saveGlobalAppearance();
            applyShellAppearance();
            SettingsPageBuilder.updateSettingValueRow(anchor, bottomNavOpacity + "%");
        });
    }

    @Override
    public void onNavHeightClicked(View anchor) {
        appearanceDialogs.showNumberDialog(getString(R.string.settings_row_nav_height), 56, 120, bottomNavHeight, 1, value -> {
            bottomNavHeight = Math.max(56, Math.min(120, value));
            saveGlobalAppearance();
            applyShellAppearance();
            refreshMyPageBehindSettings();
            renderSchedule();
            SettingsPageBuilder.updateSettingValueRow(anchor, bottomNavHeight + " dp");
        });
    }

    @Override
    public void onNavRadiusClicked(View anchor) {
        appearanceDialogs.showNumberDialog(getString(R.string.settings_row_nav_radius), 0, 72, bottomNavRadius(), value -> {
            bottomNavRectCornerRadius = Math.max(0, Math.min(72, value));
            saveGlobalAppearance();
            applyShellAppearance();
            SettingsPageBuilder.updateSettingValueRow(anchor, bottomNavRadius() + " dp");
        });
    }

    @Override
    public void onCellHeightClicked(View anchor) {
        appearanceDialogs.showNumberDialog(getString(R.string.settings_row_cell_height), 56, 120, courseCellHeight, value -> {
            courseCellHeight = Math.max(56, Math.min(120, value));
            saveGlobalAppearance();
            renderSchedule();
            SettingsPageBuilder.updateSettingValueRow(anchor, courseCellHeight + " dp");
        });
    }

    @Override
    public void onCellRadiusClicked(View anchor) {
        appearanceDialogs.showNumberDialog(getString(R.string.settings_row_cell_radius), 0, 24, courseCornerRadius, value -> {
            courseCornerRadius = Math.max(0, Math.min(24, value));
            saveGlobalAppearance();
            renderSchedule();
            SettingsPageBuilder.updateSettingValueRow(anchor, courseCornerRadius + " dp");
        });
    }

    @Override
    public void onCellOpacityClicked(View anchor) {
        appearanceDialogs.showNumberDialog(getString(R.string.settings_row_cell_opacity), 45, 100, courseBlockOpacity, value -> {
            courseBlockOpacity = Math.max(45, Math.min(100, value));
            saveGlobalAppearance();
            renderSchedule();
            SettingsPageBuilder.updateSettingValueRow(anchor, courseBlockOpacity + "%");
        });
    }

    @Override
    public String semesterNameDisplay() {
        return displaySemesterName();
    }

    @Override
    public String schoolNameDisplay() {
        return displaySchoolName();
    }

    @Override
    public String contactEmail() {
        return CONTACT_EMAIL;
    }

    @Override
    public String githubDisplay() {
        return PROJECT_HOME_URL.replace("https://", "");
    }

    @Override
    public void onSemesterNameClicked() {
        scheduleDialogs.showSemesterNameDialog();
    }

    @Override
    public void onSchoolClicked() {
        scheduleDialogs.showParserModelDialog();
    }

    @Override
    public void onExportBackupClicked() {
        exportScheduleBackup();
    }

    @Override
    public void onRestoreBackupClicked() {
        openBackupFilePicker();
    }

    @Override
    public void onVersionClicked() {
        copyTextToClipboard(appVersionText());
        android.widget.Toast.makeText(this, getString(R.string.settings_toast_version_copied), android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onContactClicked() {
        copyTextToClipboard(CONTACT_EMAIL);
        android.widget.Toast.makeText(this, getString(R.string.settings_toast_email_copied, CONTACT_EMAIL), android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onGithubClicked() {
        copyTextToClipboard(PROJECT_HOME_URL);
        android.widget.Toast.makeText(this, getString(R.string.settings_toast_github_copied, PROJECT_HOME_URL),
                android.widget.Toast.LENGTH_SHORT).show();
    }


    private SpannableString styledNavText(String text) {
        SpannableString span = new SpannableString(text);
        int lineBreak = text.indexOf('\n');
        if (lineBreak > 0) {
            // AbsoluteSizeSpan 只有 px/dip 两种模式,px 按 scaledDensity 换算等效 sp,可随系统字号缩放。
            span.setSpan(new AbsoluteSizeSpan(spToPx(DesignTokens.TYPE_NAV_GLYPH), false),
                    0, lineBreak, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new AbsoluteSizeSpan(spToPx(DesignTokens.TYPE_NAV_LABEL), false),
                    lineBreak + 1, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private SpannableString styledSettingTitle(String text) {
        SpannableString span = new SpannableString(text);
        int iconEnd = text.indexOf("  ");
        if (iconEnd > 0) {
            span.setSpan(new AbsoluteSizeSpan(spToPx(DesignTokens.TYPE_TITLE_GLYPH), false),
                    0, iconEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private int spToPx(int sp) {
        return Math.round(sp * getResources().getDisplayMetrics().scaledDensity);
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

    @Override
    public void applyThemeElevation(View view, int elevationDp) {
        view.setElevation(dp(elevationDp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int accent = PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive());
            view.setOutlineAmbientShadowColor(Color.argb(isDarkModeActive() ? 62 : 42,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
            view.setOutlineSpotShadowColor(Color.argb(isDarkModeActive() ? 88 : 58,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
        }
    }

    @Override
    public void attachCardPressFeedback(View view, int radius) {
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
                target.setBackground(roundedBg(pressColorHex(), DesignTokens.RADIUS_CARD));
                target.postDelayed(() -> target.setBackgroundColor(Color.TRANSPARENT), 180);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                target.setBackgroundColor(Color.TRANSPARENT);
            }
            return false;
        });
    }

    @Override
    public String pressColorHex() {
        return PolarisVisualTheme.hex(
                PolarisVisualTheme.pressColor(visualTheme, isDarkModeActive()));
    }

    private TextView sectionHeader(String text) {
        return new SettingsPageBuilder(this).sectionHeader(this, text);
    }

    private LinearLayout settingsGroup() {
        return new SettingsPageBuilder(this).settingsGroup(this);
    }

    private View settingValueRow(String label, String value, View.OnClickListener listener) {
        return new SettingsPageBuilder(this).settingValueRow(this, label, value, listener);
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


    private View settingSwitchRow(String label, boolean checked, BooleanSetter setter) {
        // Switch row moved to SettingsPageBuilder; delegate with adapter
        return new SettingsPageBuilder(this).settingSwitchRow(this, label, checked, value -> setter.set(value));
    }

    private SwitchThumbView switchView(boolean checked) {
        SwitchThumbView view = new SwitchThumbView(this, checked, isDarkModeActive());
        view.setTag(TAG_SWITCH_THUMB);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(52), dp(30));
        params.leftMargin = dp(14);
        view.setLayoutParams(params);
        return view;
    }


    private LinearLayout dialogPanel(String titleText) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        panel.setMinimumWidth(dp(292));
        panel.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_DIALOG_SHEET));
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
    return GlassDialogFactory.dialogContent(panel, glassConfig(),
            dialogBlurSource(), radius, opacityPercent);
    }

private View glassDialogContent(ScrollView scrollView, LinearLayout panel, int radius) {
    return GlassDialogFactory.dialogContent(scrollView, glassConfig(),
            dialogBlurSource(), radius);
    }

    private View dialogBlurSource() {
        if (!shellBarsBlurEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        return rootView != null ? rootView : getWindow().getDecorView();
    }

    private GlassDialogFactory.Config glassConfig() {
        return new GlassDialogFactory.Config(this, shellBarsBlurEnabled, isDarkModeActive(),
                isMinimalVisualTheme(), visualTheme);
    }

private GradientDrawable dialogGlassBg(int radius) {
    return GlassDialogFactory.dialogGlassBg(glassConfig(), radius);
    }

private GradientDrawable dialogGlassBg(int radius, int opacityPercent) {
    return GlassDialogFactory.dialogGlassBg(glassConfig(), radius, opacityPercent);
    }

    private void transparentDialog(Dialog dialog) {
        com.polaris.timetable.ui.dialog.DialogWindowHelper.transparentDialog(dialog, isDarkModeActive(), this);
    }

    private void makeDialogStill(Window window) {
        com.polaris.timetable.ui.dialog.DialogWindowHelper.makeDialogStill(window);
    }

    private void showScheduleSettings() {
        LinearLayout panel = new SettingsPageBuilder(this).createScheduleSettingsPanel(this);
        showSettingsPage(getString(R.string.settings_title_schedule), panel);
    }


    void copyParseDiagnostics() {
        if (lastParseDiagnosticsText.length() == 0) {
            Toast.makeText(this, getString(R.string.diagnostics_copy_none), Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, getString(R.string.import_clipboard_unavailable), Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                getString(R.string.diagnostics_clip_label), lastParseDiagnosticsText));
        Toast.makeText(this, getString(R.string.diagnostics_copied), Toast.LENGTH_SHORT).show();
    }

    private void showCourseManagePage() {
        courseManageSelectionMode = false;
        courseManageSelectedIds.clear();
        LinearLayout panel = settingsPagePanel("查看所有课程");
        courseManageStatsCard = settingsGroup();
        courseManageStatsText = new TextView(this);
        courseManageStatsText.setTextColor(mutedColor());
        courseManageStatsText.setTextSize(13);
        courseManageStatsText.setLineSpacing(dp(3), 1f);
        courseManageStatsText.setSingleLine(false);
        courseManageStatsCard.addView(courseManageStatsText);
        panel.addView(courseManageStatsCard);
        panel.addView(buildCourseManageSearchRow());
        courseManageActionBar = buildCourseManageActionBar();
        panel.addView(courseManageActionBar);
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
            empty.setText(getString(R.string.empty_courses_title));
            empty.setTextColor(mutedColor());
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(42), 0, dp(42));
            courseManageListContainer.addView(empty);
            refreshCourseManageStats();
            refreshCourseManageActionBar();
            return;
        }
        List<CourseGroup> groups = filteredCourseGroups();
        if (groups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(getString(R.string.manage_no_match));
            empty.setTextColor(mutedColor());
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(42), 0, dp(42));
            courseManageListContainer.addView(empty);
            refreshCourseManageStats();
            refreshCourseManageActionBar();
            return;
        }
        courseManageListContainer.addView(sectionHeader(courseManageSelectionMode
                ? getString(R.string.manage_title_select) : getString(R.string.manage_title_all)));
        LinearLayout list = settingsGroup();
        Collections.sort(groups, (first, second) -> Integer.compare(courseGroupTimeValue(first), courseGroupTimeValue(second)));
        for (CourseGroup group : groups) {
            list.addView(courseManageRow(group));
        }
        courseManageListContainer.addView(list);
        refreshCourseManageStats();
        refreshCourseManageActionBar();
    }

    private void refreshCourseManageStats() {
        if (courseManageStatsText == null) {
            return;
        }
        ScheduleStatistics.Statistics stats =
                ScheduleStatistics.compute(structuredCourses, semesterWeeks);
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.manage_stats_course, stats.courseCount));
        if (stats.experimentCount > 0) {
            text.append(getString(R.string.manage_stats_experiment, stats.experimentCount));
        }
        if (stats.practiceCount > 0) {
            text.append(getString(R.string.manage_stats_practice, stats.practiceCount));
        }
        if (stats.onlineCount > 0) {
            text.append(getString(R.string.manage_stats_online, stats.onlineCount));
        }
        text.append(getString(R.string.manage_stats_weekly, stats.weeklySections,
                stats.semesterSections));
        if (stats.totalCredits > 0) {
            text.append(getString(R.string.manage_stats_credit, formatCredit(stats.totalCredits)));
        }
        if (stats.teacherCount > 0) {
            text.append(getString(R.string.manage_stats_teacher, stats.teacherCount));
            if (stats.topTeacherCount > 0) {
                text.append(getString(R.string.manage_stats_top_teacher,
                        stats.topTeacher, stats.topTeacherCount));
            }
        }
        courseManageStatsText.setText(text.toString());
        courseManageStatsText.setContentDescription(getString(R.string.manage_stats_cd, text));
    }

    private String formatCredit(double credit) {
        if (credit == Math.floor(credit)) {
            return String.valueOf((long) credit);
        }
        return String.valueOf(credit);
    }

    private LinearLayout buildCourseManageSearchRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        courseManageSearchInput = new EditText(this);
        courseManageSearchInput.setHint(getString(R.string.manage_search_hint));
        courseManageSearchInput.setTextColor(inkColor());
        courseManageSearchInput.setHintTextColor(mutedColor());
        courseManageSearchInput.setTextSize(15);
        courseManageSearchInput.setSingleLine(true);
        courseManageSearchInput.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CHIP));
        courseManageSearchInput.setPadding(dp(12), 0, dp(12), 0);
        courseManageSearchInput.setContentDescription(getString(R.string.manage_search_cd));
        courseManageSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                refreshCourseManageList();
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, dp(46), 1f);
        inputParams.topMargin = dp(10);
        row.addView(courseManageSearchInput, inputParams);

        TextView select = new TextView(this);
        select.setText(courseManageSelectionMode ? getString(R.string.manage_select_done) : getString(R.string.manage_select_multi));
        select.setTextColor(selectedTextColor());
        select.setTextSize(15);
        select.setTypeface(Typeface.DEFAULT_BOLD);
        select.setGravity(Gravity.CENTER);
        select.setBackground(roundedBg(selectedFillHex(), DesignTokens.RADIUS_CHIP));
        select.setContentDescription(courseManageSelectionMode ? getString(R.string.manage_select_done_cd) : getString(R.string.manage_select_multi_cd));
        select.setOnClickListener(v -> toggleCourseManageSelectionMode());
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(dp(64), dp(46));
        selectParams.leftMargin = dp(8);
        selectParams.topMargin = dp(10);
        row.addView(select, selectParams);
        return row;
    }

    private LinearLayout buildCourseManageActionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2), dp(8), dp(2), 0);
        courseManageSelectionCount = new TextView(this);
        courseManageSelectionCount.setTextColor(inkColor());
        courseManageSelectionCount.setTextSize(14);
        courseManageSelectionCount.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(courseManageSelectionCount, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(courseManageActionButton(getString(R.string.manage_action_color), v -> scheduleDialogs.showBatchColorDialog()));
        bar.addView(courseManageActionButton(getString(R.string.manage_action_teacher), v -> scheduleDialogs.showBatchTeacherDialog()));
        bar.addView(courseManageActionButton(getString(R.string.manage_action_delete), v -> scheduleDialogs.showBatchDeleteDialog()));
        bar.setVisibility(View.GONE);
        return bar;
    }

    private TextView courseManageActionButton(String label, View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(inkColor());
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CHIP));
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    private void refreshCourseManageActionBar() {
        if (courseManageActionBar == null) {
            return;
        }
        courseManageActionBar.setVisibility(courseManageSelectionMode
                ? View.VISIBLE : View.GONE);
        if (courseManageSelectionCount != null) {
            courseManageSelectionCount.setText(
                    getString(R.string.manage_selected_count, courseManageSelectedIds.size()));
        }
    }

    private void toggleCourseManageSelectionMode() {
        courseManageSelectionMode = !courseManageSelectionMode;
        if (!courseManageSelectionMode) {
            courseManageSelectedIds.clear();
        }
        refreshCourseManageList();
    }

    private void toggleCourseGroupSelection(CourseGroup group) {
        String id = group.firstCourse().structuredCourseId;
        if (id == null || id.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.manage_old_data_unsupported),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!courseManageSelectedIds.remove(id)) {
            courseManageSelectedIds.add(id);
        }
        refreshCourseManageList();
    }

    private List<CourseGroup> filteredCourseGroups() {
        List<CourseGroup> groups = courseGroupsForManage();
        String query = courseManageSearchInput == null
                ? "" : courseManageSearchInput.getText().toString().trim();
        if (query.length() == 0) {
            return groups;
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<CourseGroup> matched = new ArrayList<>();
        for (CourseGroup group : groups) {
            boolean hit = group.name.toLowerCase(Locale.ROOT).contains(lowerQuery);
            if (!hit) {
                for (Course course : group.courses) {
                    if (course.teacher != null
                            && course.teacher.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                        hit = true;
                        break;
                    }
                    if (course.location != null
                            && course.location.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                        hit = true;
                        break;
                    }
                }
            }
            if (hit) {
                matched.add(group);
            }
        }
        return matched;
    }


    void applyBatchColor(String color) {
        int updated = CourseEditManager.batchUpdateColor(
                structuredCourses, courseManageSelectedIds, color);
        if (updated <= 0) {
            Toast.makeText(this, getString(R.string.manage_error_nothing_updated), Toast.LENGTH_SHORT).show();
            return;
        }
        refreshCourseView();
        scheduleRepository.saveStructuredCourses(activeScheduleId, structuredCourses);
        renderSchedule();
        updateEmptyScheduleView();
        refreshCourseManageList();
        Toast.makeText(this, getString(R.string.manage_updated_color, updated),
                Toast.LENGTH_SHORT).show();
    }


    void applyBatchTeacher(String teacher) {
        int updated = CourseEditManager.batchUpdateTeacher(
                structuredCourses, courseManageSelectedIds, teacher);
        if (updated <= 0) {
            Toast.makeText(this, getString(R.string.manage_error_nothing_updated), Toast.LENGTH_SHORT).show();
            return;
        }
        refreshCourseView();
        scheduleRepository.saveStructuredCourses(activeScheduleId, structuredCourses);
        renderSchedule();
        updateEmptyScheduleView();
        refreshCourseManageList();
        Toast.makeText(this, getString(R.string.manage_updated_teacher, updated),
                Toast.LENGTH_SHORT).show();
    }


    void applyBatchDelete() {
        List<StructuredCourse> previous = new ArrayList<>(structuredCourses);
        int removed = 0;
        for (int index = structuredCourses.size() - 1; index >= 0; index--) {
            StructuredCourse course = structuredCourses.get(index);
            if (course != null && courseManageSelectedIds.contains(course.id)) {
                structuredCourses.remove(index);
                removed++;
            }
        }
        if (removed <= 0) {
            Toast.makeText(this, getString(R.string.manage_error_nothing_deleted), Toast.LENGTH_SHORT).show();
            return;
        }
        refreshCourseView();
        scheduleRepository.saveStructuredCourses(activeScheduleId, structuredCourses);
        rescheduleCourseReminders();
        renderSchedule();
        updateEmptyScheduleView();
        refreshCourseManageList();
        showCourseSavedUndo(previous);
        Toast.makeText(this, getString(R.string.manage_deleted, removed),
                Toast.LENGTH_SHORT).show();
    }

    private List<CourseGroup> courseGroupsForManage() {
        Map<String, CourseGroup> groups = new LinkedHashMap<>();
        for (Course course : courses) {
            String name = course.name == null || course.name.trim().length() == 0
                    ? getString(R.string.course_unnamed) : course.name.trim();
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

        String stableId = group.firstCourse().structuredCourseId;
        boolean selectable = courseManageSelectionMode
                && stableId != null && stableId.trim().length() > 0;
        if (selectable) {
            boolean selected = courseManageSelectedIds.contains(stableId);
            TextView check = new TextView(this);
            check.setText(selected ? "✓" : "");
            check.setTextColor(selected ? Color.WHITE : Color.TRANSPARENT);
            check.setTextSize(16);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setGravity(Gravity.CENTER);
            check.setBackground(roundedBg(selected
                    ? primaryActionFillHex() : groupColorHex(), DesignTokens.RADIUS_CHIP));
            check.setContentDescription(selected ? getString(R.string.row_selected_cd) : getString(R.string.row_unselected_cd));
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                    dp(30), dp(30));
            checkParams.rightMargin = dp(10);
            row.addView(check, checkParams);
        }

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
        row.setOnClickListener(v -> {
            if (courseManageSelectionMode) {
                toggleCourseGroupSelection(group);
            } else {
                showCourseEditor(group.firstCourse());
            }
        });
        attachRowPressFeedback(row);
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
            return getString(R.string.banner_no_fixed_time_short);
        }
        return dayName(course.day) + (course.hasExactTime()
                ? CourseTimeResolver.format(course, courseTimeSettings())
                : course.startSection + "-" + course.endSection + getString(R.string.import_section_suffix));
    }

    private String courseTimeText(Course course) {
        if (course.isBannerOnlyCourse()) {
            return getString(R.string.banner_no_fixed_time_long);
        }
        return dayName(course.day) + "\n" + (course.hasExactTime()
                ? CourseTimeResolver.format(course, courseTimeSettings())
                : course.startSection + "-" + course.endSection + getString(R.string.import_section_suffix));
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
                builder.append(getString(R.string.banner_no_fixed_time_line));
            } else {
                builder.append(dayName(course.day)).append(' ')
                        .append(course.hasExactTime()
                                ? CourseTimeResolver.format(course, courseTimeSettings())
                                : course.startSection + "-" + course.endSection + getString(R.string.import_section_suffix));
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

        if (day >= 0 && day < WeekdayLabels.count()) {
            return WeekdayLabels.label(this, day);
        }
        return getString(R.string.weekday_unknown_short);
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
            choice.setBackground(roundedBg(selected ? selectedFillHex() : cardColorHex(), DesignTokens.RADIUS_CARD));
            choice.setOnClickListener(v -> {
                setter.set(value);
                for (int i = 0; i < row.getChildCount(); i++) {
                    TextView item = (TextView) row.getChildAt(i);
                    boolean active = item.getText().toString().equals(value);
                    item.setTextColor(active ? selectedTextColor() : inkColor());
                    item.setBackground(roundedBg(active ? selectedFillHex() : cardColorHex(), DesignTokens.RADIUS_CARD));
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
        save.setText(getString(R.string.editor_action_save));
        save.setTextColor(Color.WHITE);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setTextSize(16);
        save.setBackground(roundedBg(primaryActionFillHex(), DesignTokens.RADIUS_CARD));
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

    void refreshMyPage() {
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
        FrameLayout.LayoutParams params;
        if (isLandscapeTablet() && tabletSettingsOpen) {
            // 平板侧栏模式：重建后仍保持左侧侧栏的宽度与位置。
            params = new FrameLayout.LayoutParams(
                    dp(320), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT);
            params.leftMargin = dp(104);
        } else {
            params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        }
        int settingsIndex = settingsPage == null ? -1 : contentHost.indexOfChild(settingsPage);
        if (settingsIndex >= 0) {
            contentHost.addView(myPage, settingsIndex, params);
        } else {
            contentHost.addView(myPage, params);
        }
        boolean showMyPage;
        if (isLandscapeTablet() && tabletSettingsOpen) {
            // 平板侧栏模式：我的页始终作为左侧栏可见。
            showMyPage = true;
        } else {
            // 手机三 tab：我的页仅在「我的」tab（2）且设置面板未打开时可见。
            showMyPage = activeTab == 2 && !settingsVisible;
        }
        myPage.setVisibility(showMyPage ? View.VISIBLE : View.GONE);
        applyMyPageMode(isLandscapeTablet() && tabletSettingsOpen);
    }

    private LinearLayout settingsPagePanel(String headingText) {
        return new SettingsPageBuilder(this).settingsPagePanel(this, headingText);
    }

    /**
     * 平板侧栏模式：我的页从居中内容收缩为左侧侧栏（320dp @ x=104），
     * 动画结束后按侧栏宽度重建卡片布局。
     */
    private void openTabletSplit() {
        if (tabletSettingsOpen || myPage == null) {
            return;
        }
        tabletSettingsOpen = true;
        if (paneDivider != null) {
            paneDivider.setVisibility(View.VISIBLE);
        }
        final FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) myPage.getLayoutParams();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int startWidth = params.width > 0 ? params.width : screenWidth;
        int startLeft = Math.max(0, params.leftMargin);
        int endWidth = dp(320);
        int endLeft = dp(104);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            params.width = Math.round(startWidth + (endWidth - startWidth) * t);
            params.leftMargin = Math.round(startLeft + (endLeft - startLeft) * t);
            myPage.requestLayout();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!tabletSettingsOpen) {
                    return; // 动画期间面板已被关闭，跳过侧栏重建。
                }
                params.width = endWidth;
                params.leftMargin = endLeft;
                myPage.requestLayout();
                rebuildMyPageAsSidebar();
            }
        });
        animator.start();
    }

    private void rebuildMyPageAsSidebar() {
        if (contentHost == null || myPage == null) {
            return;
        }
        int settingsIndex = settingsPage == null ? -1 : contentHost.indexOfChild(settingsPage);
        contentHost.removeView(myPage);
        myPage = buildMyPage();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(320), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.LEFT);
        params.leftMargin = dp(104);
        if (settingsIndex >= 0) {
            contentHost.addView(myPage, settingsIndex, params);
        } else {
            contentHost.addView(myPage, params);
        }
        myPage.setVisibility(View.VISIBLE);
        myPage.setTranslationX(0f);
        myPage.setAlpha(1f);
        applyMyPageMode(true);
    }

    /**
     * 平板侧栏关闭：我的页重建为全宽居中内容模式，分隔线隐藏。
     */
    private void closeTabletSplit() {
        if (!tabletSettingsOpen || contentHost == null || myPage == null) {
            return;
        }
        tabletSettingsOpen = false;
        if (paneDivider != null) {
            paneDivider.setVisibility(View.GONE);
        }
        int visibility = myPage.getVisibility();
        int settingsIndex = settingsPage == null ? -1 : contentHost.indexOfChild(settingsPage);
        contentHost.removeView(myPage);
        myPage = buildMyPage();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        if (settingsIndex >= 0) {
            contentHost.addView(myPage, settingsIndex, params);
        } else {
            contentHost.addView(myPage, params);
        }
        myPage.setVisibility(visibility);
        myPage.setTranslationX(0f);
        applyMyPageMode(false);
        if (visibility == View.VISIBLE) {
            myPage.setAlpha(0.4f);
            myPage.animate().alpha(1f).setDuration(180).start();
        }
    }

    /**
     * 我的页内容列模式：侧栏模式内容铺满 320dp 容器；
     * 居中模式内容列封顶 640dp 水平居中。
     */
    private void applyMyPageMode(boolean sideMode) {
        if (myPage == null) {
            return;
        }
        android.widget.ScrollView scroll = findMyPageScrollView(myPage);
        if (scroll == null || scroll.getChildCount() == 0) {
            return;
        }
        View page = scroll.getChildAt(0);
        if (sideMode) {
            page.setLayoutParams(new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        } else {
            int column = Math.min(dp(640), getResources().getDisplayMetrics().widthPixels);
            page.setLayoutParams(new ScrollView.LayoutParams(column,
                    ScrollView.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        }
    }

    private android.widget.ScrollView findMyPageScrollView(View root) {
        if (root instanceof android.widget.ScrollView) {
            return (android.widget.ScrollView) root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                android.widget.ScrollView found = findMyPageScrollView(vg.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
        com.polaris.timetable.databinding.FragmentSettingsBinding settingsBinding = com.polaris.timetable.databinding.FragmentSettingsBinding.inflate(getLayoutInflater());
        settingsBinding.settingsRoot.setBackgroundColor(pageSurfaceColor());
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
        settingsBinding.settingsPageContainer.addView(contentScroll, scrollParams);
        settingsBinding.settingsPageContainer.addView(fixedHeader, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, headerHeight));
        settingsPage.addView(settingsBinding.getRoot(), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (isLandscapeTablet()) {
            // 平板：首次打开设置时我的页收缩为左侧侧栏，右侧面板从右滑入；
            // 后续切换设置页仅替换面板内容。
            boolean firstOpen = !tabletSettingsOpen;
            if (firstOpen) {
                openTabletSplit();
            }
            if (scheduleBoard != null) {
                scheduleBoard.setVisibility(View.GONE);
            }
            if (topPanelContainer != null) {
                topPanelContainer.setVisibility(View.GONE);
            }
            if (myPage != null) {
                myPage.animate().cancel();
                myPage.setClipBounds(null);
                myPage.setVisibility(View.VISIBLE);
                myPage.setTranslationX(0f);
                myPage.setAlpha(1f);
            }
            if (bottomNavView != null) {
                bottomNavView.animate().cancel();
                bottomNavView.setClipBounds(null);
                bottomNavView.setVisibility(View.VISIBLE);
                bottomNavView.setTranslationY(0f);
                bottomNavView.setAlpha(1f);
            }
            settingsPage.setVisibility(View.VISIBLE);
            updatePlanSidePanel();
            if (firstOpen) {
                int panelWidth = Math.max(1,
                        getResources().getDisplayMetrics().widthPixels - dp(425));
                settingsPage.setTranslationX(panelWidth);
                settingsPage.animate()
                        .translationX(0f)
                        .setDuration(220)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                settingsPage.setTranslationX(0f);
            }
            return;
        }
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

    void refreshActiveSettingsPage() {
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
            if (myPage != null && !isLandscapeTablet()) {
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
            switchTab(2);
            return;
        }
        if (isLandscapeTablet()) {
            // 平板：有上级页面时回到上级；否则面板滑出、我的页恢复居中。
            if (previousSettingsTitle != null && previousSettingsTitle.length() > 0) {
                String target = previousSettingsTitle;
                previousSettingsTitle = "";
                suppressSettingsPageAnimation = true;
                try {
                    if ("课表设置".equals(target)) { // 页面标题同时是路由标识,不资源化
                        showScheduleSettings();
                    } else if ("全局设置".equals(target)) {
                        showGlobalSettings();
                    } else if ("安全设置".equals(target)) {
                        showSecuritySettings();
                    } else {
                        switchTab(2);
                    }
                } finally {
                    suppressSettingsPageAnimation = false;
                }
                settingsPage.setTranslationX(0f);
                settingsPage.setAlpha(1f);
                settingsPage.setVisibility(View.VISIBLE);
            } else {
                settingsPage.animate().cancel();
                int panelWidth = Math.max(1,
                        getResources().getDisplayMetrics().widthPixels - dp(425));
                settingsPage.animate()
                        .translationX(panelWidth)
                        .setDuration(180)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            settingsPage.setVisibility(View.GONE);
                            settingsPage.setTranslationX(0f);
                        })
                        .start();
                closeTabletSplit();
                switchTab(2);
            }
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
                switchTab(2);
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
                    switchTab(2);
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


    TextView stepButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(22);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(inkColor());
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CARD));
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
        value.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CARD));
        return value;
    }

    EditText stepInput(String text) {
        EditText value = new EditText(this);
        value.setText(text);
        value.setTextSize(18);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextColor(inkColor());
        value.setGravity(Gravity.CENTER);
        value.setSingleLine(true);
        value.setSelectAllOnFocus(true);
        value.setInputType(InputType.TYPE_CLASS_NUMBER);
        value.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CARD));
        value.setPadding(dp(8), 0, dp(8), 0);
        return value;
    }

    Button pageSaveButton(Runnable onSave) {
        Button save = new Button(this);
        save.setText(getString(R.string.profile_save_apply));
        save.setTextColor(Color.WHITE);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setTextSize(16);
        save.setBackground(roundedBg(primaryActionFillHex(), DesignTokens.RADIUS_CARD));
        save.setOnClickListener(v -> onSave.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(0, dp(12), 0, 0);
        save.setLayoutParams(params);
        return save;
    }

    void showGlobalSettings() {
        LinearLayout panel = new SettingsPageBuilder(this).createGlobalSettingsPanel(this);
        showSettingsPage(getString(R.string.settings_title_global), panel);
    }

    private LinearLayout buildAdvancedShellSettings() {
        return new SettingsPageBuilder(this).buildAdvancedShellSettings(this);
    }

    private LinearLayout buildAdvancedScheduleFrameSettings() {
        return new SettingsPageBuilder(this).buildAdvancedScheduleFrameSettings(this);
    }

    @Override
    public String appearancePresetName() {
        if (matchesAppearancePreset(78, 86, 60, 58, 76, 9, 70)) {
            return "标准";
        }
        if (matchesAppearancePreset(90, 94, 56, 22, 64, 7, 88)) {
            return "紧凑";
        }
        if (matchesAppearancePreset(55, 64, 64, 32, 82, 14, 62)) {
            return "沉浸";
        }
        return "自定义";
    }

    private boolean matchesAppearancePreset(
            int headerOpacity, int navOpacity, int navHeight, int navRadius,
            int cellHeight, int cellRadius, int cellOpacity) {
        return timetableHeaderOpacity == headerOpacity
                && bottomNavOpacity == navOpacity
                && bottomNavHeight == navHeight
                && bottomNavRadius() == navRadius
                && courseCellHeight == cellHeight
                && courseCornerRadius == cellRadius
                && courseBlockOpacity == cellOpacity;
    }

    private void applyAppearancePreset(String preset) {
        if ("紧凑".equals(preset)) {
            setAppearanceValues(90, 94, 56, 22, 64, 7, 88);
        } else if ("沉浸".equals(preset)) {
            setAppearanceValues(55, 64, 64, 32, 82, 14, 62);
        } else {
            setAppearanceValues(78, 86, 60, 58, 76, 9, 70);
        }
        saveGlobalAppearance();
        applyShellAppearance();
        refreshMyPageBehindSettings();
        renderSchedule();
        refreshActiveSettingsPage();
        Toast.makeText(this, getString(R.string.appearance_applied_toast, appearancePresetName()), Toast.LENGTH_SHORT).show();
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
        refreshPlanTheme();
        renderSchedule();
        refreshActiveSettingsPage();
        Toast.makeText(this, getString(R.string.theme_switched_toast, visualTheme), Toast.LENGTH_SHORT).show();
    }

    private void setAppearanceValues(
            int headerOpacity, int navOpacity, int navHeight, int navRadius,
            int cellHeight, int cellRadius, int cellOpacity) {
        timetableHeaderOpacity = headerOpacity;
        bottomNavOpacity = navOpacity;
        bottomNavHeight = navHeight;
        bottomNavRectCornerRadius = navRadius;
        courseCellHeight = cellHeight;
        courseCornerRadius = cellRadius;
        courseBlockOpacity = cellOpacity;
    }

    private void applyShellAppearance() {
        if (getWindow() != null) {
            applyEdgeToEdgeWindow(getWindow());
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
            returnCurrentWeekButton.setBackground(roundedBg(cardColorHex(), DesignTokens.RADIUS_CARD));
        }
        updateTodayOverview();
        updateConflictSummary();
        if (bottomNavView != null) {
            bottomNavView.applyTabColors(activeTab == 0, activeTab == 1);
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


    void deleteCurrentSchedule() {
        List<ScheduleRepository.ScheduleEntry> schedules = scheduleRepository.loadSchedules();
        if (schedules.size() <= 1) {
            Toast.makeText(this, getString(R.string.schedule_error_last_one), Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, getString(R.string.schedule_error_last_one), Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, getString(R.string.schedule_deleted_toast), Toast.LENGTH_SHORT).show();
    }

    void switchSchedule(String scheduleId) {
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
        LinearLayout panel = new SettingsPageBuilder(this).createSecuritySettingsPanel(this);
        showSettingsPage(getString(R.string.settings_title_security), panel);
    }

    private void showMoreSettings() {
        LinearLayout panel = new SettingsPageBuilder(this).createMoreSettingsPanel(this);
        showSettingsPage(getString(R.string.settings_title_more), panel);
    }

    private void copyTextToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Polaris", text));
        }
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

    /**
     * 横屏平板顶栏的周选择下拉按钮：「第 X 周 ▾」。
     */
    private TextView buildWeekSelectorButton() {
        TextView button = new TextView(this);
        button.setText(getString(R.string.week_selector_value, currentWeek));
        button.setTextColor(inkColor());
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinHeight(dp(32));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color(cardColorHex()));
        background.setCornerRadius(dp(16));
        button.setBackground(background);
        button.setContentDescription(getString(R.string.editor_title_week));
        button.setOnClickListener(v -> showWeekPickerPopup(v));
        attachPressFeedback(button);
        return button;
    }

    private void showWeekPickerPopup(View anchor) {
        final PopupWindow[] popupHolder = new PopupWindow[1];
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(6), dp(6), dp(6), dp(6));
        list.setBackground(dialogGlassBg(18, ACTION_PANEL_OPACITY_PERCENT));
        int accent = PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive());
        for (int week = 1; week <= semesterWeeks; week++) {
            final int target = week;
            TextView item = new TextView(this);
            item.setText(getString(R.string.settings_current_week_value, week));
            item.setTextSize(15);
            item.setTypeface(week == currentWeek ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            item.setTextColor(week == currentWeek ? accent : inkColor());
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(14), 0, dp(14), 0);
            item.setOnClickListener(v -> {
                if (popupHolder[0] != null) {
                    popupHolder[0].dismiss();
                }
                switchToWeek(target);
            });
            list.addView(item, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        }
        scroll.addView(list);
        PopupWindow popup = new PopupWindow();
        popup.setContentView(scroll);
        popup.setWidth(dp(168));
        popup.setHeight(dp(Math.min(44 * semesterWeeks + 12, 460)));
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(8));
        popupHolder[0] = popup;
        popup.showAsDropDown(anchor, 0, dp(8));
    }

    private void updateWeekSelector() {
        if (weekSelectorButton != null) {
            weekSelectorButton.setText(getString(R.string.week_selector_value, currentWeek));
        }
    }

    private void switchToWeek(int week) {
        int nextWeek = Math.max(1, Math.min(semesterWeeks, week));
        if (nextWeek == currentWeek) {
            return;
        }
        currentWeek = nextWeek;
        scheduleBoard.setCurrentWeek(nextWeek);
        updateReturnCurrentWeekAction();
        updateHeader();
        updateTodayOverview();
        updateConflictSummary();
        updateWeekSelector();
        updatePracticeSidePanel();
        updatePlanSidePanel();
    }

    /**
     * Called whenever the board lands on a (possibly new) week — from a swipe
     * or from a programmatic page change. Keeps the activity state in sync
     * without re-rendering the board, so week switches stay smooth.
     *
     * delta == 0 means the pager was repositioned programmatically onto the
     * already-current week (e.g. after a settings change re-rendered the
     * board); nothing changed, so do nothing — in particular do not refresh
     * the "我的" page, whose refresh would close the open settings page.
     */
    private void onBoardWeekChanged(int delta) {
        if (delta == 0) {
            return;
        }
        int nextWeek = Math.max(1, Math.min(semesterWeeks, currentWeek + delta));
        if (nextWeek == currentWeek) {
            return;
        }
        currentWeek = nextWeek;
        updateReturnCurrentWeekAction();
        updateHeader();
        updateTodayOverview();
        updateConflictSummary();
        updateWeekSelector();
        updatePracticeSidePanel();
        updatePlanSidePanel();
    }

    void updateHeader() {
        setHeader(headerTitle(), headerSubtitle());
    }

    private String headerTitle() {
        return courses.isEmpty() ? weekSubtitle() : weekTitle();
    }

    private String headerSubtitle() {
        return courses.isEmpty() ? "" : weekSubtitle();
    }

    private String weekTitle() {
        return getString(R.string.settings_current_week_value, currentWeek);
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
        accountName = savedName.length() == 0 ? "管理员" : savedName; // 默认账户名是持久化数据值,不资源化
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
        if (!hasClassTimeTable(config.classTimeConfig)) {
            applySchoolTimeDefaults(selectedParserModel);
        }
        collapseXautMiddleSections = config.collapseXautMiddleSections;
        timetableHeaderOpacity = Math.max(40, Math.min(100, config.timetableHeaderOpacity));
        bottomNavOpacity = Math.max(40, Math.min(100, config.bottomNavOpacity));
        // 72 dp was the old fixed visual height. Migrate that legacy default once to the
        // compact 60 dp bar; all other user-selected heights remain unchanged.
        bottomNavHeight = config.bottomNavHeight == 72
                ? 60 : Math.max(56, Math.min(120, config.bottomNavHeight));
        bottomNavRectCornerRadius = Math.max(0, Math.min(72, config.bottomNavRectCornerRadius));
        shellBarsBlurEnabled = config.shellBarsBlurEnabled;
        updateVisibleDayCount();
    }

    void saveConfig() {
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
        config.bottomNavHeight = bottomNavHeight;
        config.bottomNavCornerRadius = bottomNavRectCornerRadius;
        config.bottomNavRectCornerRadius = bottomNavRectCornerRadius;
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
            Toast.makeText(this, getString(R.string.reminder_toggle_off_toast), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!CourseReminderScheduler.hasOverlayPermission(this)) {
            reminderDialogs.showOverlayPermissionDialog();
            return;
        }
        enableCourseReminders();
    }


    void requestNotificationPermissionForReminders() {
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
            Toast.makeText(this, getString(R.string.reminder_notify_permission_toast), Toast.LENGTH_LONG).show();
            return;
        }
        enableCourseReminders();
    }

    void enableCourseReminders() {
        remindersEnabled = true;
        saveConfig();
        refreshActiveSettingsPage();
        if (CourseReminderScheduler.canScheduleExactAlarms(this)) {
            Toast.makeText(this, getString(R.string.reminder_enabled_toast), Toast.LENGTH_SHORT).show();
        } else {
            reminderDialogs.showExactReminderAccessDialog();
        }
    }


    private String courseReminderStatusText() {
        if (!remindersEnabled) {
            return getString(R.string.reminder_status_off);
        }
        if (CourseReminderScheduler.hasOverlayPermission(this)) {
            return CourseReminderScheduler.canScheduleExactAlarms(this)
                    ? getString(R.string.reminder_status_overlay_exact) : getString(R.string.reminder_status_overlay);
        }
        if (CourseReminderScheduler.hasNotificationPermission(this)) {
            return CourseReminderScheduler.canScheduleExactAlarms(this)
                    ? getString(R.string.reminder_status_notify_exact) : getString(R.string.reminder_status_notify_delayed);
        }
        return getString(R.string.reminder_status_need_permission);
    }

    private void handleCourseReminderStatusClick() {
        if (!remindersEnabled) {
            Toast.makeText(this, getString(R.string.reminder_enable_first), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!CourseReminderScheduler.hasOverlayPermission(this)) {
            openOverlaySettings();
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
        Toast.makeText(this, getString(R.string.reminder_status_overlay_exact_toast), Toast.LENGTH_SHORT).show();
    }

    void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException exception) {
            openAppDetailsSettings();
        }
    }

    void openExactAlarmSettings() {
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

    void openAppDetailsSettings() {
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
        // 计划提醒与课程提醒在同一刷新点重排。
        PlanReminderScheduler.reschedule(this);
    }

    /**
     * Detects reminders that were silently cleared while the process was
     * killed (background cleanup / force stop) and guides the user to grant
     * background protection. Must run before reschedule(), which overwrites
     * the "next reminder" timestamp.
     */
    private void maybeWarnAboutMissedReminders() {
        if (!remindersEnabled || !CourseReminderScheduler.hasDeliveryChannel(this)) {
            return;
        }
        long nextAt = CourseReminderScheduler.nextReminderAtMillis(this);
        if (nextAt <= 0L || nextAt >= System.currentTimeMillis()) {
            return;
        }
        // 本该触发的提醒没有触发；标记已提示，避免每次打开都弹。
        CourseReminderScheduler.setNextReminderAt(this, System.currentTimeMillis());
        reminderDialogs.showReminderGuardDialog();
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

    void copyGlobalAppearanceToSchedule(String scheduleId) {
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
        config.bottomNavHeight = bottomNavHeight;
        config.bottomNavCornerRadius = bottomNavRectCornerRadius;
        config.bottomNavRectCornerRadius = bottomNavRectCornerRadius;
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
        return CourseTimeResolver.firstWeekStartMillis(firstWeekDay);
    }

    private String todayText() {
        Calendar date = Calendar.getInstance();
        return date.get(Calendar.YEAR) + "/" + (date.get(Calendar.MONTH) + 1) + "/"
                + date.get(Calendar.DAY_OF_MONTH);
    }

    Calendar calendarFromText(String value) {
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

    int[] timeFromText(String value) {
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

    String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    int backgroundColor() {
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

    @Override
    public boolean isDarkModeActive() {
        if ("深色".equals(darkMode)) {
            return true;
        }
        if ("浅色".equals(darkMode)) {
            return false;
        }
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public int inkColor() {
        return PolarisVisualTheme.inkColor(visualTheme, isDarkModeActive());
    }

    @Override
    public int mutedColor() {
        return PolarisVisualTheme.mutedColor(visualTheme, isDarkModeActive());
    }

    @Override
    public String cardColorHex() {
        return PolarisVisualTheme.hex(
                PolarisVisualTheme.cardColor(visualTheme, isDarkModeActive()));
    }

    @Override
    public String groupColorHex() {
        return PolarisVisualTheme.hex(
                PolarisVisualTheme.groupColor(visualTheme, isDarkModeActive()));
    }

    String selectedFillHex() {
        if (!isMinimalVisualTheme()) {
            return PolarisVisualTheme.hex(
                    PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive()));
        }
        return isDarkModeActive() ? "#FFFFFF" : "#172033";
    }

    String primaryActionFillHex() {
        return PolarisVisualTheme.hex(
                PolarisVisualTheme.accentColor(visualTheme, isDarkModeActive()));
    }

    private int bottomNavRadius() {
        return Math.max(0, Math.min(72, bottomNavRectCornerRadius));
    }

    int selectedTextColor() {
        if (!isMinimalVisualTheme()) {
            return Color.WHITE;
        }
        return isDarkModeActive() ? color("#172033") : Color.WHITE;
    }

    @Override
    public boolean isMinimalVisualTheme() {
        return PolarisVisualTheme.MINIMAL.equals(
                PolarisVisualTheme.normalize(visualTheme));
    }

    @Override
    public int pageSurfaceColor() {
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

    @Override
    public int color(String hex) {
        return Color.parseColor(hex);
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * 内容列宽（px）：手机与竖屏平板沿用全屏宽；横屏平板封顶 640dp，
     * 保证设置页/我的页在宽屏上仍保持可读的行长。
     */
    @Override
    public int contentColumnWidth() {
        return isLandscapeTablet() ? Math.min(dp(640), getResources().getDisplayMetrics().widthPixels)
                : getResources().getDisplayMetrics().widthPixels;
    }

    /** 横屏平板：导航 rail、顶栏并排、设置双栏等平板专属布局的统一判定。 */
    public boolean isLandscapeTablet() {
        return com.polaris.timetable.ui.WindowSizeClass.isLandscapeTablet(this);
    }

    /**
     * 我的页卡片宽度（px）：手机/竖屏平板为内容列宽百分比；
     * 平板居中模式为 640dp 内容列、侧栏模式为 320dp 侧栏的百分比。
     */
    private int menuCardWidth(float percent) {
        if (isLandscapeTablet()) {
            float base = tabletSettingsOpen ? dp(320) : contentColumnWidth();
            return Math.round(base * percent);
        }
        return Math.round(contentColumnWidth() * percent);
    }

    private String defaultWeeks() {
        return "1-20周";
    }

    private String normalizeWeeks(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() == 0 ? defaultWeeks() : text;
    }

    int parseBounded(String value, int min, int max, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String weekdayText(Calendar date) {

        return WeekdayLabels.label(this, (date.get(Calendar.DAY_OF_WEEK) + 5) % 7);
    }

private GradientDrawable liquidGlassBg(int opacityPercent) {
    return GlassDialogFactory.liquidGlassBg(glassConfig(), opacityPercent);
    }

private View glassLayer(GradientDrawable background, int radius) {
    return GlassDialogFactory.glassLayer(glassConfig(), contentHost, background, radius);
    }

private void updateGlassLayer(View layer, GradientDrawable background, int radius) {
    GlassDialogFactory.updateGlassLayer(layer, glassConfig(), contentHost, background, radius);
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
        // 顶栏高度变化后，同步右侧面板的垂直位置（今日概览在上、实践与计划在下）。
        updateTodayOverviewPanel();
        updatePracticeSidePanel();
        updatePlanSidePanel();
    }

private GradientDrawable floatingPanelBg(int opacityPercent, int radius) {
    return GlassDialogFactory.floatingPanelBg(glassConfig(), opacityPercent, radius);
    }

    @Override
    public GradientDrawable roundedBg(String hex, int radius) {
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

    @Override
    public int statusBarHeight() {
        if (systemTopInset >= 0) {
            return systemTopInset;
        }
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(DesignTokens.GAP_SHELL * 3);
    }

    /**
     * API 30+ 启用 edge-to-edge：系统栏透明，内容延伸到栏后，
     * 布局由真实 WindowInsets 驱动。API 23-29 维持不透明系统栏的历史行为。
     */
    private void applyEdgeToEdgeWindow(Window window) {
        if (window == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.setStatusBarColor(backgroundColor());
            window.setNavigationBarColor(backgroundColor());
            return;
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setDecorFitsSystemWindows(false);
    }

    private void recordBuiltInsets() {
        builtTopInset = statusBarHeight();
        builtBottomInset = Math.max(systemBottomInset, 0);
        builtLeftInset = systemLeftInset;
        builtRightInset = systemRightInset;
    }

    private void attachWindowInsetsListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || rootView == null) {
            return;
        }
        rootView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout());
            Insets nav = windowInsets.getInsets(
                    WindowInsets.Type.navigationBars()
                            | WindowInsets.Type.mandatorySystemGestures());
            int imeBottom = windowInsets.getInsets(WindowInsets.Type.ime()).bottom;
            systemTopInset = bars.top;
            systemBottomInset = nav.bottom;
            systemLeftInset = bars.left;
            systemRightInset = bars.right;
            // 键盘弹出/收起不重建布局，仅动态收缩内容区，保证输入框可见。
            if (contentHost != null) {
                contentHost.setPadding(0, 0, 0, imeBottom);
            }
            // 系统栏 inset 与最近一次构建取值不一致（旋转、折叠、挖孔变化）时重建。
            if (bars.top != builtTopInset || nav.bottom != builtBottomInset
                    || bars.left != builtLeftInset || bars.right != builtRightInset) {
                if (!insetsRebuildPending) {
                    insetsRebuildPending = true;
                    view.post(() -> {
                        insetsRebuildPending = false;
                        rebuildLayoutForInsets();
                    });
                }
            }
            return WindowInsets.CONSUMED;
        });
    }

    private void rebuildLayoutForInsets() {
        if (rootView == null) {
            return;
        }
        rebuildLayout();
    }

    int currentWeekFromDate() {
        int week = CourseTimeResolver.weekForDate(
                firstWeekStartMillis(), Calendar.getInstance());
        return Math.max(1, Math.min(semesterWeeks, week));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebuildLayout();
    }

    private void rebuildLayout() {
        buildLayout();
        renderSchedule();
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

