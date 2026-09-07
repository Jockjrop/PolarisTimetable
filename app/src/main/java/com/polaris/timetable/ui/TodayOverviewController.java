package com.polaris.timetable.ui;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.polaris.timetable.Course;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.Calendar;
import java.util.List;

/**
 * 今日概览控制器：承载顶栏内嵌今日概览的刷新、分钟级 ticker、3 秒自动
 * 折叠，以及横屏平板右侧独立今日概览面板的构建与定位。
 *
 * <p>抽取目标：阶段 2-3。Handler / ticker 生命周期由宿主在
 * {@code onPause}/{@code onDestroy} 显式停止（与迁移前一致）；折叠标记为
 * 进程级静态状态，跨 Activity 重建保留。视图引用与样式、布局辅助全部经
 * {@link Host} 按需读取，行为零变化。
 */
public final class TodayOverviewController {

    /** 冷启动展示 3 秒后自动折叠（仅进程内，进程结束后冷启动重新展开）。 */
    private static final long COLLAPSE_DELAY_MS = 3_000L;
    /** 仅存于进程内：系统杀掉应用后，下次冷启动重新展示 3 秒展开态。 */
    private static boolean collapsedForProcess;
    private static long collapseDeadline;

    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateTodayOverview();
            long untilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L) + 250L;
            handler.postDelayed(this, untilNextMinute);
        }
    };

    private final Runnable autoCollapse = new Runnable() {
        @Override
        public void run() {
            collapseDeadline = 0L;
            collapsedForProcess = true;
            applyCollapseState(true);
            host.updatePracticeTopBar();
        }
    };

    public TodayOverviewController(Host host) {
        this.host = host;
    }

    /** 宿主回调：由 MainActivity 实现，提供视图、状态与样式/布局辅助。 */
    public interface Host {
        TodayOverviewView todayOverviewView();

        LinearLayout topPanel();

        CourseConflictSummaryView conflictSummaryView();

        FrameLayout todayOverviewPanel();

        LinearLayout todayOverviewPanelContent();

        FrameLayout contentHost();

        void setTodayOverviewPanel(FrameLayout panel);

        void setTodayOverviewPanelContent(LinearLayout content);

        List<Course> courses();

        int semesterWeeks();

        CourseTimeResolver.Settings courseTimeSettings();

        long firstWeekStartMillis();

        boolean isDarkModeActive();

        boolean isLandscapeTablet();

        int activeTab();

        boolean settingsPageOpen();

        int rightPanelSpacePx();

        int bottomNavOpacityPercent();

        int statusBarHeight();

        int dp(int value);

        View glassLayer(GradientDrawable background, int radius);

        GradientDrawable floatingPanelBg(int opacityPercent, int radius);

        void updatePracticeTopBar();

        void refreshPracticeAndPlanSidePanels();
    }

    /** 冷启动时若尚未折叠且未设截止时间，则初始化 3 秒折叠截止。 */
    public void ensureCollapseDeadline() {
        if (!collapsedForProcess && collapseDeadline == 0L) {
            collapseDeadline = SystemClock.elapsedRealtime() + COLLAPSE_DELAY_MS;
        }
    }

    public boolean isCollapsedForProcess() {
        return collapsedForProcess;
    }

    /** 刷新今日概览内容（当前/下一门课 + 倒计时）。 */
    public void updateTodayOverview() {
        TodayOverviewView view = host.todayOverviewView();
        if (view == null) {
            return;
        }
        CourseTimeResolver.TodayOverview overview = CourseTimeResolver.resolveToday(
                host.courses(),
                host.courseTimeSettings(),
                host.firstWeekStartMillis(),
                host.semesterWeeks(),
                Calendar.getInstance());
        view.setOverview(overview, host.isDarkModeActive());
    }

    /** 启动分钟级 ticker（先跑一次，再按整分钟对齐延后）。 */
    public void startTodayOverviewTicker() {
        handler.removeCallbacks(ticker);
        ticker.run();
    }

    public void cancelTicker() {
        handler.removeCallbacks(ticker);
    }

    public void cancelAutoCollapse() {
        handler.removeCallbacks(autoCollapse);
    }

    /** 排程 3 秒自动折叠（已折叠则直接应用折叠态）。 */
    public void scheduleTodayOverviewAutoCollapse() {
        handler.removeCallbacks(autoCollapse);
        if (collapsedForProcess) {
            applyCollapseState(false);
            return;
        }
        long remaining = collapseDeadline - SystemClock.elapsedRealtime();
        if (remaining <= 0L) {
            autoCollapse.run();
        } else {
            handler.postDelayed(autoCollapse, remaining);
        }
    }

    public void applyCollapseState(boolean animate) {
        TodayOverviewView view = host.todayOverviewView();
        if (view == null) {
            return;
        }
        view.setCollapsed(collapsedForProcess, animate);
        applyTopPanelCollapseDensity();
        view.post(host::refreshPracticeAndPlanSidePanels);
    }

    /** 折叠后同步收紧顶栏留白，避免只隐藏一行文字却仍占用原高度。 */
    public void applyTopPanelCollapseDensity() {
        boolean compact = collapsedForProcess;
        LinearLayout topPanel = host.topPanel();
        if (topPanel != null) {
            topPanel.setPadding(host.dp(12), host.dp(compact ? 8 : 10),
                    host.dp(12), host.dp(compact ? 6 : 10));
        }
        updateTopPanelChildMargin(host.todayOverviewView(), compact ? 2 : 5);
        updateTopPanelChildMargin(host.conflictSummaryView(), compact ? 2 : 5);
        LinearLayout content = host.todayOverviewPanelContent();
        if (content != null) {
            int padding = host.dp(compact ? 6 : 10);
            content.setPadding(padding, padding, padding, padding);
        }
    }

    private void updateTopPanelChildMargin(View child, int topMarginDp) {
        LinearLayout topPanel = host.topPanel();
        if (child == null || child.getParent() != topPanel
                || !(child.getLayoutParams() instanceof LinearLayout.LayoutParams)) {
            return;
        }
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) child.getLayoutParams();
        int margin = host.dp(topMarginDp);
        if (params.topMargin != margin) {
            params.topMargin = margin;
            child.setLayoutParams(params);
        }
    }

    /** 刷新右侧顶部的今日概览独立面板：仅横屏平板且右侧空间充足、课表 tab 且设置未打开时显示。 */
    public void updateTodayOverviewPanel() {
        FrameLayout panel = host.todayOverviewPanel();
        if (panel == null) {
            return;
        }
        boolean visible = host.isLandscapeTablet()
                && host.rightPanelSpacePx() >= host.dp(DesignTokens.TABLET_SEPARATE_TODAY_MIN)
                && host.activeTab() == 0
                && !host.settingsPageOpen();
        if (!visible) {
            panel.setVisibility(View.GONE);
            return;
        }
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) panel.getLayoutParams();
        params.width = Math.min(host.dp(DesignTokens.PANEL_MAX_WIDTH), host.rightPanelSpacePx());
        // 与左侧顶栏顶部平齐。
        params.topMargin = host.statusBarHeight() + host.dp(8);
        params.rightMargin = host.dp(12);
        panel.setLayoutParams(params);
        panel.setVisibility(View.VISIBLE);
    }

    /** 构建右侧顶部的今日概览独立面板（毛玻璃容器 + 大号内容）。 */
    public View buildTodayOverviewPanel() {
        TodayOverviewView view = host.todayOverviewView();
        view.setLarge(true);
        // 毛玻璃容器（BackdropBlurView 按内容定尺寸）+ 大号今日概览内容。
        FrameLayout panel = (FrameLayout) host.glassLayer(
                host.floatingPanelBg(host.bottomNavOpacityPercent(), DesignTokens.RADIUS_SIDE_PANEL),
                DesignTokens.RADIUS_SIDE_PANEL);
        LinearLayout content = new LinearLayout(view.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int contentPadding = host.dp(collapsedForProcess ? 6 : 10);
        content.setPadding(contentPadding, contentPadding, contentPadding, contentPadding);
        content.addView(view, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        host.setTodayOverviewPanel(panel);
        host.setTodayOverviewPanelContent(content);
        host.contentHost().addView(panel, new FrameLayout.LayoutParams(
                host.dp(360), LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.TOP));
        panel.setVisibility(View.GONE);
        return panel;
    }
}
