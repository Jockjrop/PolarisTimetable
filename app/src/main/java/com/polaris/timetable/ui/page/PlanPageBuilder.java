package com.polaris.timetable.ui.page;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.polaris.timetable.R;
import com.polaris.timetable.model.AcademicEvent;
import com.polaris.timetable.model.StudyPlan;
import com.polaris.timetable.ui.DesignTokens;
import com.polaris.timetable.ui.WindowSizeClass;
import com.polaris.timetable.ui.shell.BottomNavView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 计划页构建器：手机/竖屏平板的计划 tab 页、平板横屏的计划管理浮层、
 * 课表右侧计划面板的头部与列表行，以及计划相关界面的主题刷新。
 * 纯搬代码自 MainActivity，视觉零变化；状态与外观全部经 {@link Host}
 * 按需读取。Builder 持有自己构建的视图引用（浮层、面板、列表容器、
 * 两套悬浮新增菜单），Activity 侧不再镜像这些字段。
 *
 * <p>新增入口自 1.26.0 起统一收敛到右下角悬浮加号：手机计划页与横屏平板
 * 计划管理浮层各持一个 {@link PlanAddMenuView} 实例，只共享动作路由与定位
 * 规则，不共享视图引用。
 */
public class PlanPageBuilder implements PlanAddMenuView.Host {

    /** 已完成分组折叠态：Builder 常驻，勾选/主题刷新重建列表时保持。 */
    private boolean doneCollapsed;
    /** 用户是否手动切换过折叠（切换后取消进入页面时的 3s 自动折叠）。 */
    private boolean doneTouched;
    private boolean autoCollapseScheduled;
    private final android.os.Handler autoCollapseHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingAutoCollapse;

    /** 宿主回调:由 MainActivity 实现,提供主题状态、样式辅助与计划动作。 */
    public interface Host {
        boolean isLandscapeTablet();

        int statusBarInsetPx();

        int bottomContentInsetPx();

        int contentColumnWidthPx();

        int pageSurfaceColor();

        int settingsHeaderSurfaceColor();

        int inkColor();

        int mutedColor();

        int accentColor();

        /** 强调色填充之上的前景色：由实际填充色亮度决定，不写死白色。 */
        int onAccentColor();

        String cardColorHex();

        GradientDrawable roundedBg(String hex, int radius);

        void attachPressFeedback(View view);

        void attachCardPressFeedback(View view, int radiusDp);

        View sectionHeader(String text);

        LinearLayout settingsGroup();

        void showPlanEditor(StudyPlan plan);

        void togglePlanDone(StudyPlan plan);

        String dayText(int dayOfWeek);

        String remindTimeText(int minute);

        void openPlanPage();

        /** 打开考试/DDL 时间线对话框（P4）。 */
        void openAcademicTimeline();

        /** 直接打开学业事件编辑器并预选类型（1.26.0 悬浮新增入口）。 */
        void showAcademicEventEditor(AcademicEvent.Type presetType);

        /** 底部导航的视觉高度（px，56–120dp 可配置），用于悬浮按钮避让。 */
        int navVisualHeightPx();

        /** 系统底部安全区（px，手势区/导航栏）。 */
        int systemBottomInsetPx();

        /** 系统右侧安全区（px，横屏侧边导航栏/挖孔）。 */
        int systemRightInsetPx();
    }

    private final Host host;

    // Builder 构建的共享视图：计划列表容器同时服务于手机计划页与平板浮层，
    // 由最后构建的页面持有（沿用既有行为）。
    private LinearLayout planListContainer;
    private FrameLayout planManageOverlay;
    private LinearLayout planManagePanel;
    private FrameLayout planManageFrame;
    private LinearLayout planManageContent;
    private LinearLayout planManageHeader;
    private TextView academicEntryButton;

    // 悬浮新增菜单：手机计划页与横屏平板管理浮层各一个实例，不共享视图引用。
    private PlanAddMenuView planPageMenu;
    private PlanAddMenuView planManageMenu;
    /** 计划页滚动内容（用于按悬浮按钮占用高度动态留白）。 */
    private LinearLayout planPageContent;

    public PlanPageBuilder(Host host) {
        this.host = host;
    }

    /**
     * 手机/竖屏平板：底部导航「计划」tab 的独立页面。
     * 结构为「滚动内容 + 固定悬浮新增菜单」两层，悬浮层不得进入 ScrollView，
     * 否则会跟随列表滚动。
     */
    public View buildPlanPage(Context context) {
        FrameLayout root = new FrameLayout(context);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(host.pageSurfaceColor());
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, host.statusBarInsetPx() + dp(context, 34), 0, 0);
        if (!host.isLandscapeTablet()) {
            int columnWidth = host.contentColumnWidthPx();
            if (columnWidth < context.getResources().getDisplayMetrics().widthPixels) {
                page.setLayoutParams(new ScrollView.LayoutParams(columnWidth,
                        LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            }
        }
        scrollView.addView(page);
        root.addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        planPageContent = page;

        // 考试/DDL 时间线入口（P4）：只负责浏览与管理，新增统一走悬浮菜单。
        TextView eventEntry = new TextView(context);
        eventEntry.setText(context.getString(R.string.academic_entry));
        eventEntry.setTextColor(host.accentColor());
        eventEntry.setTextSize(15);
        eventEntry.setTypeface(Typeface.DEFAULT_BOLD);
        eventEntry.setGravity(Gravity.CENTER_VERTICAL);
        eventEntry.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        eventEntry.setMinHeight(dp(context, 44));
        eventEntry.setBackground(host.roundedBg(host.cardColorHex(), 18));
        eventEntry.setOnClickListener(v -> host.openAcademicTimeline());
        host.attachPressFeedback(eventEntry);
        academicEntryButton = eventEntry;
        LinearLayout.LayoutParams entryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 44));
        entryParams.setMargins(dp(context, 12), 0, dp(context, 12), dp(context, 10));
        page.addView(eventEntry, entryParams);

        planListContainer = new LinearLayout(context);
        planListContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(planListContainer);

        planPageMenu = new PlanAddMenuView(context, this);
        root.addView(planPageMenu, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        relayoutAddMenus(context);
        return root;
    }

    /** 平板横屏：全屏遮罩 + 右侧手机宽度计划管理浮层（内容与手机计划页一致）。 */
    public void buildManageOverlay(Context context, FrameLayout rootView) {
        planManageOverlay = new FrameLayout(context);
        planManageOverlay.setBackgroundColor(0x82000000);
        planManageOverlay.setVisibility(View.GONE);
        planManageOverlay.setOnClickListener(v -> closeManagePanel(context));
        rootView.addView(planManageOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        int panelWidth = Math.min(dp(context, 380),
                context.getResources().getDisplayMetrics().widthPixels - dp(context, 24));
        // 浮层内容用 FrameLayout 承载，才能在面板之上叠加固定层悬浮菜单。
        planManageFrame = new FrameLayout(context);
        planManageFrame.setBackgroundColor(host.pageSurfaceColor());
        planManageFrame.setOnClickListener(v -> {
            // 消费点击，阻止遮罩关闭。
        });
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        planManageOverlay.addView(planManageFrame, panelParams);

        planManagePanel = new LinearLayout(context);
        planManagePanel.setOrientation(LinearLayout.VERTICAL);
        planManagePanel.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        planManageFrame.addView(planManagePanel);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, host.statusBarInsetPx() + dp(context, 8), 0, dp(context, 8));
        header.setBackgroundColor(host.settingsHeaderSurfaceColor());
        planManageHeader = header;
        TextView close = new TextView(context);
        close.setText("×");
        close.setTextColor(host.inkColor());
        close.setTextSize(30);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(context.getString(R.string.plan_overlay_close_cd));
        close.setOnClickListener(v -> closeManagePanel(context));
        header.addView(close, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 54)));
        TextView title = new TextView(context);
        title.setText(context.getString(R.string.plan_title));
        title.setTextColor(host.inkColor());
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        planManagePanel.addView(header);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), 0);
        scroll.addView(content);
        planManageContent = content;
        planManagePanel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // 考试/DDL 时间线入口（P4，与手机计划页一致）：只负责浏览与管理。
        TextView eventEntry = new TextView(context);
        eventEntry.setText(context.getString(R.string.academic_entry));
        eventEntry.setTextColor(host.accentColor());
        eventEntry.setTextSize(15);
        eventEntry.setTypeface(Typeface.DEFAULT_BOLD);
        eventEntry.setGravity(Gravity.CENTER_VERTICAL);
        eventEntry.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        eventEntry.setMinHeight(dp(context, 44));
        eventEntry.setBackground(host.roundedBg(host.cardColorHex(), 18));
        eventEntry.setOnClickListener(v -> host.openAcademicTimeline());
        host.attachPressFeedback(eventEntry);
        academicEntryButton = eventEntry;
        LinearLayout.LayoutParams entryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 44));
        entryParams.setMargins(0, 0, 0, dp(context, 10));
        content.addView(eventEntry, entryParams);

        planListContainer = new LinearLayout(context);
        planListContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(planListContainer);

        // 浮层内独立悬浮菜单：遮罩只作用于浮层内容，不遮住整个课表。
        planManageMenu = new PlanAddMenuView(context, this);
        planManageFrame.addView(planManageMenu, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        relayoutAddMenus(context);
    }

    /**
     * 按实时布局重算两套悬浮菜单的定位与滚动内容底部留白。
     * 底栏高度可配置（56–120dp）、系统安全区随旋转/分屏变化，故不使用固定值。
     */
    public void relayoutAddMenus(Context context) {
        if (planPageMenu != null) {
            int bottomOffset = pageFabBottomOffsetPx(context);
            planPageMenu.setPlacement(bottomOffset, pageFabRightOffsetPx(context),
                    host.statusBarInsetPx());
            if (planPageContent != null) {
                planPageContent.setPadding(planPageContent.getPaddingLeft(),
                        planPageContent.getPaddingTop(),
                        planPageContent.getPaddingRight(),
                        fabReserveHeightPx(context, bottomOffset));
            }
        }
        if (planManageMenu != null) {
            // 浮层自身盖住底部导航，故只避让系统底部安全区与悬浮边距。
            int bottomOffset = host.systemBottomInsetPx()
                    + dp(context, DesignTokens.NAV_FLOATING_MARGIN);
            planManageMenu.setPlacement(bottomOffset,
                    host.systemRightInsetPx()
                            + dp(context, DesignTokens.MARGIN_PAGE_TABLET)
                            + dp(context, BottomNavView.VISUAL_HORIZONTAL_INSET_DP),
                    host.statusBarInsetPx());
            if (planManageContent != null) {
                planManageContent.setPadding(planManageContent.getPaddingLeft(),
                        planManageContent.getPaddingTop(),
                        planManageContent.getPaddingRight(),
                        fabReserveHeightPx(context, bottomOffset));
            }
        }
    }

    /** 手机页 FAB 底边距：系统底部 inset + 导航悬浮边距 + 导航视觉高 + FAB 与导航间隙。 */
    private int pageFabBottomOffsetPx(Context context) {
        return host.systemBottomInsetPx()
                + dp(context, DesignTokens.NAV_FLOATING_MARGIN)
                + host.navVisualHeightPx()
                + context.getResources().getDimensionPixelSize(R.dimen.plan_fab_nav_gap);
    }

    /** 手机/竖屏平板 FAB 右边距：系统右侧安全距 + 页面边距 + 底部导航视觉内缩。 */
    private int pageFabRightOffsetPx(Context context) {
        boolean tablet = WindowSizeClass.isTablet(context.getResources().getConfiguration());
        return host.systemRightInsetPx()
                + dp(context, tablet ? DesignTokens.MARGIN_PAGE_TABLET
                        : DesignTokens.MARGIN_PAGE_PHONE)
                + dp(context, BottomNavView.VISUAL_HORIZONTAL_INSET_DP);
    }

    /** 列表末端需预留的高度：FAB 底边距 + FAB 高度 + 列表末端安全间距。 */
    private int fabReserveHeightPx(Context context, int bottomOffsetPx) {
        return bottomOffsetPx
                + context.getResources().getDimensionPixelSize(R.dimen.plan_fab_size)
                + context.getResources().getDimensionPixelSize(R.dimen.plan_list_tail_gap);
    }

    /** 任一悬浮菜单处于展开态（返回键、切页等节点据此优先收起）。 */
    public boolean isAddMenuExpanded() {
        return (planPageMenu != null && planPageMenu.isExpanded())
                || (planManageMenu != null && planManageMenu.isExpanded());
    }

    /** 收起所有悬浮菜单：单一收起入口，供切页、返回、旋转、打开编辑器等节点调用。 */
    public void collapseAddMenus() {
        if (planPageMenu != null) {
            planPageMenu.collapse();
        }
        if (planManageMenu != null) {
            planManageMenu.collapse();
        }
    }

    public boolean isManagePanelOpen() {
        return planManageOverlay != null && planManageOverlay.getVisibility() == View.VISIBLE;
    }

    public void showManagePanel(Context context, List<StudyPlan> plans) {
        if (planManageOverlay == null) {
            return;
        }
        collapseAddMenus();
        planManageOverlay.setAlpha(0f);
        planManageFrame.setTranslationX(dp(context, 380));
        planManageOverlay.setVisibility(View.VISIBLE);
        planManageFrame.setVisibility(View.VISIBLE);
        refreshList(context, plans);
        planManageOverlay.animate().alpha(1f).setDuration(200).start();
        planManageFrame.animate().translationX(0f).setDuration(200).start();
    }

    public void closeManagePanel(Context context) {
        if (planManageOverlay == null || planManageOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        collapseAddMenus();
        planManageOverlay.animate().alpha(0f).setDuration(180).start();
        planManageFrame.animate().translationX(dp(context, 380)).setDuration(180)
                .withEndAction(() -> planManageOverlay.setVisibility(View.GONE)).start();
    }

    public void refreshList(Context context, List<StudyPlan> plans) {
        lastPlans = plans == null ? Collections.emptyList() : plans;
        if (planListContainer == null || context == null) {
            return;
        }
        planListContainer.removeAllViews();
        if (plans.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(context.getString(R.string.plan_empty_hint));
            empty.setTextColor(host.mutedColor());
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(context, 42), 0, dp(context, 42));
            planListContainer.addView(empty);
            return;
        }
        List<StudyPlan> pending = new ArrayList<>();
        List<StudyPlan> finished = new ArrayList<>();
        for (StudyPlan plan : plans) {
            (plan.done ? finished : pending).add(plan);
        }
        Collections.sort(pending, (a, b) -> weekDayValue(a) - weekDayValue(b));
        Collections.sort(finished, (a, b) -> weekDayValue(b) - weekDayValue(a));
        if (!pending.isEmpty()) {
            planListContainer.addView(host.sectionHeader(context.getString(R.string.plan_section_pending)));
            LinearLayout group = host.settingsGroup();
            for (StudyPlan plan : pending) {
                group.addView(planRow(context, plan));
            }
            planListContainer.addView(group);
        }
        if (!finished.isEmpty()) {
            planListContainer.addView(doneHeader(context, finished.size()));
            if (!doneCollapsed) {
                LinearLayout group = host.settingsGroup();
                for (StudyPlan plan : finished) {
                    group.addView(planRow(context, plan));
                }
                planListContainer.addView(group);
            }
        }
    }

    /**
     * 已完成分组头：标题+数量+折叠箭头，点击切换展开/折叠（Builder 常驻态，
     * 勾选/主题刷新重建列表时保持；手动切换后取消 3s 自动折叠）。
     */
    private View doneHeader(Context context, int count) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(context, 40));
        header.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 6));
        TextView title = new TextView(context);
        title.setText(context.getString(R.string.plan_done_count, count));
        title.setTextColor(host.mutedColor());
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = new TextView(context);
        arrow.setText(doneCollapsed ? "▸" : "▾");
        arrow.setTextColor(host.mutedColor());
        arrow.setTextSize(13);
        arrow.setTypeface(Typeface.DEFAULT_BOLD);
        arrow.setPadding(dp(context, 8), 0, 0, 0);
        header.addView(arrow);
        header.setContentDescription(context.getString(doneCollapsed
                ? R.string.plan_done_cd_expand : R.string.plan_done_cd_collapse));
        header.setOnClickListener(v -> {
            doneTouched = true;
            cancelAutoCollapse();
            doneCollapsed = !doneCollapsed;
            refreshList(context, currentPlans());
        });
        host.attachPressFeedback(header);
        return header;
    }

    /** 最近一次刷新的计划列表（重建列表时复用，避免头部点击闭包持有旧数据）。 */
    private List<StudyPlan> lastPlans = Collections.emptyList();

    private List<StudyPlan> currentPlans() {
        return lastPlans;
    }

    /** 进入计划页时武装 3s 自动折叠（用户手动切换过则不再自动）。 */
    public void armDoneAutoCollapse() {
        if (doneTouched || doneCollapsed) {
            return;
        }
        cancelAutoCollapse();
        autoCollapseScheduled = true;
        pendingAutoCollapse = () -> {
            pendingAutoCollapse = null;
            autoCollapseScheduled = false;
            if (!doneTouched && !doneCollapsed) {
                doneCollapsed = true;
                refreshList(currentContext(), lastPlans);
            }
        };
        autoCollapseHandler.postDelayed(pendingAutoCollapse, 3000);
    }

    /** 离开计划页或销毁时取消自动折叠回调。 */
    public void cancelAutoCollapse() {
        if (pendingAutoCollapse != null) {
            autoCollapseHandler.removeCallbacks(pendingAutoCollapse);
            pendingAutoCollapse = null;
        }
        autoCollapseScheduled = false;
    }

    private Context currentContext() {
        return planListContainer == null ? null : planListContainer.getContext();
    }

    /** 课表右侧计划面板头部：标题 + 「管理」入口。 */
    public View buildSidePanelHeader(Context context) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        // 内容层已有 8dp 水平 padding，这里与「本周实践」面板标题的缩进保持一致。
        header.setPadding(dp(context, 6), dp(context, 10), dp(context, 6), dp(context, 4));
        TextView title = new TextView(context);
        title.setText(context.getString(R.string.side_panel_plan_title));
        title.setTextColor(host.inkColor());
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView manage = new TextView(context);
        manage.setText(context.getString(R.string.common_manage));
        manage.setTextSize(13);
        manage.setTypeface(Typeface.DEFAULT_BOLD);
        manage.setTextColor(host.accentColor());
        manage.setGravity(Gravity.CENTER_VERTICAL);
        manage.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        manage.setMinHeight(dp(context, 32));
        manage.setOnClickListener(v -> host.openPlanPage());
        header.addView(manage);
        return header;
    }

    public View planRow(Context context, StudyPlan plan) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        row.setBackground(host.roundedBg(host.cardColorHex(), 16));
        row.setOnClickListener(v -> host.showPlanEditor(plan));
        host.attachCardPressFeedback(row, 16);

        TextView check = new TextView(context);
        check.setText(plan.done ? "☑" : "☐");
        check.setTextSize(24);
        check.setGravity(Gravity.CENTER);
        check.setTextColor(plan.done ? host.accentColor() : host.mutedColor());
        check.setContentDescription(plan.done
                ? context.getString(R.string.plan_cd_mark_undone)
                : context.getString(R.string.plan_cd_mark_done));
        check.setOnClickListener(v -> host.togglePlanDone(plan));
        host.attachPressFeedback(check);
        row.addView(check, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(plan.title.length() == 0
                ? context.getString(R.string.plan_unnamed) : plan.title);
        title.setTextColor(host.inkColor());
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (plan.done) {
            title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            title.setAlpha(0.55f);
        }
        content.addView(title);

        StringBuilder meta = new StringBuilder(context.getString(R.string.plan_meta_week, plan.week))
                .append(host.dayText(plan.dayOfWeek));
        if (plan.hasCourse()) {
            meta.append(" · ").append(plan.courseName);
        }
        if (plan.hasReminder()) {
            meta.append(context.getString(R.string.plan_meta_remind, host.remindTimeText(plan.remindMinute)));
        }
        TextView metaView = new TextView(context);
        metaView.setText(meta.toString());
        metaView.setTextColor(host.mutedColor());
        metaView.setTextSize(12);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = dp(context, 2);
        content.addView(metaView, metaParams);
        row.addView(content, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView edit = new TextView(context);
        edit.setText("✎");
        edit.setTextSize(18);
        edit.setGravity(Gravity.CENTER);
        edit.setTextColor(host.mutedColor());
        edit.setContentDescription(context.getString(R.string.plan_edit));
        edit.setOnClickListener(v -> host.showPlanEditor(plan));
        host.attachPressFeedback(edit);
        row.addView(edit, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(context, 8));
        row.setLayoutParams(params);
        return row;
    }

    /**
     * 主题/深色模式切换后刷新计划相关界面的固有色：
     * 管理浮层背景、浮层头部、新建按钮底色，并重建列表行。
     * 计划 tab 页背景与右侧玻璃面板由 Activity 侧刷新链处理。
     */
    public void refreshTheme(Context context, List<StudyPlan> plans) {
        if (planManageFrame != null) {
            planManageFrame.setBackgroundColor(host.pageSurfaceColor());
        }
        if (planManageHeader != null) {
            planManageHeader.setBackgroundColor(host.settingsHeaderSurfaceColor());
        }
        if (academicEntryButton != null) {
            academicEntryButton.setBackground(host.roundedBg(host.cardColorHex(), 18));
            academicEntryButton.setTextColor(host.accentColor());
        }
        if (planPageMenu != null) {
            planPageMenu.refreshTheme(context);
        }
        if (planManageMenu != null) {
            planManageMenu.refreshTheme(context);
        }
        // 底栏高度或安全区可能随设置/旋转变化，顺带重算悬浮定位与列表留白。
        relayoutAddMenus(context);
        refreshList(context, plans);
    }

    // ===== PlanAddMenuView.Host：颜色与按压反馈直接复用页面宿主，动作在这里统一路由 =====

    @Override
    public int onAccentColor() {
        return host.onAccentColor();
    }

    @Override
    public void attachPressFeedback(View view) {
        host.attachPressFeedback(view);
    }

    @Override
    public void onPlanAddAction(PlanAddMenuView.Action action) {
        if (action == PlanAddMenuView.Action.ADD_DDL) {
            host.showAcademicEventEditor(AcademicEvent.Type.DEADLINE);
        } else if (action == PlanAddMenuView.Action.ADD_EXAM) {
            host.showAcademicEventEditor(AcademicEvent.Type.EXAM);
        } else {
            host.showPlanEditor(null);
        }
    }

    // 以下四项与 PlanPageBuilder.Host 同名同义，直接委托宿主。
    @Override
    public int accentColor() {
        return host.accentColor();
    }

    @Override
    public int inkColor() {
        return host.inkColor();
    }

    @Override
    public String cardColorHex() {
        return host.cardColorHex();
    }

    @Override
    public GradientDrawable roundedBg(String hex, int radius) {
        return host.roundedBg(hex, radius);
    }

    private static int weekDayValue(StudyPlan plan) {
        return plan.week * 7 + plan.dayOfWeek;
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
