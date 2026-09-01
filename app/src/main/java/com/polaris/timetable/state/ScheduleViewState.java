package com.polaris.timetable.state;

import com.polaris.timetable.SemesterStartDateDefaults;
import com.polaris.timetable.parser.SchoolParserModel;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.time.CourseTimeResolver;
import com.polaris.timetable.ui.BackgroundImageCrop;
import com.polaris.timetable.ui.DesignTokens;
import com.polaris.timetable.ui.PolarisVisualTheme;

import java.util.Calendar;

/**
 * 课表视图状态持有者：集中管理 MainActivity 中与 {@link ScheduleRepository.Config}
 * 互为镜像的配置字段，以及 {@code applyConfig}/{@code saveConfig} 的纯状态逻辑。
 *
 * <p>抽取目标：阶段 2-2。将原先散落在 MainActivity 中的 30 余个镜像字段与
 * 46 个相关方法（约 281 行）收敛至此，Activity 仅保留一个实例并委托读写，
 * 纯 UI 副作用（布局重建、重排提醒等）仍留在 Activity 侧。
 */
public class ScheduleViewState {

    // ===== 镜像字段（默认值与 MainActivity 历史默认值保持一致） =====

    public String scheduleName = "默认课表";
    public String classTimeConfig = "08:00 开始";
    public String firstClassStartTime = "08:00";
    public int classDurationMinutes = 50;
    public int classBreakMinutes = 10;
    public int classBigBreakMinutes = 30;
    public String afternoonStartTime = "14:30";
    public String lateAfternoonStartTime = "16:35";
    public String firstWeekDay = "2026/3/3";
    public String timetableBackground = "清爽蓝";
    public String visualTheme = PolarisVisualTheme.MINIMAL;
    public String backgroundImageUri = "";
    public BackgroundImageCrop backgroundImageCrop = BackgroundImageCrop.full();
    public int courseSectionCount = 11;
    public int semesterWeeks = 20;
    public boolean remindersEnabled = false;
    public int reminderMinutesBefore = 15;
    public boolean showSaturday = false;
    public boolean showSunday = false;
    public boolean showOutOfWeekCourses = true;
    public boolean showPracticeBanner = true;
    public boolean collapseLunchBreak = false;
    public boolean collapseXautMiddleSections = false;
    public int courseCellHeight = 76;
    public int courseCornerRadius = 9;
    public int courseBlockOpacity = 100;
    public int timetableHeaderOpacity = DesignTokens.GLASS_OPACITY_HEADER_DEFAULT;
    public int bottomNavOpacity = DesignTokens.GLASS_OPACITY_NAV_DEFAULT;
    public int bottomNavHeight = DesignTokens.NAV_HEIGHT_DEFAULT;
    public int bottomNavRectCornerRadius = 58;
    public boolean shellBarsBlurEnabled = true;
    public String semesterName = "";
    public String schoolName = "";
    public String darkMode = "跟随系统";
    public SchoolParserModel selectedParserModel;

    /**
     * 将持久化配置应用到内存镜像，含必要的归一化与边界收敛。
     * 纯状态操作，不触及任何 View 或 Scheduler。
     */
    public void applyConfig(ScheduleRepository.Config config) {
        if (config == null) {
            return;
        }
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
        darkMode = config.darkMode;
    }

    /**
     * 将当前内存镜像填充到待持久化的 Config 对象。
     */
    public void fillConfig(ScheduleRepository.Config config) {
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
    }

    // ===== 归一化与辅助逻辑（原 MainActivity 私有方法，原样迁移，保持行为一致） =====

    private static String normalizedTimeText(String value) {
        int[] time = timeFromText(value);
        return twoDigits(time[0]) + ":" + twoDigits(time[1]);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    static int[] timeFromText(String value) {
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

    private static int normalizedReminderMinutes(int value) {
        int[] choices = {5, 10, 15, 30};
        int closest = choices[0];
        for (int choice : choices) {
            if (Math.abs(choice - value) < Math.abs(closest - value)) {
                closest = choice;
            }
        }
        return closest;
    }

    private static SchoolParserModel parserModelFromConfig(String value) {
        if (value == null || value.length() == 0) {
            return null;
        }
        try {
            return SchoolParserModel.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean hasClassTimeTable(String value) {
        return value != null && !CourseTimeResolver.parseSectionAnchors(value).isEmpty();
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

    static Calendar calendarFromText(String value) {
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
}
