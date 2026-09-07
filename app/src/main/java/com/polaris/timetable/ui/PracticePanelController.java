package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.polaris.timetable.Course;
import com.polaris.timetable.R;
import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * 实践侧栏控制器：承载横屏平板课表右侧实践面板的课程过滤、内容刷新与
 * 列表项构建。课程过滤为纯静态方法（无 Android 依赖）便于单测；视图与
 * 样式经 {@link Host} 按需读取，行为零变化。
 *
 * <p>抽取目标：阶段 2-3。计划面板（{@code updatePlanSidePanel} /
 * {@code layoutPlanSidePanel}）与计划页构建器耦合紧密，仍留在 MainActivity。
 */
public final class PracticePanelController {

    private final Host host;

    public PracticePanelController(Host host) {
        this.host = host;
    }

    /** 宿主回调：由 MainActivity 实现，提供视图、状态与样式/布局辅助。 */
    public interface Host {
        FrameLayout practiceSidePanel();

        LinearLayout practiceSidePanelContent();

        FrameLayout todayOverviewPanel();

        List<Course> courses();

        boolean isLandscapeTablet();

        boolean showPracticeBanner();

        boolean hasPracticePanelSpace();

        int activeTab();

        boolean settingsPageOpen();

        int practicePanelWidth();

        int dp(int value);

        int inkColor();

        int mutedColor();

        boolean isDarkModeActive();

        String cardColorHex();

        GradientDrawable roundedBg(String hex, int radius);

        Context context();

        View dialogBlurSource();

        CourseTimeResolver.Settings courseTimeSettings();

        void showCourseEditor(Course course);

        String courseTimeInlineText(Course course);

        String string(int resId);

        String string(int resId, Object... args);

        int scheduleOverlayTopInsetPx();
    }

    // ===== 纯静态逻辑（无 Android 依赖，可单测） =====

    /** 当周实践课程：实践类型或集中实践 banner，且当周有效。 */
    public static List<Course> forCurrentWeek(List<Course> courses, int currentWeek) {
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

    /** 右侧实践面板数据：课表中全部实践课程，不按当前周过滤（1.27.7）。 */
    public static List<Course> forPanel(List<Course> courses) {
        List<Course> result = new ArrayList<>();
        for (Course course : courses) {
            if (course != null && (course.courseType == CourseType.PRACTICE
                    || course.isBannerOnlyCourse())) {
                result.add(course);
            }
        }
        return result;
    }

    // ===== 视图刷新与构建 =====

    /**
     * 刷新课表右侧的实践面板（1.27.7 改回）：仅横屏平板且右侧空间充足、
     * 课表 tab 且设置面板未打开时显示；内容为全部实践课程，超高可滚动。
     * 位置排在今日概览面板下方、计划面板上方。
     */
    public void updatePracticeSidePanel() {
        FrameLayout practiceSidePanel = host.practiceSidePanel();
        if (practiceSidePanel == null) {
            return;
        }
        boolean panelEnabled = host.isLandscapeTablet() && host.showPracticeBanner()
                && host.hasPracticePanelSpace();
        boolean visible = panelEnabled && host.activeTab() == 0
                && !host.settingsPageOpen();
        List<Course> practices = panelEnabled
                ? forPanel(host.courses()) : new ArrayList<>();
        if (!visible || practices.isEmpty()) {
            practiceSidePanel.setVisibility(View.GONE);
            return;
        }
        LinearLayout practiceSidePanelContent = host.practiceSidePanelContent();
        practiceSidePanelContent.removeAllViews();

        TextView title = new TextView(host.context());
        title.setText(host.string(R.string.side_panel_practice_title));
        title.setTextColor(host.inkColor());
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(host.dp(14), host.dp(10), host.dp(14), host.dp(6));
        practiceSidePanelContent.addView(title);

        ScrollView practiceScroll = new ScrollView(host.context());
        practiceScroll.setVerticalScrollBarEnabled(practices.size() > 4);
        practiceScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout practiceList = new LinearLayout(host.context());
        practiceList.setOrientation(LinearLayout.VERTICAL);
        for (Course course : practices) {
            practiceList.addView(buildPracticePanelItem(course));
        }
        practiceScroll.addView(practiceList, new ScrollView.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        practiceSidePanelContent.addView(practiceScroll, new LinearLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                Math.min(host.dp(340), practices.size() * host.dp(66))));

        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) practiceSidePanel.getLayoutParams();
        params.width = host.practicePanelWidth();
        int topInset = host.scheduleOverlayTopInsetPx();
        FrameLayout todayOverviewPanel = host.todayOverviewPanel();
        if (todayOverviewPanel != null && todayOverviewPanel.getVisibility() == View.VISIBLE) {
            // 实践面板排在今日概览面板下方；未布局时用估算高度兜底。
            int todayBottom;
            if (todayOverviewPanel.getHeight() > 0) {
                todayBottom = todayOverviewPanel.getTop() + todayOverviewPanel.getHeight();
            } else {
                FrameLayout.LayoutParams todayParams =
                        (FrameLayout.LayoutParams) todayOverviewPanel.getLayoutParams();
                todayBottom = todayParams.topMargin + host.dp(90);
            }
            topInset = todayBottom + host.dp(10);
        }
        params.topMargin = topInset;
        params.rightMargin = host.dp(12);
        practiceSidePanel.setLayoutParams(params);
        practiceSidePanel.setVisibility(View.VISIBLE);
    }

    /** 构建单条实践课程卡片（名称 + 时间地点教师元信息）。 */
    public View buildPracticePanelItem(Course course) {
        LinearLayout card = new LinearLayout(host.context());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(host.dp(14), host.dp(8), host.dp(14), host.dp(8));
        card.setBackground(host.roundedBg(host.cardColorHex(), DesignTokens.RADIUS_CARD));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> new CourseDetailDialog(
                host.context(), host.isDarkModeActive(), host.dialogBlurSource(),
                host.courseTimeSettings())
                .show(course, host::showCourseEditor));

        TextView name = new TextView(host.context());
        name.setText(course.name == null || course.name.trim().isEmpty()
                ? host.string(R.string.board_practice_unnamed) : course.name.trim());
        name.setTextColor(host.inkColor());
        name.setTextSize(14);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name);

        StringBuilder meta = new StringBuilder(course.isBannerOnlyCourse()
                ? host.string(R.string.board_practice_concentrated)
                : host.courseTimeInlineText(course));
        if (course.location != null && !course.location.trim().isEmpty()) {
            meta.append(" · ").append(course.location.trim());
        }
        if (course.teacher != null && !course.teacher.trim().isEmpty()) {
            meta.append(host.string(R.string.practice_meta_teacher, course.teacher.trim()));
        }
        TextView metaView = new TextView(host.context());
        metaView.setText(meta.toString());
        metaView.setTextColor(host.mutedColor());
        metaView.setTextSize(13);
        metaView.setSingleLine(false);
        metaView.setMaxLines(2);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = host.dp(2);
        card.addView(metaView, metaParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(host.dp(8), 0, host.dp(8), host.dp(8));
        card.setLayoutParams(params);
        return card;
    }
}
