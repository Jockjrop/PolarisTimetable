package com.polaris.timetable.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.polaris.timetable.Course;
import com.polaris.timetable.R;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.time.CourseTimeResolver;
import com.polaris.timetable.time.ScheduleTimeAxis;
import com.polaris.timetable.validation.CourseConflictDetector;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScheduleBoardView extends FrameLayout {
    private static final String TAG = "ScheduleBoardView";

    public interface OnCourseClickListener {
        void onCourseClick(Course course);
    }

    public interface OnWeekSwipeListener {
        void onWeekSwipe(int delta);
    }

    public interface OnCourseLongClickListener {
        void onCourseLongClick(Course course);
    }

    public interface OnSlotLongClickListener {
        void onSlotLongClick(int day, int section);
    }

    public interface OnPracticeBannerClickListener {
        void onPracticeBannerClick(List<Course> practiceCourses);
    }

    public interface OnCourseDragListener {
        void onCourseDragDrop(Course course, int day, int section);
    }

    public interface OnVerticalScrollListener {
        void onVerticalScroll(int scrollY, int deltaY, boolean atBottom);
    }

    /** 每天列数,与 WeekdayLabels.count() 一致。 */
    private static final int DAY_COUNT = WeekdayLabels.count();
    private static final String[] TIMES = {
            "08:00\n08:50", "08:55\n09:45", "10:15\n11:05", "11:10\n12:00",
            "14:30\n15:20", "15:25\n16:15", "16:35\n17:25", "17:30\n18:20",
            "18:30\n19:20", "19:30\n20:20", "20:25\n21:15"
    };

    private final List<Course> courses = new ArrayList<>();
    private final Map<String, Integer> courseColors = new LinkedHashMap<>();
    private final List<AdaptiveTextTarget> adaptiveTextTargets = new ArrayList<>();
    private final ScrollView verticalScroll;
    private final List<Integer> visibleDays = new ArrayList<>();
    private OnCourseClickListener courseClickListener;
    private OnWeekSwipeListener weekSwipeListener;
    private OnCourseLongClickListener courseLongClickListener;
    private OnSlotLongClickListener slotLongClickListener;
    private OnPracticeBannerClickListener practiceBannerClickListener;
    private OnVerticalScrollListener verticalScrollListener;
    private OnCourseDragListener courseDragListener;
    private TextView dragGhostView;
    private Course dragCourse;
    private FrameLayout dragBoard;
    private int dragWeek;
    private float dragOffsetX;
    private float dragOffsetY;
    private int dragBlockWidth;
    private int dragBlockHeight;
    private int dragTargetDay = -1;
    private int dragTargetSection = -1;
    private View dragTargetHighlight;
    private int firstWeek = 1;
    private int lastWeek = 20;
    private int currentWeek = 18;
    private int visibleDayCount = DAY_COUNT;
    private int sectionCount = TIMES.length;
    private long firstWeekStartMillis = defaultFirstWeekStartMillis();
    private boolean darkMode;
    private String visualTheme = PolarisVisualTheme.MINIMAL;
    private boolean showOutOfWeekCourses;
    private boolean showPracticeBanner = true;
    private boolean hasBackgroundImage;
    private String currentBackgroundImageUri = "";
    private BackgroundImageCrop currentBackgroundCrop = BackgroundImageCrop.full();
    private Bitmap currentBackgroundBitmap;
    private Drawable currentBackgroundDrawable;
    private boolean backgroundDrawableApplied;
    private int appliedBoardBgColor = Integer.MIN_VALUE;
    private int configuredSectionHeight = 76;
    private int courseCornerRadius = 9;
    private int courseBlockOpacity = 100;
    private int timetableHeaderOpacity;
    private String[] sectionTimes = TIMES;
    private CourseTimeResolver.Settings classTimeSettings = CourseTimeResolver.defaultSettings();
    private boolean collapseLunchBreak;
    private ScheduleTimeAxis.Axis timeAxis;
    private int dayWidth;
    private int sectionHeight;
    private int timeWidth;
    private int dayHeaderHeight;
    /** 平板横屏下列宽封顶后，网格整体相对板左侧的居中偏移（px）。 */
    private int boardContentOffset;
    private int overlayTopInset;
    private int overlayBottomInset;
    /** 平板横屏左侧导航 rail 占位：课表板整体右移避让（px）。 */
    private int overlayLeftInset;
    private float boardTouchX;
    private float boardTouchY;
    private boolean waitingForLayout;
    private int lastBoardWidth;
    private int lastRenderSignature = -1;
    private final Map<Integer, PreviewEntry> mainCache = new LinkedHashMap<>();
    private static final int MAIN_CACHE_CAPACITY = 3;
    private final FixedHeightViewPager weekPager;

    public ScheduleBoardView(Context context) {
        super(context);
        setClipChildren(true);
        setBackgroundColor(PolarisVisualTheme.boardSurfaceColor(visualTheme, false));
        resetVisibleDays();

        verticalScroll = new AdaptiveScrollView(context);
        verticalScroll.setFillViewport(true);
        verticalScroll.setBackgroundColor(Color.TRANSPARENT);

        // 标准 ViewPager 承载每周课表板：跟手拖动、惯性翻页、边界回弹
        // 全部由系统组件处理，杜绝自定义手势带来的叠影与竞态。
        weekPager = new FixedHeightViewPager(context);
        weekPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        weekPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                int week = firstWeek + position;
                int delta = week - currentWeek;
                currentWeek = week;
                if (weekSwipeListener != null) {
                    weekSwipeListener.onWeekSwipe(delta);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
        overlayBottomInset = dp(82);
        updateSchedulePadding();
        verticalScroll.addView(weekPager, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        addView(verticalScroll, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

    }

    public void setCourses(List<Course> nextCourses) {
        if (sameCourses(nextCourses)) {
            return;
        }
        courses.clear();
        if (nextCourses != null) {
            courses.addAll(nextCourses);
        }
        rebuildCourseColors();
        renderSchedule();
    }

    public void setOnCourseClickListener(OnCourseClickListener listener) {
        courseClickListener = listener;
    }

    public void setOnWeekSwipeListener(OnWeekSwipeListener listener) {
        weekSwipeListener = listener;
    }

    public void setOnCourseLongClickListener(OnCourseLongClickListener listener) {
        courseLongClickListener = listener;
    }

    public void setOnSlotLongClickListener(OnSlotLongClickListener listener) {
        slotLongClickListener = listener;
    }

    public void setOnPracticeBannerClickListener(OnPracticeBannerClickListener listener) {
        practiceBannerClickListener = listener;
    }

    public void setOnVerticalScrollListener(OnVerticalScrollListener listener) {
        verticalScrollListener = listener;
    }

    public void setOnCourseDragListener(OnCourseDragListener listener) {
        courseDragListener = listener;
    }

    public void setCurrentWeek(int week) {
        int nextWeek = clamp(week, firstWeek, lastWeek);
        int position = nextWeek - firstWeek;
        if (currentWeek == nextWeek
                && (weekPager.getAdapter() == null
                || weekPager.getCurrentItem() == position)) {
            return;
        }
        currentWeek = nextWeek;
        if (weekPager.getAdapter() != null
                && position >= 0 && position < weekPager.getAdapter().getCount()) {
            weekPager.setCurrentItem(position, true);
        }
    }

    public void setWeekBounds(int first, int last) {
        int nextFirstWeek = Math.max(1, first);
        int nextLastWeek = Math.max(nextFirstWeek, last);
        int nextCurrentWeek = clamp(currentWeek, nextFirstWeek, nextLastWeek);
        if (firstWeek == nextFirstWeek && lastWeek == nextLastWeek
                && currentWeek == nextCurrentWeek) {
            return;
        }
        firstWeek = nextFirstWeek;
        lastWeek = nextLastWeek;
        currentWeek = nextCurrentWeek;
        renderSchedule();
    }

    public void setShowPracticeBanner(boolean enabled) {
        if (showPracticeBanner == enabled) {
            return;
        }
        showPracticeBanner = enabled;
        renderSchedule();
    }

    public void setVisibleDayCount(int count) {
        int nextCount = Math.max(1, Math.min(DAY_COUNT, count));
        if (visibleDayCount == nextCount) {
            return;
        }
        visibleDayCount = nextCount;
        resetVisibleDays();
        renderSchedule();
    }

    public void setVisibleDays(boolean showSaturday, boolean showSunday) {
        List<Integer> nextVisibleDays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            nextVisibleDays.add(i);
        }
        if (showSaturday) {
            nextVisibleDays.add(5);
        }
        if (showSunday) {
            nextVisibleDays.add(6);
        }
        if (visibleDays.equals(nextVisibleDays)) {
            return;
        }
        visibleDays.clear();
        visibleDays.addAll(nextVisibleDays);
        visibleDayCount = visibleDays.size();
        renderSchedule();
    }

    public void setSectionCount(int count) {
        int nextCount = Math.max(1, Math.min(20, count));
        if (sectionCount == nextCount) {
            return;
        }
        sectionCount = nextCount;
        renderSchedule();
    }

    public void setClassTimeConfig(String configText) {
        String[] nextTimes = parseClassTimeConfig(configText);
        if (sameTimes(sectionTimes, nextTimes)) {
            return;
        }
        sectionTimes = nextTimes;
        renderSchedule();
    }

    public void setClassTimeSettings(String firstStartTime, int classMinutes, int breakMinutes) {
        setClassTimeSettings(firstStartTime, classMinutes, breakMinutes, 30, "14:30", "16:35", "");
    }

    public void setClassTimeSettings(String firstStartTime, int classMinutes, int breakMinutes, String anchorConfigText) {
        setClassTimeSettings(firstStartTime, classMinutes, breakMinutes, 30, "14:30", "16:35", anchorConfigText);
    }

    public void setClassTimeSettings(String firstStartTime, int classMinutes, int breakMinutes,
                                     int bigBreakMinutes, String afternoonStartTime, String anchorConfigText) {
        setClassTimeSettings(firstStartTime, classMinutes, breakMinutes,
                bigBreakMinutes, afternoonStartTime, "16:35", anchorConfigText);
    }

    public void setClassTimeSettings(String firstStartTime, int classMinutes, int breakMinutes,
                                     int bigBreakMinutes, String afternoonStartTime,
                                     String lateAfternoonStartTime, String anchorConfigText) {
        CourseTimeResolver.Settings settings = new CourseTimeResolver.Settings(
                firstStartTime, classMinutes, breakMinutes, bigBreakMinutes,
                afternoonStartTime, lateAfternoonStartTime, anchorConfigText);
        classTimeSettings = settings;
        String[] nextTimes = CourseTimeResolver.sectionTimeLabels(settings, 20);
        if (sameTimes(sectionTimes, nextTimes)) {
            return;
        }
        sectionTimes = nextTimes;
        renderSchedule();
    }

    public void setCollapseMiddleSections(boolean enabled) {
        setCollapseLunchBreak(enabled);
    }

    public void setCollapseLunchBreak(boolean enabled) {
        if (collapseLunchBreak == enabled) {
            return;
        }
        collapseLunchBreak = enabled;
        renderSchedule();
    }

    public void setCourseMetrics(int cellHeightDp, int cornerRadiusDp) {
        int nextHeight = Math.max(56, Math.min(120, cellHeightDp));
        int nextRadius = Math.max(0, Math.min(24, cornerRadiusDp));
        if (configuredSectionHeight == nextHeight && courseCornerRadius == nextRadius) {
            return;
        }
        configuredSectionHeight = nextHeight;
        courseCornerRadius = nextRadius;
        renderSchedule();
    }

    public void setCourseBlockOpacity(int opacityPercent) {
        int nextOpacity = Math.max(45, Math.min(100, opacityPercent));
        if (courseBlockOpacity == nextOpacity) {
            return;
        }
        courseBlockOpacity = nextOpacity;
        renderSchedule();
    }

    public void setOverlayInsets(int topPx, int bottomPx) {
        int nextTop = Math.max(0, topPx);
        int nextBottom = Math.max(0, bottomPx);
        if (overlayTopInset == nextTop && overlayBottomInset == nextBottom) {
            return;
        }
        overlayTopInset = nextTop;
        overlayBottomInset = nextBottom;
        updateSchedulePadding();
        updateAdaptiveTextColors();
        // inset 变化会影响 ViewPager 固定高度（含 padding），需重算。
        renderSchedule();
    }

    public void setTimetableHeaderOpacity(int opacityPercent) {
        int nextOpacity = Math.max(0, Math.min(100, opacityPercent));
        if (timetableHeaderOpacity == nextOpacity) {
            return;
        }
        timetableHeaderOpacity = nextOpacity;
        renderSchedule();
    }

    public void setFirstWeekStartMillis(long millis) {
        if (firstWeekStartMillis == millis) {
            return;
        }
        firstWeekStartMillis = millis;
        renderSchedule();
    }

    public boolean setBackgroundImageUri(String uriText) {
        return setBackgroundImage(uriText, currentBackgroundCrop);
    }

    public boolean setBackgroundImage(String uriText, BackgroundImageCrop crop) {
        String nextUri = uriText == null ? "" : uriText;
        BackgroundImageCrop nextCrop = crop == null ? BackgroundImageCrop.full() : crop;
        boolean uriChanged = !nextUri.equals(currentBackgroundImageUri);
        boolean cropChanged = !nextCrop.sameAs(currentBackgroundCrop);
        if (!uriChanged && !cropChanged) {
            return nextUri.isEmpty() || hasBackgroundImage;
        }
        currentBackgroundCrop = nextCrop;
        if (!uriChanged && currentBackgroundBitmap != null) {
            currentBackgroundDrawable = new CoverBitmapDrawable(
                    currentBackgroundBitmap, currentBackgroundCrop);
            backgroundDrawableApplied = false;
            applyBoardBackground();
            updateAdaptiveTextColors();
            return hasBackgroundImage;
        }
        currentBackgroundImageUri = nextUri;
        currentBackgroundBitmap = null;
        currentBackgroundDrawable = null;
        backgroundDrawableApplied = false;
        if (nextUri.length() == 0) {
            hasBackgroundImage = false;
            setBackground(null);
            applyBoardBackground();
            updateAdaptiveTextColors();
            return true;
        }
        hasBackgroundImage = false;
        try {
            int targetWidth = getWidth() > 0
                    ? getWidth()
                    : getResources().getDisplayMetrics().widthPixels;
            int targetHeight = getHeight() > 0
                    ? getHeight()
                    : getResources().getDisplayMetrics().heightPixels;
            Bitmap bitmap = BackgroundImageLoader.decode(
                    getContext(),
                    Uri.parse(nextUri),
                    Math.max(1, targetWidth),
                    Math.max(1, targetHeight));
            if (bitmap != null) {
                hasBackgroundImage = true;
                currentBackgroundBitmap = bitmap;
                currentBackgroundDrawable = new CoverBitmapDrawable(bitmap, currentBackgroundCrop);
            } else {
                Log.w(TAG, "Background image decoder returned no bitmap");
            }
            applyBoardBackground();
            updateAdaptiveTextColors();
            return hasBackgroundImage;
        } catch (Exception exception) {
            Log.w(TAG, "Unable to load timetable background", exception);
            applyBoardBackground();
            updateAdaptiveTextColors();
            return false;
        } catch (OutOfMemoryError error) {
            Log.e(TAG, "Insufficient memory for timetable background", error);
            applyBoardBackground();
            updateAdaptiveTextColors();
            return false;
        }
    }

    public void setDarkMode(boolean enabled) {
        if (darkMode == enabled) {
            return;
        }
        darkMode = enabled;
        rebuildCourseColors();
        applyBoardBackground();
        renderSchedule();
    }

    public void setVisualTheme(String theme) {
        String nextTheme = PolarisVisualTheme.normalize(theme);
        if (nextTheme.equals(visualTheme)) {
            return;
        }
        visualTheme = nextTheme;
        rebuildCourseColors();
        applyBoardBackground();
        renderSchedule();
    }

    public void setShowOutOfWeekCourses(boolean enabled) {
        if (showOutOfWeekCourses == enabled) {
            return;
        }
        showOutOfWeekCourses = enabled;
        renderSchedule();
    }

    public void captureCurrentBoardForTransition() {
        // ViewPager 自带切换动画，无需捕获旧板。
    }

    public void playWeekTransition(int delta) {
        // ViewPager 自带标准翻页动画，此处无需额外处理。
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
    }

    /**
     * Returns an interactive board for the given week, reusing the cached one
     * when the rendering inputs have not changed. Week switches therefore swap
     * in an already-built board instead of tearing the old one down and
     * rebuilding synchronously on the UI thread, which was the source of the
     * visible "clear then refill" flicker.
     */
    private FrameLayout interactiveBoard(int week) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return buildBoard(week, true);
        }
        int signature = previewSignature();
        PreviewEntry entry = mainCache.get(week);
        if (entry != null && entry.signature == signature && entry.board.getParent() == null) {
            adaptiveTextTargets.clear();
            adaptiveTextTargets.addAll(entry.targets);
            return entry.board;
        }
        FrameLayout board = buildBoard(week, true);
        mainCache.put(week, new PreviewEntry(signature, board,
                new ArrayList<>(adaptiveTextTargets)));
        if (mainCache.size() > MAIN_CACHE_CAPACITY) {
            mainCache.remove(mainCache.keySet().iterator().next());
        }
        return board;
    }

    /** Content-based signature of everything that affects how a board renders. */
    private int previewSignature() {
        int hash = 1;
        hash = 31 * hash + (visualTheme == null ? 0 : visualTheme.hashCode());
        hash = 31 * hash + (darkMode ? 1 : 0);
        hash = 31 * hash + (showOutOfWeekCourses ? 1 : 0);
        hash = 31 * hash + (showPracticeBanner ? 1 : 0);
        hash = 31 * hash + hashOf(currentBackgroundImageUri);
        hash = 31 * hash + sectionCount;
        hash = 31 * hash + (collapseLunchBreak ? 1 : 0);
        hash = 31 * hash + visibleDayCount;
        hash = 31 * hash + firstWeek;
        hash = 31 * hash + lastWeek;
        hash = 31 * hash + (int) (firstWeekStartMillis ^ (firstWeekStartMillis >>> 32));
        hash = 31 * hash + courseCornerRadius;
        hash = 31 * hash + courseBlockOpacity;
        hash = 31 * hash + configuredSectionHeight;
        hash = 31 * hash + timetableHeaderOpacity;
        // 布局尺寸参与签名：窗口 resize 导致列宽/居中偏移变化时强制重建板，
        // 避免缓存的旧坐标错位。
        hash = 31 * hash + dayWidth;
        hash = 31 * hash + sectionHeight;
        hash = 31 * hash + boardContentOffset;
        if (sectionTimes != null) {
            for (String time : sectionTimes) {
                hash = 31 * hash + (time == null ? 0 : time.hashCode());
            }
        }
        if (classTimeSettings != null) {
            hash = 31 * hash + (classTimeSettings.firstStartTime == null ? 0
                    : classTimeSettings.firstStartTime.hashCode());
            hash = 31 * hash + classTimeSettings.classMinutes;
            hash = 31 * hash + classTimeSettings.breakMinutes;
            hash = 31 * hash + classTimeSettings.bigBreakMinutes;
            hash = 31 * hash + (classTimeSettings.afternoonStartTime == null ? 0
                    : classTimeSettings.afternoonStartTime.hashCode());
            hash = 31 * hash + (classTimeSettings.lateAfternoonStartTime == null ? 0
                    : classTimeSettings.lateAfternoonStartTime.hashCode());
            hash = 31 * hash + (classTimeSettings.anchorConfigText == null ? 0
                    : classTimeSettings.anchorConfigText.hashCode());
        }
        for (Integer day : visibleDays) {
            hash = 31 * hash + day;
        }
        for (Course course : courses) {
            hash = 31 * hash + course.day;
            hash = 31 * hash + course.startSection;
            hash = 31 * hash + course.endSection;
            hash = 31 * hash + hashOf(course.name);
            hash = 31 * hash + hashOf(course.weeks);
            hash = 31 * hash + hashOf(course.location);
            hash = 31 * hash + hashOf(course.teacher);
            hash = 31 * hash + hashOf(course.color);
            hash = 31 * hash + (course.courseType == null ? 0 : course.courseType.ordinal());
        }
        return hash;
    }

    private static int hashOf(String value) {
        return value == null ? 0 : value.hashCode();
    }

    private static final class PreviewEntry {
        final int signature;
        final FrameLayout board;
        final List<AdaptiveTextTarget> targets;

        PreviewEntry(int signature, FrameLayout board, List<AdaptiveTextTarget> targets) {
            this.signature = signature;
            this.board = board;
            this.targets = targets;
        }
    }

    private void renderSchedule() {
        if ((getWidth() <= 0 || getHeight() <= 0) && !waitingForLayout) {
            waitingForLayout = true;
            post(() -> {
                waitingForLayout = false;
                renderSchedule();
            });
            return;
        }
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        // 横屏平板纵向空间紧张：节高额外抬高，让单屏容纳更多节次并让格子接近方形。
        int tabletSectionBoost = landscape ? 16 : 8;
        sectionHeight = dp(tablet ? Math.max(72, configuredSectionHeight + tabletSectionBoost)
                : configuredSectionHeight);
        timeAxis = ScheduleTimeAxis.create(
                courses, classTimeSettings, sectionCount, sectionHeight, collapseLunchBreak);
        timeWidth = resolveTimeWidth(getContext(), tablet);
        int availableWidth = getAvailableBoardWidth();
        // 列宽在 resolveDayWidth 内封顶 110dp：平板横屏 7 列不再被无限拉宽。
        // floor 取整保证手机/竖屏平板 content 不超出可用宽度（仅 1~7dp 居中微移）。
        dayWidth = resolveDayWidth(getContext(), availableWidth, visibleDayCount, tablet);
        dayHeaderHeight = dp(62);
        int contentWidth = timeWidth + dayWidth * visibleDayCount;
        // 横屏平板：网格左侧贴边（右侧空间留给实践/今日概览面板）；
        // 其他设备按剩余空间居中。
        boardContentOffset = tablet && landscape
                ? 0 : Math.max(0, (availableWidth - contentWidth) / 2);
        lastBoardWidth = Math.max(availableWidth, contentWidth);
        int maxHeight = 0;
        for (int week = firstWeek; week <= lastWeek; week++) {
            maxHeight = Math.max(maxHeight, boardHeight(week));
        }
        // 固定高度须包含上下 inset：ViewPager 的 padding 会压缩页面可用高度，
        // 否则板底部（晚节次）会被裁切。
        weekPager.setFixedHeight(Math.max(1, maxHeight + overlayTopInset + overlayBottomInset));

        int signature = previewSignature();
        if (signature != lastRenderSignature || weekPager.getAdapter() == null) {
            lastRenderSignature = signature;
            adaptiveTextTargets.clear();
            weekPager.setAdapter(new WeekPagerAdapter());
            weekPager.getAdapter().notifyDataSetChanged();
        }
        int position = currentWeek - firstWeek;
        if (weekPager.getAdapter() != null && weekPager.getAdapter().getCount() > 0
                && weekPager.getCurrentItem() != position) {
            weekPager.setCurrentItem(position, false);
        }
    }

    private FrameLayout buildBoard(int week, boolean interactive) {
        int width = Math.max(getAvailableBoardWidth(), timeWidth + dayWidth * visibleDayCount);
        int height = boardHeight(week);
        FrameLayout board = new FrameLayout(getContext());
        if (interactive) {
            board.setOnTouchListener((view, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    boardTouchX = event.getX();
                    boardTouchY = event.getY();
                }
                return false;
            });
            board.setOnLongClickListener(view -> {
                int day = (int) ((boardTouchX - boardContentOffset - timeWidth) / dayWidth);
                int section = sectionAtY(Math.round(boardTouchY), week);
                if (slotLongClickListener != null && day >= 0 && day < visibleDayCount
                        && section >= 1 && section <= sectionCount) {
                    slotLongClickListener.onSlotLongClick(visibleDays.get(day), section);
                    return true;
                }
                return false;
            });
        }

        addHeaderBackground(board);
        addMonthLabel(board);
        for (int day = 0; day < visibleDayCount; day++) {
            addDayHeader(board, day, visibleDays.get(day), week);
        }
        addPracticeBanner(board, week, interactive);
        for (int section = 1; section <= sectionCount; section++) {
            if (sectionRowHeight(section) <= 0) {
                continue;
            }
            addTimeLabel(board, section, week);
            addRowLine(board, section, week);
        }
        addLunchHourLines(board, week);
        List<Course> visibleCourses = displayCourses(week);
        Map<Course, SlotLayout> layouts = slotLayouts(visibleCourses);
        List<Course> conflictingCourses = CourseConflictDetector.conflictingCoursesForWeek(
                courses, lastWeek, week, classTimeSettings);
        for (Course course : visibleCourses) {
            addCourseBlock(board, course, week, interactive, layouts.get(course),
                    containsIdentity(conflictingCourses, course));
        }
        board.setLayoutParams(new FrameLayout.LayoutParams(width, height));
        return board;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth) {
            renderSchedule();
        }
    }

    private int getAvailableBoardWidth() {
        // 减去左侧 rail inset：网格居中偏移须按 weekPager 的实际内容宽计算，
        // 否则横屏平板下网格会整体偏右（视觉失衡）。
        int width = getWidth() - getPaddingLeft() - getPaddingRight() - overlayLeftInset;
        if (width > 0) {
            return width;
        }
        return getResources().getDisplayMetrics().widthPixels - overlayLeftInset;
    }

    private CharSequence courseText(Course course, boolean conflict) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (conflict) {
            String badge = getContext().getString(R.string.board_badge_conflict);
            text.append(badge);
            text.setSpan(new RelativeSizeSpan(0.78f), 0, badge.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (course.courseType != CourseType.LECTURE) {
            String badge = course.courseType.badgeText + " · ";
            int start = text.length();
            text.append(badge);
            text.setSpan(new RelativeSizeSpan(0.78f), start, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text.append(course.name);
        if (course.hasExactTime()) {
            text.append("\n").append(CourseTimeResolver.format(course, classTimeSettings));
        }
        if (!course.location.isEmpty()) {
            text.append("\n@").append(course.location);
        }
        if (!course.teacher.isEmpty()) {
            text.append('\n').append(course.teacher);
        }
        return text;
    }

    private int courseColor(Course course) {
        if (course.color != null && !course.color.isEmpty()) {
            try {
                return Color.parseColor(course.color);
            } catch (IllegalArgumentException exception) {
                Log.w(TAG, "Invalid saved course color: " + course.color, exception);
            }
        }
        Integer mapped = courseColors.get(course.name == null ? "" : course.name);
        return mapped == null ? color("#4FA4F3") : mapped;
    }

    private void addMonthLabel(FrameLayout board) {
        TextView view = new TextView(getContext());
        view.setText(getContext().getString(R.string.board_time_column));
        applyAdaptiveTextColor(view, boardContentOffset, 0, timeWidth, dayHeaderHeight, false);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(timeWidth, dayHeaderHeight);
        params.leftMargin = boardContentOffset;
        board.addView(view, params);
    }

    private void addHeaderBackground(FrameLayout board) {
        if (timetableHeaderOpacity <= 0) {
            return;
        }
        View header = new View(getContext());
        int fill = PolarisVisualTheme.MINIMAL.equals(visualTheme)
                ? (darkMode ? color("#101827") : color("#F8FBFF"))
                : PolarisVisualTheme.cardColor(visualTheme, darkMode);
        int alpha = Math.round(255f * timetableHeaderOpacity / 100f);
        int sourceAlpha = Color.alpha(fill);
        int combinedAlpha = Math.round(alpha * sourceAlpha / 255f);
        header.setBackgroundColor(Color.argb(combinedAlpha,
                Color.red(fill), Color.green(fill), Color.blue(fill)));
        // 表头背景与网格区完全对齐：从居中偏移处开始，宽度=时间轴+全部列。
        // （手机端 offset 为 0，宽度即网格全宽，与原来一致。）
        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(
                timeWidth + dayWidth * visibleDayCount,
                dayHeaderHeight);
        headerParams.leftMargin = boardContentOffset;
        board.addView(header, headerParams);
    }

    private void addDayHeader(FrameLayout board, int column, int actualDay, int week) {
        LinearLayout view = new LinearLayout(getContext());
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        boolean today = isToday(actualDay, week);
        if (today && !PolarisVisualTheme.MINIMAL.equals(visualTheme)) {
            GradientDrawable highlight = new GradientDrawable();
            highlight.setColor(PolarisVisualTheme.accentSurfaceColor(visualTheme, darkMode));
            highlight.setStroke(dp(1), PolarisVisualTheme.outlineColor(visualTheme, darkMode));
            highlight.setCornerRadius(dp(13));
            view.setBackground(highlight);
        }

        TextView weekday = new TextView(getContext());
        weekday.setText(WeekdayLabels.label(getContext(), actualDay));
        weekday.setTextSize(tabletText(today ? 15 : 13, today ? 13 : 12));
        int left = boardContentOffset + timeWidth + dayWidth * column;
        applyAdaptiveTextColor(weekday, left, 0, dayWidth, dayHeaderHeight / 2, !today);
        weekday.setTypeface(today ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        weekday.setGravity(Gravity.CENTER);
        view.addView(weekday);

        TextView date = new TextView(getContext());
        date.setText(String.valueOf(dayOfMonth(actualDay, week)));
        date.setTextSize(tabletText(12, 10));
        applyAdaptiveTextColor(date, left, dayHeaderHeight / 2, dayWidth, dayHeaderHeight / 2, !today);
        date.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(3);
        view.addView(date, dateParams);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dayWidth, dayHeaderHeight);
        params.leftMargin = left;
        board.addView(view, params);
    }

    private int dayOfMonth(int day, int week) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(firstWeekStartMillis);
        date.add(Calendar.DATE, (week - 1) * 7 + day);
        return date.get(Calendar.DAY_OF_MONTH);
    }

    private boolean isToday(int day, int week) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(firstWeekStartMillis);
        date.add(Calendar.DATE, (week - 1) * 7 + day);
        Calendar today = Calendar.getInstance();
        return date.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && date.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
    }

    private void addPracticeBanner(FrameLayout board, int week, boolean interactive) {
        List<Course> practiceCourses = practiceCoursesForWeek(week);
        if (practiceCourses.isEmpty()) {
            return;
        }

        LinearLayout banner = new LinearLayout(getContext());
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(14), dp(7), dp(14), dp(7));
        banner.setBackground(practiceBannerBg());

        LinearLayout heading = new LinearLayout(getContext());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(getContext());
        title.setText(getContext().getString(R.string.board_practice_title));
        title.setTextColor(PolarisVisualTheme.MINIMAL.equals(visualTheme)
                ? (darkMode ? color("#9CE9DF") : color("#075E56"))
                : PolarisVisualTheme.accentColor(visualTheme, darkMode));
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        heading.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = new TextView(getContext());
        count.setText(getContext().getString(R.string.board_practice_view_count, practiceCourses.size()));
        count.setTextColor(PolarisVisualTheme.MINIMAL.equals(visualTheme)
                ? (darkMode ? color("#B7D8D4") : color("#39736D"))
                : mutedColor());
        count.setTextSize(11);
        count.setSingleLine(true);
        heading.addView(count);
        banner.addView(heading, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView summary = new TextView(getContext());
        summary.setText(practiceSummary(practiceCourses));
        summary.setTextColor(PolarisVisualTheme.MINIMAL.equals(visualTheme)
                ? (darkMode ? color("#F0FFFC") : color("#123B38"))
                : inkColor());
        summary.setTextSize(14);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(2);
        banner.addView(summary, summaryParams);
        banner.setContentDescription(getContext().getString(R.string.board_cd_practice, practiceCourses.size()));

        if (interactive) {
            banner.setClickable(true);
            banner.setOnClickListener(v -> {
                if (practiceCourses.size() == 1 && courseClickListener != null) {
                    courseClickListener.onCourseClick(practiceCourses.get(0));
                } else if (practiceBannerClickListener != null) {
                    practiceBannerClickListener.onPracticeBannerClick(new ArrayList<>(practiceCourses));
                }
            });
            if (practiceCourses.size() == 1) {
                banner.setOnLongClickListener(v -> {
                    if (courseLongClickListener != null) {
                        courseLongClickListener.onCourseLongClick(practiceCourses.get(0));
                        return true;
                    }
                    return false;
                });
            }
        }

        // 实践横幅与网格列区对齐：左右各留 8dp，不再横跨整块空白。
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(dp(120), dayWidth * visibleDayCount - dp(16)), dp(64));
        params.leftMargin = boardContentOffset + timeWidth + dp(8);
        params.topMargin = dayHeaderHeight + dp(6);
        board.addView(banner, params);
    }

    private List<Course> practiceCoursesForWeek(int week) {
        List<Course> result = new ArrayList<>();
        if (!showPracticeBanner) {
            return result;
        }
        for (Course course : courses) {
            if (course != null && isPracticeBannerCourse(course)
                    && isCourseInWeek(course, week)) {
                result.add(course);
            }
        }
        return result;
    }

    private int practiceBannerInset(int week) {
        if (!showPracticeBanner) {
            return 0;
        }
        for (Course course : courses) {
            if (course != null && isPracticeBannerCourse(course)
                    && isCourseInWeek(course, week)) {
                return dp(76);
            }
        }
        return 0;
    }

    private String practiceSummary(List<Course> practiceCourses) {
        StringBuilder summary = new StringBuilder();
        int visibleCount = Math.min(3, practiceCourses.size());
        for (int index = 0; index < visibleCount; index++) {
            Course course = practiceCourses.get(index);
            if (summary.length() > 0) {
                summary.append(" · ");
            }
            summary.append(course.name == null || course.name.trim().isEmpty()
                    ? getContext().getString(R.string.board_practice_unnamed) : course.name.trim());
        }
        if (practiceCourses.size() == 1) {
            summary.append(" · ").append(practiceTimeText(practiceCourses.get(0)));
        } else if (practiceCourses.size() > visibleCount) {
            summary.append(getContext().getString(R.string.board_practice_more, practiceCourses.size()));
        }
        return summary.toString();
    }

    private boolean isPracticeBannerCourse(Course course) {
        return course.courseType == CourseType.PRACTICE || course.isBannerOnlyCourse();
    }

    private String practiceTimeText(Course course) {
        if (course.isBannerOnlyCourse()) {
            return getContext().getString(R.string.board_practice_concentrated);
        }
        if (course.day >= 0 && course.day < DAY_COUNT && course.hasScheduledTime()) {
            return WeekdayLabels.label(getContext(), course.day) + " "
                    + CourseTimeResolver.format(course, classTimeSettings);
        }
        return getContext().getString(R.string.board_time_pending);
    }

    private GradientDrawable practiceBannerBg() {
        GradientDrawable drawable = new GradientDrawable();
        if (PolarisVisualTheme.MINIMAL.equals(visualTheme)) {
            drawable.setColor(darkMode ? color("#E620504D") : color("#F2DDF4F0"));
            drawable.setStroke(dp(1), darkMode ? color("#5B58CFC0") : color("#8044AFA2"));
            drawable.setCornerRadius(dp(14));
            return drawable;
        }
        drawable.setColor(PolarisVisualTheme.accentSurfaceColor(visualTheme, darkMode));
        drawable.setStroke(dp(1), PolarisVisualTheme.outlineColor(visualTheme, darkMode));
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private void addTimeLabel(FrameLayout board, int section, int week) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        TextView number = new TextView(getContext());
        number.setText(String.valueOf(section));
        int top = sectionTop(section, week);
        int rowHeight = sectionRowHeight(section);
        applyAdaptiveTextColor(number, boardContentOffset, top, timeWidth, rowHeight / 2, false);
        number.setTextSize(getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 19 : 17);
        number.setGravity(Gravity.CENTER);
        box.addView(number);

        TextView time = new TextView(getContext());
        time.setText(section <= sectionTimes.length ? sectionTimes[section - 1] : "");
        applyAdaptiveTextColor(time, boardContentOffset, top + rowHeight / 2, timeWidth, rowHeight / 2, true);
        time.setTextSize(getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 10 : 9);
        time.setGravity(Gravity.CENTER);
        box.addView(time);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(timeWidth, rowHeight);
        params.leftMargin = boardContentOffset;
        params.topMargin = top;
        board.addView(box, params);
    }

    private void addRowLine(FrameLayout board, int section, int week) {
        View line = new View(getContext());
        int top = sectionTop(section, week);
        line.setBackgroundColor(PolarisVisualTheme.gridLineColor(visualTheme, darkMode));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dayWidth * visibleDayCount, dp(1));
        params.leftMargin = boardContentOffset + timeWidth;
        params.topMargin = top;
        board.addView(line, params);
    }

    /**
     * Draws horizontal grid lines at whole hours inside the lunch break region
     * (e.g. 13:00 and 14:00 between the morning and afternoon sections), so the
     * empty midday band still carries hour marks. Skipped when the lunch break
     * is collapsed (no visible region) or when the hour coincides with a
     * section boundary line.
     */
    private void addLunchHourLines(FrameLayout board, int week) {
        if (timeAxis == null || timeAxis.lunchStartMinute < 0
                || timeAxis.lunchEndMinute <= timeAxis.lunchStartMinute
                || timeAxis.isLunchBreakCollapsed()) {
            return;
        }
        int firstHour = timeAxis.lunchStartMinute / 60 + 1;
        int lastHour = (timeAxis.lunchEndMinute - 1) / 60;
        for (int hour = firstHour; hour <= lastHour; hour++) {
            int minute = hour * 60;
            if (minute <= timeAxis.startMinute || minute >= timeAxis.endMinute) {
                continue;
            }
            if (minuteOnSectionBoundary(minute)) {
                continue;
            }
            View line = new View(getContext());
            line.setBackgroundColor(PolarisVisualTheme.gridLineColor(visualTheme, darkMode));
            FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams(dayWidth * visibleDayCount, dp(1));
            params.leftMargin = boardContentOffset + timeWidth;
            params.topMargin = bodyTop(week) + timeAxis.yForMinute(minute);
            board.addView(line, params);
        }
    }

    private boolean minuteOnSectionBoundary(int minute) {
        for (int section = 1; section <= sectionCount; section++) {
            CourseTimeResolver.TimeRange range =
                    CourseTimeResolver.sectionTimeRange(classTimeSettings, section);
            if (range == null) {
                continue;
            }
            if (range.startMinutes == minute || range.endMinutes == minute) {
                return true;
            }
        }
        return false;
    }

    private int boardHeight(int week) {
        return bodyTop(week) + (timeAxis == null ? sectionCount * sectionHeight
                : timeAxis.contentHeight());
    }

    private int bodyTop(int week) {
        return dayHeaderHeight + practiceBannerInset(week);
    }

    private int sectionTop(int section, int week) {
        CourseTimeResolver.TimeRange range = CourseTimeResolver.sectionTimeRange(
                classTimeSettings, Math.max(1, Math.min(sectionCount, section)));
        if (range == null || timeAxis == null) {
            return bodyTop(week) + (Math.max(1, section) - 1) * sectionHeight;
        }
        return bodyTop(week) + timeAxis.yForMinute(range.startMinutes);
    }

    private int sectionRowHeight(int section) {
        CourseTimeResolver.TimeRange range = CourseTimeResolver.sectionTimeRange(
                classTimeSettings, section);
        if (range != null && timeAxis != null) {
            return timeAxis.heightForRange(range.startMinutes, range.endMinutes);
        }
        return sectionHeight;
    }

    private int sectionAtY(int y, int week) {
        int minute = minuteAtY(y, week);
        int closestSection = 1;
        int closestDistance = Integer.MAX_VALUE;
        for (int section = 1; section <= sectionCount; section++) {
            CourseTimeResolver.TimeRange range = CourseTimeResolver.sectionTimeRange(
                    classTimeSettings, section);
            if (range == null) {
                continue;
            }
            if (minute >= range.startMinutes && minute < range.endMinutes) {
                return section;
            }
            int distance = Math.min(Math.abs(minute - range.startMinutes),
                    Math.abs(minute - range.endMinutes));
            if (distance < closestDistance) {
                closestDistance = distance;
                closestSection = section;
            }
        }
        return closestSection;
    }

    private int minuteAtY(int y, int week) {
        if (timeAxis == null) {
            return 0;
        }
        int bodyY = Math.max(0, y - bodyTop(week));
        return timeAxis.minuteForY(bodyY);
    }

    private void addCourseBlock(FrameLayout board, Course course, int week, boolean interactive,
                                SlotLayout layout, boolean conflict) {
        int column = columnForCourse(course);
        if (column < 0) {
            return;
        }
        TextView view = new TextView(getContext());
        view.setText(courseText(course, conflict));
        int fill = courseColor(course);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.START | Gravity.TOP);
        view.setBackground(cardBg(fill, conflict));
        if (!PolarisVisualTheme.MINIMAL.equals(visualTheme)) {
            view.setElevation(dp(darkMode ? 1 : 2));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                view.setOutlineAmbientShadowColor(Color.argb(darkMode ? 64 : 38,
                        Color.red(fill), Color.green(fill), Color.blue(fill)));
                view.setOutlineSpotShadowColor(Color.argb(darkMode ? 86 : 52,
                        Color.red(fill), Color.green(fill), Color.blue(fill)));
            }
        }
        view.setContentDescription(courseContentDescription(course, conflict));
        if (showOutOfWeekCourses && !isCourseInWeek(course, week)) {
            view.setAlpha(0.52f);
        }
        if (interactive) {
            attachCourseBlockGestures(view, course, board, week);
        }

        int slotCount = layout == null ? 1 : Math.max(1, layout.count);
        int slotIndex = layout == null ? 0 : Math.max(0, layout.index);
        int availableWidth = dayWidth - dp(4);
        CourseTimeResolver.TimeRange timeRange =
                CourseTimeResolver.timeRange(course, classTimeSettings);
        if (timeRange == null || timeAxis == null) {
            return;
        }
        int spanHeight = timeAxis.heightForRange(
                timeRange.startMinutes, timeRange.endMinutes);
        int availableHeight = Math.max(dp(20), spanHeight - dp(6));
        int left = boardContentOffset + timeWidth + dayWidth * column + dp(2);
        int top = bodyTop(week) + timeAxis.yForMinute(timeRange.startMinutes) + dp(3);
        int width;
        int height;
        if (layout != null && layout.vertical) {
            int blockHeight = Math.max(dp(24), availableHeight / slotCount);
            width = availableWidth - dp(2);
            height = Math.max(dp(20), blockHeight - dp(2));
            top += blockHeight * slotIndex;
        } else {
            int blockWidth = Math.max(dp(20), availableWidth / slotCount);
            width = blockWidth - dp(2);
            height = availableHeight;
            left += blockWidth * slotIndex;
        }
        int courseTextColor = contrastingCourseTextColor(fill, left, top, width, height);
        view.setTextColor(courseTextColor);
        view.setShadowLayer(dp(1), 0f, dp(1), courseTextColor == Color.WHITE
                ? color("#66000000") : color("#33FFFFFF"));
        configureCourseBlockText(view, width, height);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        params.topMargin = top;
        board.addView(view, params);
    }

    /**
     * Unified gesture handling for course blocks: tap opens the detail dialog,
     * long-press without movement opens the editor, long-press followed by a
     * drag moves the course to another day/section via the drag listener.
     */
    private void attachCourseBlockGestures(TextView view, Course course,
                                           FrameLayout board, int week) {
        final float[] downX = {0f};
        final float[] downY = {0f};
        final boolean[] longPressed = {false};
        final boolean[] dragging = {false};
        final Runnable[] longPressRunnable = new Runnable[1];
        final float touchSlop = ViewConfiguration.get(getContext())
                .getScaledTouchSlop();
        longPressRunnable[0] = () -> {
            if (!dragging[0] && !longPressed[0]) {
                longPressed[0] = true;
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        };
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getX();
                    downY[0] = event.getY();
                    longPressed[0] = false;
                    dragging[0] = false;
                    v.postDelayed(longPressRunnable[0],
                            ViewConfiguration.getLongPressTimeout());
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float deltaX = event.getX() - downX[0];
                    float deltaY = event.getY() - downY[0];
                    if (!dragging[0] && longPressed[0]
                            && (Math.abs(deltaX) > touchSlop
                            || Math.abs(deltaY) > touchSlop)) {
                        dragging[0] = true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        startCourseDrag(view, course, board, week,
                                downX[0], downY[0]);
                    }
                    if (dragging[0]) {
                        updateCourseDrag(event);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    v.removeCallbacks(longPressRunnable[0]);
                    if (dragging[0]) {
                        finishCourseDrag(view);
                        return true;
                    }
                    if (longPressed[0]) {
                        longPressed[0] = false;
                        if (courseLongClickListener != null) {
                            courseLongClickListener.onCourseLongClick(course);
                        }
                        return true;
                    }
                    if (courseClickListener != null) {
                        courseClickListener.onCourseClick(course);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.removeCallbacks(longPressRunnable[0]);
                    if (dragging[0]) {
                        cancelCourseDrag(view);
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    private void startCourseDrag(TextView source, Course course,
                                 FrameLayout board, int week,
                                 float downX, float downY) {
        TextView ghost = new TextView(getContext());
        ghost.setText(source.getText());
        ghost.setTypeface(source.getTypeface());
        ghost.setGravity(source.getGravity());
        ghost.setTextColor(source.getCurrentTextColor());
        Drawable background = source.getBackground();
        if (background != null) {
            ghost.setBackground(background.getConstantState().newDrawable().mutate());
        }
        ghost.setPadding(source.getPaddingLeft(), source.getPaddingTop(),
                source.getPaddingRight(), source.getPaddingBottom());
        ghost.setAlpha(0.9f);
        ghost.setElevation(dp(8));
        ghost.setContentDescription(getContext().getString(R.string.board_cd_dragging, course.name));

        int[] blockLocation = new int[2];
        source.getLocationOnScreen(blockLocation);
        int[] rootLocation = new int[2];
        getLocationOnScreen(rootLocation);
        dragBlockWidth = source.getWidth();
        dragBlockHeight = source.getHeight();
        dragOffsetX = downX;
        dragOffsetY = downY;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dragBlockWidth, dragBlockHeight);
        params.leftMargin = blockLocation[0] - rootLocation[0];
        params.topMargin = blockLocation[1] - rootLocation[1];
        addView(ghost, params);
        dragGhostView = ghost;
        dragCourse = course;
        dragBoard = board;
        dragWeek = week;
        dragTargetDay = -1;
        dragTargetSection = -1;
        source.setAlpha(0.4f);
    }

    private void updateCourseDrag(MotionEvent event) {
        if (dragGhostView == null) {
            return;
        }
        int[] rootLocation = new int[2];
        getLocationOnScreen(rootLocation);
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) dragGhostView.getLayoutParams();
        params.leftMargin = Math.round(event.getRawX() - rootLocation[0] - dragOffsetX);
        params.topMargin = Math.round(event.getRawY() - rootLocation[1] - dragOffsetY);
        dragGhostView.setLayoutParams(params);
        updateDragTarget(event.getRawX(), event.getRawY());
    }

    private void updateDragTarget(float rawX, float rawY) {
        if (dragBoard == null) {
            return;
        }
        int[] boardLocation = new int[2];
        dragBoard.getLocationOnScreen(boardLocation);
        float boardX = rawX - boardLocation[0];
        float boardY = rawY - boardLocation[1];
        int day = (int) ((boardX - boardContentOffset - timeWidth) / dayWidth);
        if (day < 0 || day >= visibleDayCount) {
            day = -1;
        }
        int section = sectionAtY(Math.round(boardY), dragWeek);
        if (day == dragTargetDay && section == dragTargetSection) {
            return;
        }
        dragTargetDay = day;
        dragTargetSection = section;
        if (dragTargetHighlight != null) {
            dragBoard.removeView(dragTargetHighlight);
            dragTargetHighlight = null;
        }
        if (day < 0 || section < 1 || section > sectionCount) {
            return;
        }
        TextView highlight = new TextView(getContext());
        GradientDrawable stroke = new GradientDrawable();
        stroke.setColor(Color.argb(46, 255, 255, 255));
        int accent = PolarisVisualTheme.accentColor(visualTheme, darkMode);
        stroke.setStroke(dp(2), accent);
        stroke.setCornerRadius(dp(courseCornerRadius));
        highlight.setBackground(stroke);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dayWidth - dp(4), sectionRowHeight(section));
        params.leftMargin = boardContentOffset + timeWidth + dayWidth * day + dp(2);
        params.topMargin = sectionTop(section, dragWeek);
        highlight.setLayoutParams(params);
        highlight.setContentDescription(getContext().getString(R.string.board_cd_drop_target, section));
        dragBoard.addView(highlight, params);
        dragTargetHighlight = highlight;
    }

    private void finishCourseDrag(TextView source) {
        int targetDay = dragTargetDay;
        int targetSection = dragTargetSection;
        Course draggedCourse = dragCourse;
        cleanupCourseDrag(source);
        if (targetDay >= 0 && targetSection >= 1 && targetSection <= sectionCount
                && courseDragListener != null) {
            courseDragListener.onCourseDragDrop(
                    draggedCourse, visibleDays.get(targetDay), targetSection);
        }
    }

    private void cancelCourseDrag(TextView source) {
        cleanupCourseDrag(source);
    }

    private void cleanupCourseDrag(TextView source) {
        if (dragGhostView != null) {
            removeView(dragGhostView);
            dragGhostView = null;
        }
        if (dragTargetHighlight != null && dragBoard != null) {
            dragBoard.removeView(dragTargetHighlight);
            dragTargetHighlight = null;
        }
        if (source != null) {
            source.setAlpha(1f);
        }
        dragCourse = null;
        dragBoard = null;
        dragWeek = 0;
        dragTargetDay = -1;
        dragTargetSection = -1;
    }

    private void configureCourseBlockText(TextView view, int width, int height) {
        int horizontalPadding = width < dp(48) ? dp(3) : dp(5);
        int verticalPadding = height < dp(44) ? dp(2) : dp(6);
        view.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        view.setSingleLine(false);
        view.setHorizontallyScrolling(false);
        view.setEllipsize(TextUtils.TruncateAt.END);

        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        int minimumTextSize = tablet ? 10 : (visibleDayCount >= 7 ? 10 : 8);
        int textSize = courseTextSize();
        int maxLines;
        while (true) {
            view.setTextSize(textSize);
            Paint.FontMetricsInt fontMetrics = view.getPaint().getFontMetricsInt();
            int lineHeight = Math.max(1, fontMetrics.descent - fontMetrics.ascent);
            int contentHeight = Math.max(1, height - view.getPaddingTop() - view.getPaddingBottom());
            maxLines = Math.max(1, contentHeight / lineHeight);
            int contentWidth = Math.max(1, width - view.getPaddingLeft() - view.getPaddingRight());
            if (textSize <= minimumTextSize
                    || estimatedCourseLineCount(view, contentWidth) <= maxLines) {
                break;
            }
            textSize--;
        }
        view.setMaxLines(maxLines);
    }

    private int estimatedCourseLineCount(TextView view, int contentWidth) {
        String text = view.getText() == null ? "" : view.getText().toString();
        String[] lines = text.split("\\n", -1);
        int count = 0;
        for (String line : lines) {
            float measuredWidth = view.getPaint().measureText(line);
            count += Math.max(1, (int) Math.ceil(measuredWidth / Math.max(1, contentWidth)));
        }
        return count;
    }

    private Map<Course, SlotLayout> slotLayouts(List<Course> visibleCourses) {
        Map<Course, SlotLayout> layouts = new LinkedHashMap<>();
        for (Course course : visibleCourses) {
            List<Course> overlaps = new ArrayList<>();
            for (Course other : visibleCourses) {
                if (other.day == course.day
                        && timeRangesOverlap(course, other)) {
                    overlaps.add(other);
                }
            }
            boolean vertical = false;
            layouts.put(course, new SlotLayout(overlaps.indexOf(course), overlaps.size(), vertical));
        }
        return layouts;
    }

    private List<Course> displayCourses(int week) {
        if (!courses.isEmpty()) {
            return filterDisplayCourses(courses, week);
        }
        return new ArrayList<>();
    }

    private List<Course> filterDisplayCourses(List<Course> source, int week) {
        List<Course> visibleCourses = new ArrayList<>();
        for (Course course : source) {
            boolean inCurrentWeek = isCourseInWeek(course, week);
            if (columnForCourse(course) >= 0 && (inCurrentWeek
                    || (showOutOfWeekCourses && !hasCourseEnded(course, week)
                    && !hasCurrentWeekOverlap(source, course, week)))) {
                visibleCourses.add(course);
            }
        }
        return visibleCourses;
    }

    private boolean hasCurrentWeekOverlap(List<Course> source, Course candidate, int week) {
        for (Course course : source) {
            if (course == candidate || !isCourseInWeek(course, week) || course.day != candidate.day) {
                continue;
            }
            if (timeRangesOverlap(course, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean timeRangesOverlap(Course first, Course second) {
        CourseTimeResolver.TimeRange firstRange =
                CourseTimeResolver.timeRange(first, classTimeSettings);
        CourseTimeResolver.TimeRange secondRange =
                CourseTimeResolver.timeRange(second, classTimeSettings);
        return firstRange != null && secondRange != null
                && firstRange.startMinutes < secondRange.endMinutes
                && secondRange.startMinutes < firstRange.endMinutes;
    }

    private int columnForCourse(Course course) {
        if (course.day < 0) {
            return -1;
        }
        return visibleDays.indexOf(course.day);
    }

    private void resetVisibleDays() {
        visibleDays.clear();
        for (int i = 0; i < visibleDayCount; i++) {
            visibleDays.add(i);
        }
    }

    private boolean sameCourses(List<Course> nextCourses) {
        if (nextCourses == null) {
            return courses.isEmpty();
        }
        if (courses.size() != nextCourses.size()) {
            return false;
        }
        for (int i = 0; i < courses.size(); i++) {
            if (!sameCourse(courses.get(i), nextCourses.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean sameCourse(Course first, Course second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return first.day == second.day
                && first.startSection == second.startSection
                && first.endSection == second.endSection
                && first.timeMode == second.timeMode
                && first.startMinuteOfDay == second.startMinuteOfDay
                && first.endMinuteOfDay == second.endMinuteOfDay
                && sameText(first.name, second.name)
                && sameText(first.weeks, second.weeks)
                && sameText(first.location, second.location)
                && sameText(first.teacher, second.teacher)
                && sameText(first.raw, second.raw)
                && sameText(first.credit, second.credit)
                && sameText(first.color, second.color)
                && first.courseType == second.courseType;
    }

    private boolean sameText(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private String[] parseClassTimeConfig(String configText) {
        String text = configText == null ? "" : configText.trim();
        if (text.length() == 0 || !text.contains("-")) {
            return TIMES;
        }
        String[] parsed = new String[Math.max(20, TIMES.length)];
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{1,2})\\s+((?:\\d{1,2})[:：](?:\\d{2})\\s*-\\s*(?:\\d{1,2})[:：](?:\\d{2}))")
                .matcher(text);
        while (matcher.find()) {
            int section = Integer.parseInt(matcher.group(1));
            if (section <= 0 || section > parsed.length) {
                continue;
            }
            parsed[section - 1] = matcher.group(2).replace('：', ':').replace("-", "\n");
        }
        for (int i = 0; i < parsed.length; i++) {
            if (parsed[i] == null) {
                parsed[i] = "";
            }
        }
        return parsed;
    }

    private boolean sameTimes(String[] first, String[] second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null || first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            if (!sameText(first[i], second[i])) {
                return false;
            }
        }
        return true;
    }

    private void rebuildCourseColors() {
        courseColors.clear();
        List<String> names = new ArrayList<>();
        for (Course course : courses) {
            String name = course.name == null ? "" : course.name;
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        for (int i = 0; i < names.size(); i++) {
            if (PolarisVisualTheme.MINIMAL.equals(visualTheme)) {
                float hue = (i * 137.508f) % 360f;
                courseColors.put(names.get(i),
                        Color.HSVToColor(new float[]{hue, 0.62f, 0.94f}));
            } else {
                int[] palette = PolarisVisualTheme.coursePalette(visualTheme, darkMode);
                courseColors.put(names.get(i), palette[i % palette.length]);
            }
        }
    }

    private boolean isCourseInWeek(Course course, int week) {
        return CourseTimeResolver.isActiveInWeek(course, week);
    }

    private boolean hasCourseEnded(Course course, int week) {
        int endWeek = courseEndWeek(course);
        return endWeek > 0 && endWeek < week;
    }

    private int courseEndWeek(Course course) {
        String weeks = course.weeks == null ? "" : course.weeks;
        if (weeks.contains("项目")) { // 存储格式判断,不可资源化
            return 0;
        }
        int endWeek = 0;
        java.util.regex.Matcher rangeMatcher = java.util.regex.Pattern
                .compile("(\\d+)\\s*-\\s*(\\d+)")
                .matcher(weeks);
        while (rangeMatcher.find()) {
            endWeek = Math.max(endWeek, Integer.parseInt(rangeMatcher.group(2)));
        }
        java.util.regex.Matcher singleMatcher = java.util.regex.Pattern
                .compile("(^|[^\\d])(\\d+)\\s*周")
                .matcher(weeks);
        while (singleMatcher.find()) {
            endWeek = Math.max(endWeek, Integer.parseInt(singleMatcher.group(2)));
        }
        return endWeek;
    }

    private GradientDrawable cardBg(int fill, boolean conflict) {
        if (PolarisVisualTheme.MINIMAL.equals(visualTheme)) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(courseFill(fill));
            drawable.setCornerRadius(dp(courseCornerRadius));
            if (conflict) {
                drawable.setStroke(dp(2), darkMode ? color("#FF9DA8") : color("#C92840"));
            }
            return drawable;
        }
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{courseFill(mixColor(fill, Color.WHITE, darkMode ? 0.05f : 0.16f)),
                        courseFill(mixColor(fill, Color.BLACK, darkMode ? 0.10f : 0.03f))});
        drawable.setCornerRadius(dp(courseCornerRadius));
        if (conflict) {
            drawable.setStroke(dp(2), darkMode ? color("#FF9DA8") : color("#C92840"));
        } else {
            drawable.setStroke(dp(1), Color.argb(darkMode ? 84 : 150, 255, 255, 255));
        }
        return drawable;
    }

    private int courseFill(int value) {
        int alpha = Math.round(255f * courseBlockOpacity / 100f);
        return Color.argb(alpha, Color.red(value), Color.green(value), Color.blue(value));
    }

    private int mixColor(int from, int to, float amount) {
        float bounded = Math.max(0f, Math.min(1f, amount));
        int red = Math.round(Color.red(from) * (1f - bounded) + Color.red(to) * bounded);
        int green = Math.round(Color.green(from) * (1f - bounded) + Color.green(to) * bounded);
        int blue = Math.round(Color.blue(from) * (1f - bounded) + Color.blue(to) * bounded);
        return Color.rgb(red, green, blue);
    }

    private String courseContentDescription(Course course, boolean conflict) {
        StringBuilder text = new StringBuilder();
        if (conflict) {
            text.append(getContext().getString(R.string.board_cd_conflict_prefix));
        }
        String name = course.name == null || course.name.trim().isEmpty()
                ? getContext().getString(R.string.course_unnamed) : course.name.trim();
        text.append(name);
        if (course.day >= 0 && course.day < DAY_COUNT) {
            text.append("，").append(WeekdayLabels.label(getContext(), course.day));
        }
        text.append("，").append(CourseTimeResolver.format(course, classTimeSettings));
        if (course.location != null && !course.location.trim().isEmpty()) {
            text.append(getContext().getString(R.string.board_cd_location, course.location.trim()));
        }
        return text.append(getContext().getString(R.string.board_cd_course_hint)).toString();
    }

    private boolean containsIdentity(List<Course> source, Course target) {
        for (Course course : source) {
            if (course == target) {
                return true;
            }
        }
        return false;
    }

    private int contrastingCourseTextColor(int fill, int left, int top, int width, int height) {
        int lightText = Color.WHITE;
        int darkText = color("#101827");
        double backgroundLuminance = effectiveCourseBackgroundLuminance(fill, left, top, width, height);
        return contrastRatio(backgroundLuminance, relativeLuminance(darkText))
                >= contrastRatio(backgroundLuminance, relativeLuminance(lightText))
                ? darkText : lightText;
    }

    private double effectiveCourseBackgroundLuminance(int fill, int left, int top, int width, int height) {
        double fillLuminance = relativeLuminance(fill);
        double surfaceLuminance;
        if (hasBackgroundImage && currentBackgroundBitmap != null) {
            double sampledChannel = backgroundLuminance(left, top, width, height) / 255d;
            surfaceLuminance = linearColorChannel(sampledChannel);
        } else {
            surfaceLuminance = relativeLuminance(boardBgColor());
        }
        double opacity = courseBlockOpacity / 100d;
        return fillLuminance * opacity + surfaceLuminance * (1d - opacity);
    }

    private double contrastRatio(double firstLuminance, double secondLuminance) {
        return (Math.max(firstLuminance, secondLuminance) + 0.05d)
                / (Math.min(firstLuminance, secondLuminance) + 0.05d);
    }

    private double relativeLuminance(int value) {
        double red = linearColorChannel(Color.red(value) / 255d);
        double green = linearColorChannel(Color.green(value) / 255d);
        double blue = linearColorChannel(Color.blue(value) / 255d);
        return red * 0.2126d + green * 0.7152d + blue * 0.0722d;
    }

    private double linearColorChannel(double value) {
        return value <= 0.04045d
                ? value / 12.92d
                : Math.pow((value + 0.055d) / 1.055d, 2.4d);
    }

    private int tabletText(int tabletSize, int phoneSize) {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600 ? tabletSize : phoneSize;
    }

    private int courseTextSize() {
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        if (visibleDayCount <= 5) {
            return tablet ? 15 : 12;
        }
        if (visibleDayCount == 6) {
            return tablet ? 14 : 11;
        }
        return tablet ? 13 : 11;
    }

    private int color(String hex) {
        return Color.parseColor(hex);
    }

    private int boardBgColor() {
        return PolarisVisualTheme.boardSurfaceColor(visualTheme, darkMode);
    }

    private int inkColor() {
        return PolarisVisualTheme.inkColor(visualTheme, darkMode);
    }

    private int mutedColor() {
        return PolarisVisualTheme.mutedColor(visualTheme, darkMode);
    }

    private int gridTextColor() {
        return hasBackgroundImage ? Color.WHITE : inkColor();
    }

    private int gridMutedColor() {
        return hasBackgroundImage ? color("#D9E6F5") : mutedColor();
    }

    private void applyAdaptiveTextColor(TextView textView, int left, int top, int width, int height, boolean muted) {
        adaptiveTextTargets.add(new AdaptiveTextTarget(textView, left, top, width, height, muted));
        updateAdaptiveTextColor(textView, left, top, width, height, muted);
    }

    private void updateAdaptiveTextColor(TextView textView, int left, int top, int width, int height, boolean muted) {
        int textColor = adaptiveTextColor(left, top, width, height, muted);
        textView.setTextColor(textColor);
        if (hasBackgroundImage) {
            boolean lightText = Color.red(textColor) > 180;
            textView.setShadowLayer(dp(1), 0f, dp(1), lightText ? color("#66000000") : color("#66FFFFFF"));
        } else {
            textView.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        }
    }

    private void updateAdaptiveTextColors() {
        for (AdaptiveTextTarget target : adaptiveTextTargets) {
            updateAdaptiveTextColor(target.textView, target.left, target.top,
                    target.width, target.height, target.muted);
        }
    }

    private int adaptiveTextColor(int left, int top, int width, int height, boolean muted) {
        if (!hasBackgroundImage || currentBackgroundBitmap == null) {
            return muted ? gridMutedColor() : gridTextColor();
        }
        double luminance = backgroundLuminance(left, top, width, height);
        if (luminance >= 145d) {
            return muted ? color("#A6172033") : color("#172033");
        }
        return muted ? color("#DDEBFF") : Color.WHITE;
    }

    private double backgroundLuminance(int left, int top, int width, int height) {
        if (currentBackgroundBitmap == null
                || currentBackgroundBitmap.getWidth() <= 0
                || currentBackgroundBitmap.getHeight() <= 0) {
            return darkMode ? 0d : 255d;
        }
        int viewWidth = Math.max(1, getWidth());
        int viewHeight = Math.max(1, getHeight());
        int[][] points = {
                {left + width / 2, top + height / 2},
                {left + width / 4, top + height / 2},
                {left + width * 3 / 4, top + height / 2},
                {left + width / 2, top + height / 4},
                {left + width / 2, top + height * 3 / 4}
        };
        double total = 0d;
        for (int[] point : points) {
            total += sampleBackgroundLuminance(point[0] - getScrollX() + overlayLeftInset,
                    point[1] + overlayTopInset - verticalScroll.getScrollY(), viewWidth, viewHeight);
        }
        return total / points.length;
    }

    private void updateSchedulePadding() {
        if (weekPager != null) {
            weekPager.setPadding(overlayLeftInset, overlayTopInset, 0, overlayBottomInset);
        }
    }

    /** 平板横屏：为左侧导航 rail 预留水平 inset，课表网格整体右移避让。 */
    public void setOverlayLeftInset(int insetPx) {
        int bounded = Math.max(0, insetPx);
        if (overlayLeftInset == bounded) {
            return;
        }
        overlayLeftInset = bounded;
        updateSchedulePadding();
        renderSchedule();
    }

    private double sampleBackgroundLuminance(int localX, int localY, int viewWidth, int viewHeight) {
        Bitmap bitmap = currentBackgroundBitmap;
        BackgroundImageCrop fittedCrop = currentBackgroundCrop.fitToAspect(
                bitmap.getWidth(), bitmap.getHeight(), viewWidth / (float) viewHeight);
        float sourceLeft = fittedCrop.left * bitmap.getWidth();
        float sourceTop = fittedCrop.top * bitmap.getHeight();
        float sourceWidth = (fittedCrop.right - fittedCrop.left) * bitmap.getWidth();
        float sourceHeight = (fittedCrop.bottom - fittedCrop.top) * bitmap.getHeight();
        int bitmapX = clamp(Math.round(sourceLeft + localX / (float) viewWidth * sourceWidth),
                0, bitmap.getWidth() - 1);
        int bitmapY = clamp(Math.round(sourceTop + localY / (float) viewHeight * sourceHeight),
                0, bitmap.getHeight() - 1);
        int color = bitmap.getPixel(bitmapX, bitmapY);
        return 0.299d * Color.red(color) + 0.587d * Color.green(color) + 0.114d * Color.blue(color);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void applyBoardBackground() {
        if (hasBackgroundImage && currentBackgroundDrawable != null) {
            if (!backgroundDrawableApplied) {
                setBackground(currentBackgroundDrawable);
                verticalScroll.setBackgroundColor(Color.TRANSPARENT);
                backgroundDrawableApplied = true;
            }
            return;
        }
        int color = boardBgColor();
        if (backgroundDrawableApplied || appliedBoardBgColor != color) {
            setBackgroundColor(color);
            verticalScroll.setBackgroundColor(Color.TRANSPARENT);
            backgroundDrawableApplied = false;
            appliedBoardBgColor = color;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** 网格时间轴宽度（px），板内布局与外部对齐计算共用。 */
    private static int resolveTimeWidth(Context context, boolean tablet) {
        return dp(context, tablet ? 54 : 34);
    }

    /**
     * 网格单列宽度（px）：可用宽度均分后按 38/72dp 下限、110dp 上限夹取。
     * 板内布局与 MainActivity 顶栏/右侧面板对齐计算共用同一组常数。
     */
    private static int resolveDayWidth(Context context, int availableWidthPx,
                                       int visibleDayCount, boolean tablet) {
        int perDay = (availableWidthPx - resolveTimeWidth(context, tablet))
                / Math.max(1, visibleDayCount);
        return Math.max(dp(context, tablet ? 72 : 38), Math.min(dp(context, 110), perDay));
    }

    /**
     * 课表网格总宽度（时间轴+全部列，px）。供顶栏等外部视图与网格左右对齐，
     * 传入的 availableWidthPx 应为网格实际可用宽度（通常为屏幕宽度）。
     */
    public static int gridContentWidth(Context context, int availableWidthPx,
                                       int visibleDayCount) {
        boolean tablet = context.getResources().getConfiguration()
                .smallestScreenWidthDp >= 600;
        return resolveTimeWidth(context, tablet)
                + resolveDayWidth(context, availableWidthPx, visibleDayCount, tablet)
                * Math.max(1, visibleDayCount);
    }

    private class AdaptiveScrollView extends ScrollView {
        AdaptiveScrollView(Context context) {
            super(context);
        }

        @Override
        protected void onScrollChanged(int left, int top, int oldLeft, int oldTop) {
            super.onScrollChanged(left, top, oldLeft, oldTop);
            if (top != oldTop) {
                updateAdaptiveTextColors();
                if (verticalScrollListener != null) {
                    verticalScrollListener.onVerticalScroll(
                            top, top - oldTop, !canScrollVertically(1));
                }
            }
        }
    }

    private static class AdaptiveTextTarget {
        final TextView textView;
        final int left;
        final int top;
        final int width;
        final int height;
        final boolean muted;

        AdaptiveTextTarget(TextView textView, int left, int top, int width, int height, boolean muted) {
            this.textView = textView;
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.muted = muted;
        }
    }

    private static class SlotLayout {
        final int index;
        final int count;
        final boolean vertical;

        SlotLayout(int index, int count, boolean vertical) {
            this.index = index;
            this.count = count;
            this.vertical = vertical;
        }
    }

    private static class CoverBitmapDrawable extends Drawable {
        private final Bitmap bitmap;
        private final BackgroundImageCrop crop;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect source = new Rect();
        private final RectF target = new RectF();

        CoverBitmapDrawable(Bitmap bitmap, BackgroundImageCrop crop) {
            this.bitmap = bitmap;
            this.crop = crop == null ? BackgroundImageCrop.full() : crop;
        }

        @Override
        public void draw(Canvas canvas) {
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                return;
            }
            float width = getBounds().width();
            float height = getBounds().height();
            if (width <= 0f || height <= 0f) {
                return;
            }
            BackgroundImageCrop fittedCrop = crop.fitToAspect(
                    bitmap.getWidth(), bitmap.getHeight(), width / height);
            source.set(
                    Math.max(0, Math.round(fittedCrop.left * bitmap.getWidth())),
                    Math.max(0, Math.round(fittedCrop.top * bitmap.getHeight())),
                    Math.min(bitmap.getWidth(), Math.round(fittedCrop.right * bitmap.getWidth())),
                    Math.min(bitmap.getHeight(), Math.round(fittedCrop.bottom * bitmap.getHeight())));
            target.set(getBounds().left, getBounds().top, getBounds().right, getBounds().bottom);
            canvas.drawBitmap(bitmap, source, target, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.OPAQUE;
        }
    }

    private static long defaultFirstWeekStartMillis() {
        Calendar date = Calendar.getInstance();
        date.set(2026, Calendar.MARCH, 3, 0, 0, 0);
        date.set(Calendar.MILLISECOND, 0);
        return date.getTimeInMillis();
    }

    /** Maps pager positions to week boards, reusing the interactive board cache. */
    private class WeekPagerAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return Math.max(0, lastWeek - firstWeek + 1);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            int week = firstWeek + position;
            FrameLayout board = interactiveBoard(week);
            if (board.getParent() != null) {
                ((ViewGroup) board.getParent()).removeView(board);
            }
            container.addView(board, new ViewGroup.LayoutParams(
                    Math.max(1, lastBoardWidth), ViewGroup.LayoutParams.MATCH_PARENT));
            return board;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            // 板本身保留在缓存中复用，这里只从容器移除。
            container.removeView((View) object);
        }
    }

    /**
     * ViewPager with a fixed height so week switches never change the
     * vertical layout (each page may have a different natural height).
     */
    private static class FixedHeightViewPager extends ViewPager {
        private int fixedHeight;

        FixedHeightViewPager(Context context) {
            super(context);
        }

        void setFixedHeight(int height) {
            if (fixedHeight != height) {
                fixedHeight = height;
                requestLayout();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (fixedHeight > 0) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                        fixedHeight, MeasureSpec.EXACTLY);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
