package com.polaris.timetable.ui;

import android.content.Context;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.time.CourseTimeResolver;
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

    public interface OnVerticalScrollListener {
        void onVerticalScroll(int scrollY, int deltaY, boolean atBottom);
    }

    private static final String[] DAYS = {"一", "二", "三", "四", "五", "六", "日"};
    private static final String[] TIMES = {
            "08:00\n08:50", "08:55\n09:45", "10:15\n11:05", "11:10\n12:00",
            "14:30\n15:20", "15:25\n16:15", "16:35\n17:25", "17:30\n18:20",
            "18:30\n19:20", "19:30\n20:20", "20:25\n21:15"
    };

    private final List<Course> courses = new ArrayList<>();
    private final Map<String, Integer> courseColors = new LinkedHashMap<>();
    private final List<AdaptiveTextTarget> adaptiveTextTargets = new ArrayList<>();
    private final LinearLayout scheduleHost;
    private final ScrollView verticalScroll;
    private final List<Integer> visibleDays = new ArrayList<>();
    private OnCourseClickListener courseClickListener;
    private OnWeekSwipeListener weekSwipeListener;
    private OnCourseLongClickListener courseLongClickListener;
    private OnSlotLongClickListener slotLongClickListener;
    private OnPracticeBannerClickListener practiceBannerClickListener;
    private OnVerticalScrollListener verticalScrollListener;
    private int firstWeek = 1;
    private int lastWeek = 20;
    private int currentWeek = 18;
    private int visibleDayCount = DAYS.length;
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
    private boolean collapseMiddleSections;
    private int dayWidth;
    private int sectionHeight;
    private int timeWidth;
    private int dayHeaderHeight;
    private int overlayTopInset;
    private int overlayBottomInset;
    private float touchStartX;
    private float touchStartY;
    private float boardTouchX;
    private float boardTouchY;
    private boolean waitingForLayout;
    private FrameLayout lastBoard;
    private FrameLayout dragSlider;
    private FrameLayout dragOverlay;
    private View dragCurrentBoard;
    private boolean transitionBoardLocked;
    private boolean draggingWeek;
    private boolean previewingWeekDrag;
    private boolean suppressSlotLongClick;
    private long suppressCourseInteractionUntil;
    private boolean skipNextProgrammaticTransition;

    public ScheduleBoardView(Context context) {
        super(context);
        setClipChildren(true);
        setBackgroundColor(PolarisVisualTheme.boardSurfaceColor(visualTheme, false));
        resetVisibleDays();

        verticalScroll = new AdaptiveScrollView(context);
        verticalScroll.setFillViewport(true);
        verticalScroll.setBackgroundColor(Color.TRANSPARENT);

        scheduleHost = new LinearLayout(context);
        scheduleHost.setOrientation(LinearLayout.VERTICAL);
        overlayBottomInset = dp(82);
        updateSchedulePadding();
        verticalScroll.addView(scheduleHost, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.WRAP_CONTENT, ScrollView.LayoutParams.WRAP_CONTENT));
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

    public void setCurrentWeek(int week) {
        int nextWeek = clamp(week, firstWeek, lastWeek);
        if (currentWeek == nextWeek) {
            return;
        }
        currentWeek = nextWeek;
        renderSchedule();
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
        int nextCount = Math.max(1, Math.min(DAYS.length, count));
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
        String[] nextTimes = CourseTimeResolver.sectionTimeLabels(settings, 20);
        if (sameTimes(sectionTimes, nextTimes)) {
            return;
        }
        sectionTimes = nextTimes;
        renderSchedule();
    }

    public void setCollapseMiddleSections(boolean enabled) {
        if (collapseMiddleSections == enabled) {
            return;
        }
        collapseMiddleSections = enabled;
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
        lastBoard = null;
        transitionBoardLocked = false;
    }

    public void playWeekTransition(int delta) {
        if (skipNextProgrammaticTransition) {
            skipNextProgrammaticTransition = false;
            scheduleHost.setTranslationX(0f);
            return;
        }
        skipNextProgrammaticTransition = false;
        scheduleHost.animate().cancel();
        scheduleHost.setTranslationX(0f);
    }

    private void finishWeekTransition(View newBoard, int width, int height) {
        try {
            scheduleHost.removeAllViews();
            if (newBoard.getParent() instanceof ViewGroup) {
                ((ViewGroup) newBoard.getParent()).removeView(newBoard);
            }
            newBoard.setTranslationX(0f);
            scheduleHost.addView(newBoard, new LinearLayout.LayoutParams(width, height));
            scheduleHost.setTranslationX(0f);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Week transition failed; rendering without animation", exception);
            renderSchedule();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchStartX = event.getX();
            touchStartY = event.getY();
            draggingWeek = false;
            scheduleHost.animate().cancel();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - touchStartX;
            float dy = event.getY() - touchStartY;
            if (Math.abs(dx) > dp(6) && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                if (!draggingWeek) {
                    beginWeekDrag(event);
                }
                draggingWeek = true;
                int width = Math.max(1, getDragPageWidth());
                int delta = dx < 0 ? 1 : -1;
                if (!canChangeWeek(delta)) {
                    if (dragSlider != null) {
                        dragSlider.setTranslationX(-width);
                    }
                    scheduleHost.setTranslationX(0f);
                    return true;
                }
                prepareWeekDragPreview();
                float limited = Math.max(-width, Math.min(width, dx));
                if (dragSlider != null) {
                    dragSlider.setTranslationX(-width + limited);
                } else {
                    scheduleHost.setTranslationX(limited);
                }
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            float dx = event.getX() - touchStartX;
            float dy = event.getY() - touchStartY;
            int width = Math.max(1, getDragPageWidth());
            int threshold = Math.max(dp(42), Math.round(width * 0.12f));
            if (event.getAction() == MotionEvent.ACTION_UP
                    && Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                int delta = dx < 0 ? 1 : -1;
                if (canChangeWeek(delta)) {
                    if (dragSlider != null) {
                        float target = delta > 0 ? -width * 2f : 0f;
                        dragSlider.animate()
                                .translationX(target)
                                .setDuration(220)
                                .withEndAction(() -> finishGestureWeekSwitch(delta))
                                .start();
                    } else {
                        scheduleHost.animate()
                            .translationX(delta > 0 ? -width * 0.45f : width * 0.45f)
                            .setDuration(100)
                            .withEndAction(() -> finishGestureWeekSwitch(delta))
                            .start();
                    }
                    draggingWeek = false;
                    keepCourseInteractionsSuppressed();
                    return true;
                }
            }
            if (draggingWeek) {
                if (dragSlider != null) {
                    dragSlider.animate()
                            .translationX(-width)
                            .setDuration(160)
                            .withEndAction(this::restoreCurrentBoardFromDrag)
                            .start();
                } else {
                    scheduleHost.animate().translationX(0f).setDuration(140).start();
                }
                draggingWeek = false;
                keepCourseInteractionsSuppressed();
                return true;
            }
            suppressSlotLongClick = false;
        }
        return super.dispatchTouchEvent(event);
    }

    private void finishGestureWeekSwitch(int delta) {
        if (!canChangeWeek(delta)) {
            restoreCurrentBoardFromDrag();
            return;
        }
        skipNextProgrammaticTransition = true;
        if (weekSwipeListener != null) {
            weekSwipeListener.onWeekSwipe(delta);
        }
        post(this::finishCommittedWeekDrag);
        keepCourseInteractionsSuppressed();
    }

    private void finishCommittedWeekDrag() {
        if (!previewingWeekDrag) {
            scheduleHost.setTranslationX(0f);
            return;
        }
        verticalScroll.setVisibility(VISIBLE);
        if (dragOverlay != null) {
            removeView(dragOverlay);
        }
        dragSlider = null;
        dragOverlay = null;
        dragCurrentBoard = null;
        previewingWeekDrag = false;
        scheduleHost.setTranslationX(0f);
        keepCourseInteractionsSuppressed();
    }

    private void beginWeekDrag(MotionEvent event) {
        keepCourseInteractionsSuppressed();
        cancelActiveChildGesture(event);
        cancelLongPressRecursively(this);
    }

    private void keepCourseInteractionsSuppressed() {
        suppressSlotLongClick = true;
        suppressCourseInteractionUntil = System.currentTimeMillis() + 320L;
        postDelayed(() -> {
            if (System.currentTimeMillis() >= suppressCourseInteractionUntil) {
                suppressSlotLongClick = false;
            }
        }, 340L);
    }

    private boolean interactionsSuppressed() {
        return draggingWeek || previewingWeekDrag || suppressSlotLongClick
                || System.currentTimeMillis() < suppressCourseInteractionUntil;
    }

    private void cancelActiveChildGesture(MotionEvent event) {
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        verticalScroll.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    private void cancelLongPressRecursively(View view) {
        view.cancelLongPress();
        view.setPressed(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                cancelLongPressRecursively(group.getChildAt(i));
            }
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
    }

    private void prepareWeekDragPreview() {
        if (previewingWeekDrag || scheduleHost.getChildCount() == 0
                || !(scheduleHost.getChildAt(0) instanceof FrameLayout)) {
            return;
        }
        int width = getDragPageWidth();
        int previousWeek = Math.max(firstWeek, currentWeek - 1);
        int nextWeek = Math.min(lastWeek, currentWeek + 1);
        int height = Math.max(scheduleHost.getChildAt(0).getHeight(),
                Math.max(boardHeight(previousWeek),
                        Math.max(boardHeight(currentWeek), boardHeight(nextWeek))));
        if (width <= 0 || height <= 0) {
            return;
        }

        dragOverlay = new FrameLayout(getContext());
        dragOverlay.setClipChildren(true);
        dragOverlay.setBackgroundColor(Color.TRANSPARENT);
        dragSlider = new FrameLayout(getContext());
        FrameLayout previous = buildBoard(previousWeek, false);
        FrameLayout current = buildBoard(currentWeek, false);
        FrameLayout next = buildBoard(nextWeek, false);
        dragSlider.addView(previous, dragPageParams(0, width, height));
        dragSlider.addView(current, dragPageParams(width, width, height));
        dragSlider.addView(next, dragPageParams(width * 2, width, height));
        dragSlider.setTranslationX(-width);

        FrameLayout.LayoutParams sliderParams = new FrameLayout.LayoutParams(width * 3, height);
        sliderParams.topMargin = overlayTopInset - verticalScroll.getScrollY();
        dragOverlay.addView(dragSlider, sliderParams);
        addView(dragOverlay, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        verticalScroll.setVisibility(INVISIBLE);
        previewingWeekDrag = true;
    }

    private boolean canChangeWeek(int delta) {
        int targetWeek = currentWeek + delta;
        return targetWeek >= firstWeek && targetWeek <= lastWeek;
    }

    private FrameLayout.LayoutParams dragPageParams(int left, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.leftMargin = left;
        return params;
    }

    private int getDragPageWidth() {
        int childWidth = scheduleHost.getChildCount() > 0 ? scheduleHost.getChildAt(0).getWidth() : 0;
        return Math.max(Math.max(getWidth(), childWidth), getAvailableBoardWidth());
    }

    private void restoreCurrentBoardFromDrag() {
        if (!previewingWeekDrag) {
            scheduleHost.setTranslationX(0f);
            return;
        }
        if (dragOverlay != null) {
            removeView(dragOverlay);
        }
        dragSlider = null;
        dragOverlay = null;
        dragCurrentBoard = null;
        previewingWeekDrag = false;
        verticalScroll.setVisibility(VISIBLE);
        scheduleHost.setTranslationX(0f);
        renderSchedule();
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
        adaptiveTextTargets.clear();
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        sectionHeight = dp(tablet ? Math.max(72, configuredSectionHeight + 8) : configuredSectionHeight);
        timeWidth = dp(tablet ? 54 : 34);
        int availableWidth = getAvailableBoardWidth();
        dayWidth = Math.max(dp(tablet ? 72 : 38), (availableWidth - timeWidth) / visibleDayCount);
        dayHeaderHeight = dp(62);

        if (!transitionBoardLocked && scheduleHost.getChildCount() > 0 && scheduleHost.getChildAt(0) instanceof FrameLayout) {
            lastBoard = (FrameLayout) scheduleHost.getChildAt(0);
        } else if (!transitionBoardLocked) {
            lastBoard = null;
        }
        scheduleHost.removeAllViews();
        int width = Math.max(availableWidth, timeWidth + dayWidth * visibleDayCount);
        int height = boardHeight(currentWeek);
        FrameLayout board = buildBoard(currentWeek, true);
        scheduleHost.addView(board, new LinearLayout.LayoutParams(width, height));
        if (!draggingWeek && !previewingWeekDrag) {
            scheduleHost.setTranslationX(0f);
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
                if (interactionsSuppressed()) {
                    return true;
                }
                int day = (int) ((boardTouchX - timeWidth) / dayWidth);
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
        List<Course> visibleCourses = displayCourses(week);
        Map<Course, SlotLayout> layouts = slotLayouts(visibleCourses, week);
        List<Course> conflictingCourses = CourseConflictDetector.conflictingCoursesForWeek(
                courses, lastWeek, week);
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
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        if (width > 0) {
            return width;
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    private CharSequence courseText(Course course, boolean conflict) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (conflict) {
            String badge = "冲突 · ";
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
        view.setText("时间");
        applyAdaptiveTextColor(view, 0, 0, timeWidth, dayHeaderHeight, false);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(timeWidth, dayHeaderHeight);
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
        board.addView(header, new FrameLayout.LayoutParams(
                Math.max(getAvailableBoardWidth(), timeWidth + dayWidth * visibleDayCount),
                dayHeaderHeight));
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
        weekday.setText("周" + DAYS[actualDay]);
        weekday.setTextSize(tabletText(today ? 15 : 13, today ? 13 : 12));
        int left = timeWidth + dayWidth * column;
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
        title.setText("本周实践");
        title.setTextColor(PolarisVisualTheme.MINIMAL.equals(visualTheme)
                ? (darkMode ? color("#9CE9DF") : color("#075E56"))
                : PolarisVisualTheme.accentColor(visualTheme, darkMode));
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        heading.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = new TextView(getContext());
        count.setText(practiceCourses.size() + " 项 · 查看");
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
        banner.setContentDescription("本周实践，" + practiceCourses.size() + "项，点击查看");

        if (interactive) {
            banner.setClickable(true);
            banner.setOnClickListener(v -> {
                if (interactionsSuppressed()) {
                    return;
                }
                if (practiceCourses.size() == 1 && courseClickListener != null) {
                    courseClickListener.onCourseClick(practiceCourses.get(0));
                } else if (practiceBannerClickListener != null) {
                    practiceBannerClickListener.onPracticeBannerClick(new ArrayList<>(practiceCourses));
                }
            });
            if (practiceCourses.size() == 1) {
                banner.setOnLongClickListener(v -> {
                    if (interactionsSuppressed()) {
                        return true;
                    }
                    if (courseLongClickListener != null) {
                        courseLongClickListener.onCourseLongClick(practiceCourses.get(0));
                        return true;
                    }
                    return false;
                });
            }
        }

        int width = Math.max(getAvailableBoardWidth(), timeWidth + dayWidth * visibleDayCount);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.max(dp(120), width - dp(16)), dp(64));
        params.leftMargin = dp(8);
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
                    ? "未命名实践" : course.name.trim());
        }
        if (practiceCourses.size() == 1) {
            summary.append(" · ").append(practiceTimeText(practiceCourses.get(0)));
        } else if (practiceCourses.size() > visibleCount) {
            summary.append(" 等").append(practiceCourses.size()).append("项");
        }
        return summary.toString();
    }

    private boolean isPracticeBannerCourse(Course course) {
        return course.courseType == CourseType.PRACTICE || course.isBannerOnlyCourse();
    }

    private String practiceTimeText(Course course) {
        if (course.isBannerOnlyCourse()) {
            return "集中实践";
        }
        if (course.day >= 0 && course.day < DAYS.length) {
            return "周" + DAYS[course.day] + " " + course.startSection + "-" + course.endSection + "节";
        }
        return "时间待定";
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
        applyAdaptiveTextColor(number, 0, top, timeWidth, rowHeight / 2, false);
        number.setTextSize(getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 19 : 17);
        number.setGravity(Gravity.CENTER);
        box.addView(number);

        TextView time = new TextView(getContext());
        time.setText(section <= sectionTimes.length ? sectionTimes[section - 1] : "");
        applyAdaptiveTextColor(time, 0, top + rowHeight / 2, timeWidth, rowHeight / 2, true);
        time.setTextSize(getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 10 : 9);
        time.setGravity(Gravity.CENTER);
        box.addView(time);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(timeWidth, rowHeight);
        params.topMargin = top;
        board.addView(box, params);
    }

    private void addRowLine(FrameLayout board, int section, int week) {
        View line = new View(getContext());
        int top = sectionTop(section, week);
        line.setBackgroundColor(PolarisVisualTheme.gridLineColor(visualTheme, darkMode));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dayWidth * visibleDayCount, dp(1));
        params.leftMargin = timeWidth;
        params.topMargin = top;
        board.addView(line, params);
    }

    private int boardHeight(int week) {
        int height = dayHeaderHeight + practiceBannerInset(week);
        for (int section = 1; section <= sectionCount; section++) {
            height += sectionRowHeight(section);
        }
        return height;
    }

    private int sectionTop(int section, int week) {
        int top = dayHeaderHeight + practiceBannerInset(week);
        int bounded = Math.max(1, Math.min(sectionCount + 1, section));
        for (int index = 1; index < bounded; index++) {
            top += sectionRowHeight(index);
        }
        return top;
    }

    private int sectionRowHeight(int section) {
        if (collapseMiddleSections && (section == 5 || section == 6)) {
            return 0;
        }
        return sectionHeight;
    }

    private int sectionSpanHeight(int startSection, int endSection) {
        int start = Math.max(1, Math.min(sectionCount, startSection));
        int end = Math.max(start, Math.min(sectionCount, endSection));
        int height = 0;
        for (int section = start; section <= end; section++) {
            height += sectionRowHeight(section);
        }
        return height;
    }

    private int sectionAtY(int y, int week) {
        for (int section = 1; section <= sectionCount; section++) {
            int top = sectionTop(section, week);
            if (y >= top && y < top + sectionRowHeight(section)) {
                return section;
            }
        }
        return sectionCount + 1;
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
            view.setOnClickListener(v -> {
                if (interactionsSuppressed()) {
                    return;
                }
                if (courseClickListener != null) {
                    courseClickListener.onCourseClick(course);
                }
            });
            view.setOnLongClickListener(v -> {
                if (interactionsSuppressed()) {
                    return true;
                }
                if (courseLongClickListener != null) {
                    courseLongClickListener.onCourseLongClick(course);
                    return true;
                }
                return false;
            });
        }

        int slotCount = layout == null ? 1 : Math.max(1, layout.count);
        int slotIndex = layout == null ? 0 : Math.max(0, layout.index);
        int availableWidth = dayWidth - dp(4);
        int spanHeight = sectionSpanHeight(course.startSection, course.endSection);
        if (spanHeight <= 0) {
            return;
        }
        int availableHeight = Math.max(dp(20), spanHeight - dp(8));
        int left = timeWidth + dayWidth * column + dp(2);
        int top = sectionTop(course.startSection, week) + dp(4);
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

    private Map<Course, SlotLayout> slotLayouts(List<Course> visibleCourses, int week) {
        Map<Course, SlotLayout> layouts = new LinkedHashMap<>();
        for (Course course : visibleCourses) {
            List<Course> overlaps = new ArrayList<>();
            boolean allOutOfWeek = true;
            for (Course other : visibleCourses) {
                if (other.day == course.day
                        && other.startSection <= course.endSection
                        && other.endSection >= course.startSection) {
                    overlaps.add(other);
                    if (isCourseInWeek(other, week)) {
                        allOutOfWeek = false;
                    }
                }
            }
            boolean vertical = showOutOfWeekCourses && allOutOfWeek && overlaps.size() > 1;
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
            if (course.startSection <= candidate.endSection && course.endSection >= candidate.startSection) {
                return true;
            }
        }
        return false;
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
        if (weeks.contains("项目")) {
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
            text.append("时间冲突，");
        }
        String name = course.name == null || course.name.trim().isEmpty()
                ? "未命名课程" : course.name.trim();
        text.append(name);
        if (course.day >= 0 && course.day < DAYS.length) {
            text.append("，周").append(DAYS[course.day]);
        }
        text.append("，第").append(course.startSection)
                .append("至").append(course.endSection).append("节");
        if (course.location != null && !course.location.trim().isEmpty()) {
            text.append("，地点").append(course.location.trim());
        }
        return text.append("，点击查看详情，长按编辑").toString();
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
            total += sampleBackgroundLuminance(point[0] - getScrollX(),
                    point[1] + overlayTopInset - verticalScroll.getScrollY(), viewWidth, viewHeight);
        }
        return total / points.length;
    }

    private void updateSchedulePadding() {
        if (scheduleHost != null) {
            scheduleHost.setPadding(0, overlayTopInset, 0, overlayBottomInset);
        }
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
}
