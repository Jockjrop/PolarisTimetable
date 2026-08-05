package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
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
    private int currentWeek = 18;
    private int visibleDayCount = DAYS.length;
    private int sectionCount = TIMES.length;
    private long firstWeekStartMillis = defaultFirstWeekStartMillis();
    private boolean darkMode;
    private boolean showOutOfWeekCourses;
    private boolean hasBackgroundImage;
    private String currentBackgroundImageUri = "";
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
        setBackgroundColor(color("#EAF3FB"));
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

    public void setCurrentWeek(int week) {
        int nextWeek = Math.max(1, week);
        if (currentWeek == nextWeek) {
            return;
        }
        currentWeek = nextWeek;
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
        String[] nextTimes = generatedClassTimes(firstStartTime, classMinutes, breakMinutes,
                bigBreakMinutes, afternoonStartTime, lateAfternoonStartTime, anchorConfigText);
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
        String nextUri = uriText == null ? "" : uriText;
        if (nextUri.equals(currentBackgroundImageUri)) {
            return nextUri.isEmpty() || hasBackgroundImage;
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
                currentBackgroundDrawable = new CoverBitmapDrawable(bitmap);
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
                prepareWeekDragPreview();
                int width = Math.max(1, getDragPageWidth());
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
            if (Math.abs(dx) > threshold && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                int delta = dx < 0 ? 1 : -1;
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
        int height = Math.max(scheduleHost.getChildAt(0).getHeight(), boardHeight());
        if (width <= 0 || height <= 0) {
            return;
        }

        dragOverlay = new FrameLayout(getContext());
        dragOverlay.setClipChildren(true);
        dragOverlay.setBackgroundColor(Color.TRANSPARENT);
        dragSlider = new FrameLayout(getContext());
        FrameLayout previous = buildBoard(Math.max(1, currentWeek - 1), false);
        FrameLayout current = buildBoard(currentWeek, false);
        FrameLayout next = buildBoard(currentWeek + 1, false);
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
        int height = boardHeight();
        FrameLayout board = buildBoard(currentWeek, true);
        scheduleHost.addView(board, new LinearLayout.LayoutParams(width, height));
        if (!draggingWeek && !previewingWeekDrag) {
            scheduleHost.setTranslationX(0f);
        }
    }

    private FrameLayout buildBoard(int week, boolean interactive) {
        int width = Math.max(getAvailableBoardWidth(), timeWidth + dayWidth * visibleDayCount);
        int height = boardHeight();
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
                int section = sectionAtY(Math.round(boardTouchY));
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
        for (int section = 1; section <= sectionCount; section++) {
            if (sectionRowHeight(section) <= 0) {
                continue;
            }
            addTimeLabel(board, section);
            addRowLine(board, section);
        }
        List<Course> visibleCourses = displayCourses(week);
        Map<Course, SlotLayout> layouts = slotLayouts(visibleCourses, week);
        for (Course course : visibleCourses) {
            addCourseBlock(board, course, week, interactive, layouts.get(course));
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

    private String courseText(Course course) {
        StringBuilder text = new StringBuilder(course.name);
        if (!course.location.isEmpty()) {
            text.append("\n@").append(course.location);
        }
        if (!course.teacher.isEmpty()) {
            text.append('\n').append(course.teacher);
        }
        return text.toString();
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
        int fill = darkMode ? color("#101827") : color("#F8FBFF");
        int alpha = Math.round(255f * timetableHeaderOpacity / 100f);
        header.setBackgroundColor(Color.argb(alpha, Color.red(fill), Color.green(fill), Color.blue(fill)));
        board.addView(header, new FrameLayout.LayoutParams(
                Math.max(getAvailableBoardWidth(), timeWidth + dayWidth * visibleDayCount),
                dayHeaderHeight));
    }

    private void addDayHeader(FrameLayout board, int column, int actualDay, int week) {
        LinearLayout view = new LinearLayout(getContext());
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER);
        boolean today = isToday(actualDay, week);

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

    private void addTimeLabel(FrameLayout board, int section) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        TextView number = new TextView(getContext());
        number.setText(String.valueOf(section));
        int top = sectionTop(section);
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

    private void addRowLine(FrameLayout board, int section) {
        View line = new View(getContext());
        int top = sectionTop(section);
        line.setBackgroundColor(darkMode ? color("#31415B") : color("#D1DCEE"));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dayWidth * visibleDayCount, dp(1));
        params.leftMargin = timeWidth;
        params.topMargin = top;
        board.addView(line, params);
    }

    private int boardHeight() {
        int height = dayHeaderHeight;
        for (int section = 1; section <= sectionCount; section++) {
            height += sectionRowHeight(section);
        }
        return height;
    }

    private int sectionTop(int section) {
        int top = dayHeaderHeight;
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

    private int sectionAtY(int y) {
        for (int section = 1; section <= sectionCount; section++) {
            int top = sectionTop(section);
            if (y >= top && y < top + sectionRowHeight(section)) {
                return section;
            }
        }
        return sectionCount + 1;
    }

    private void addCourseBlock(FrameLayout board, Course course, int week, boolean interactive, SlotLayout layout) {
        int column = columnForCourse(course);
        if (column < 0) {
            return;
        }
        TextView view = new TextView(getContext());
        view.setText(courseText(course));
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.START | Gravity.TOP);
        view.setBackground(cardBg(courseColor(course)));
        if (showOutOfWeekCourses && !isCourseInWeek(course, week)) {
            view.setAlpha(0.28f);
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
        int top = sectionTop(course.startSection) + dp(4);
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

        int minimumTextSize = getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 10 : 8;
        int textSize = courseTextSize();
        int maxLines;
        while (true) {
            view.setTextSize(textSize);
            Paint.FontMetricsInt fontMetrics = view.getPaint().getFontMetricsInt();
            int lineHeight = Math.max(1, fontMetrics.descent - fontMetrics.ascent);
            int contentHeight = Math.max(1, height - view.getPaddingTop() - view.getPaddingBottom());
            maxLines = Math.max(1, Math.min(6, contentHeight / lineHeight));
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
                && sameText(first.credit, second.credit);
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

    private String[] generatedClassTimes(String firstStartTime, int classMinutes, int breakMinutes,
                                         int bigBreakMinutes, String afternoonStartTime,
                                         String lateAfternoonStartTime, String anchorConfigText) {
        String[] generated = new String[Math.max(20, TIMES.length)];
        Map<Integer, Integer> anchors = classTimeAnchors(anchorConfigText);
        int start = minutesFromText(firstStartTime);
        int duration = Math.max(1, Math.min(240, classMinutes));
        int rest = Math.max(0, Math.min(120, breakMinutes));
        int longRest = Math.max(0, Math.min(240, bigBreakMinutes));
        int afternoonStart = minutesFromText(afternoonStartTime);
        int lateAfternoonStart = minutesFromText(lateAfternoonStartTime);
        for (int i = 0; i < generated.length; i++) {
            int section = i + 1;
            Integer anchoredStart = anchors.get(i + 1);
            if (anchoredStart != null && i > 0) {
                start = anchoredStart;
            } else if (section == 5) {
                start = afternoonStart;
            } else if (section == 7) {
                start = lateAfternoonStart;
            }
            int end = start + duration;
            generated[i] = timeText(start) + "\n" + timeText(end);
            start = end + (section % 2 == 0 ? longRest : rest);
        }
        return generated;
    }

    private Map<Integer, Integer> classTimeAnchors(String configText) {
        Map<Integer, Integer> anchors = new LinkedHashMap<>();
        String text = configText == null ? "" : configText.trim();
        if (text.length() == 0 || !text.contains("-")) {
            return anchors;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d{1,2})\\s+((?:\\d{1,2})[:：](?:\\d{2}))\\s*-\\s*(?:\\d{1,2})[:：](?:\\d{2})")
                .matcher(text);
        while (matcher.find()) {
            int section = Integer.parseInt(matcher.group(1));
            if (section > 0 && section <= 40) {
                anchors.put(section, minutesFromText(matcher.group(2)));
            }
        }
        return anchors;
    }

    private int minutesFromText(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})")
                .matcher(value == null ? "" : value);
        if (matcher.find()) {
            int hour = Math.max(0, Math.min(23, Integer.parseInt(matcher.group(1))));
            int minute = Math.max(0, Math.min(59, Integer.parseInt(matcher.group(2))));
            return hour * 60 + minute;
        }
        return 8 * 60;
    }

    private String timeText(int minutes) {
        int normalized = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60);
        return twoDigits(normalized / 60) + ":" + twoDigits(normalized % 60);
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
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
            float hue = (i * 137.508f) % 360f;
            courseColors.put(names.get(i), Color.HSVToColor(new float[]{hue, 0.62f, 0.94f}));
        }
    }

    private boolean isCourseInWeek(Course course, int week) {
        String weeks = course.weeks == null ? "" : course.weeks;
        if (weeks.contains("项目")) {
            return true;
        }
        if (weeks.contains("双") && week % 2 != 0) {
            return false;
        }
        if (weeks.contains("单") && week % 2 == 0) {
            return false;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+)\\s*-\\s*(\\d+)")
                .matcher(weeks);
        if (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            int end = Integer.parseInt(matcher.group(2));
            return week >= start && week <= end;
        }
        matcher = java.util.regex.Pattern
                .compile("(^|[^\\d])(\\d+)\\s*周")
                .matcher(weeks);
        if (matcher.find()) {
            return week == Integer.parseInt(matcher.group(2));
        }
        return true;
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

    private GradientDrawable cardBg(int fill) {
        GradientDrawable drawable = new GradientDrawable();
        int alpha = Math.round(255f * courseBlockOpacity / 100f);
        drawable.setColor(Color.argb(alpha, Color.red(fill), Color.green(fill), Color.blue(fill)));
        drawable.setCornerRadius(dp(courseCornerRadius));
        return drawable;
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
        return tablet ? 13 : 10;
    }

    private int color(String hex) {
        return Color.parseColor(hex);
    }

    private int boardBgColor() {
        return darkMode ? color("#101827") : color("#EAF3FB");
    }

    private int inkColor() {
        return darkMode ? color("#EEF4FF") : color("#172033");
    }

    private int mutedColor() {
        return darkMode ? color("#9AA8BE") : color("#667085");
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
        float scale = Math.max(viewWidth / (float) bitmap.getWidth(), viewHeight / (float) bitmap.getHeight());
        float drawWidth = bitmap.getWidth() * scale;
        float drawHeight = bitmap.getHeight() * scale;
        float offsetX = (viewWidth - drawWidth) / 2f;
        float offsetY = (viewHeight - drawHeight) / 2f;
        int bitmapX = clamp(Math.round((localX - offsetX) / scale), 0, bitmap.getWidth() - 1);
        int bitmapY = clamp(Math.round((localY - offsetY) / scale), 0, bitmap.getHeight() - 1);
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
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF target = new RectF();

        CoverBitmapDrawable(Bitmap bitmap) {
            this.bitmap = bitmap;
        }

        @Override
        public void draw(Canvas canvas) {
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                return;
            }
            float width = getBounds().width();
            float height = getBounds().height();
            float scale = Math.max(width / bitmap.getWidth(), height / bitmap.getHeight());
            float drawWidth = bitmap.getWidth() * scale;
            float drawHeight = bitmap.getHeight() * scale;
            float left = getBounds().left + (width - drawWidth) / 2f;
            float top = getBounds().top + (height - drawHeight) / 2f;
            target.set(left, top, left + drawWidth, top + drawHeight);
            canvas.drawBitmap(bitmap, null, target, paint);
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
