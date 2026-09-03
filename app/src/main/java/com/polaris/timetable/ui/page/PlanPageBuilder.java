package com.polaris.timetable.ui.page;

import android.content.Context;
import android.graphics.Color;
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
import com.polaris.timetable.model.StudyPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 计划页构建器：手机/竖屏平板的计划 tab 页、平板横屏的计划管理浮层、
 * 课表右侧计划面板的头部与列表行，以及计划相关界面的主题刷新。
 * 纯搬代码自 MainActivity，视觉零变化；状态与外观全部经 {@link Host}
 * 按需读取。Builder 持有自己构建的视图引用（浮层、面板、新建按钮、
 * 列表容器），Activity 侧不再镜像这些字段。
 */
public class PlanPageBuilder {

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
    }

    private final Host host;

    // Builder 构建的共享视图：手机计划页与平板浮层共用同一组按钮与列表容器。
    private TextView planAddButton;
    private LinearLayout planListContainer;
    private FrameLayout planManageOverlay;
    private LinearLayout planManagePanel;
    private LinearLayout planManageHeader;
    private TextView academicEntryButton;

    public PlanPageBuilder(Host host) {
        this.host = host;
    }

    /** 手机/竖屏平板：底部导航「计划」tab 的独立页面。 */
    public ScrollView buildPlanPage(Context context) {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(host.pageSurfaceColor());
        LinearLayout page = new LinearLayout(context);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, host.statusBarInsetPx() + dp(context, 34), 0,
                host.bottomContentInsetPx() + dp(context, 48));
        if (!host.isLandscapeTablet()) {
            int columnWidth = host.contentColumnWidthPx();
            if (columnWidth < context.getResources().getDisplayMetrics().widthPixels) {
                page.setLayoutParams(new ScrollView.LayoutParams(columnWidth,
                        LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
            }
        }
        scrollView.addView(page);

        // 考试/DDL 时间线入口（P4）
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

        TextView addButton = new TextView(context);
        addButton.setText(context.getString(R.string.plan_action_new));
        addButton.setTextColor(Color.WHITE);
        addButton.setTextSize(15);
        addButton.setTypeface(Typeface.DEFAULT_BOLD);
        addButton.setGravity(Gravity.CENTER);
        addButton.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        addButton.setMinHeight(dp(context, 44));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(host.accentColor());
        addBg.setCornerRadius(dp(context, 18));
        addButton.setBackground(addBg);
        addButton.setOnClickListener(v -> host.showPlanEditor(null));
        host.attachPressFeedback(addButton);
        planAddButton = addButton;
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 44));
        addParams.setMargins(dp(context, 12), 0, dp(context, 12), dp(context, 6));
        page.addView(addButton, addParams);

        planListContainer = new LinearLayout(context);
        planListContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(planListContainer);
        return scrollView;
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
        planManagePanel = new LinearLayout(context);
        planManagePanel.setOrientation(LinearLayout.VERTICAL);
        planManagePanel.setBackgroundColor(host.pageSurfaceColor());
        planManagePanel.setOnClickListener(v -> {
            // 消费点击，阻止遮罩关闭。
        });
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        planManageOverlay.addView(planManagePanel, panelParams);

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
        content.setPadding(dp(context, 12), dp(context, 8), dp(context, 12),
                host.bottomContentInsetPx() + dp(context, 48));
        scroll.addView(content);
        planManagePanel.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView addButton = new TextView(context);
        addButton.setText(context.getString(R.string.plan_action_new));
        addButton.setTextColor(Color.WHITE);
        addButton.setTextSize(15);
        addButton.setTypeface(Typeface.DEFAULT_BOLD);
        addButton.setGravity(Gravity.CENTER);
        addButton.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        addButton.setMinHeight(dp(context, 44));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(host.accentColor());
        addBg.setCornerRadius(dp(context, 18));
        addButton.setBackground(addBg);
        addButton.setOnClickListener(v -> host.showPlanEditor(null));
        host.attachPressFeedback(addButton);
        planAddButton = addButton;
        content.addView(addButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 44)));

        // 考试/DDL 时间线入口（P4，与手机计划页一致）
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
        entryParams.setMargins(0, dp(context, 10), 0, dp(context, 10));
        content.addView(eventEntry, entryParams);

        planListContainer = new LinearLayout(context);
        planListContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(planListContainer);
    }

    public boolean isManagePanelOpen() {
        return planManageOverlay != null && planManageOverlay.getVisibility() == View.VISIBLE;
    }

    public void showManagePanel(Context context, List<StudyPlan> plans) {
        if (planManageOverlay == null) {
            return;
        }
        planManageOverlay.setAlpha(0f);
        planManagePanel.setTranslationX(dp(context, 380));
        planManageOverlay.setVisibility(View.VISIBLE);
        planManagePanel.setVisibility(View.VISIBLE);
        refreshList(context, plans);
        planManageOverlay.animate().alpha(1f).setDuration(200).start();
        planManagePanel.animate().translationX(0f).setDuration(200).start();
    }

    public void closeManagePanel(Context context) {
        if (planManageOverlay == null || planManageOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        planManageOverlay.animate().alpha(0f).setDuration(180).start();
        planManagePanel.animate().translationX(dp(context, 380)).setDuration(180)
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
        if (planManagePanel != null) {
            planManagePanel.setBackgroundColor(host.pageSurfaceColor());
        }
        if (planManageHeader != null) {
            planManageHeader.setBackgroundColor(host.settingsHeaderSurfaceColor());
        }
        if (planAddButton != null) {
            GradientDrawable addBg = new GradientDrawable();
            addBg.setColor(host.accentColor());
            addBg.setCornerRadius(dp(context, 18));
            planAddButton.setBackground(addBg);
        }
        if (academicEntryButton != null) {
            academicEntryButton.setBackground(host.roundedBg(host.cardColorHex(), 18));
            academicEntryButton.setTextColor(host.accentColor());
        }
        refreshList(context, plans);
    }

    private static int weekDayValue(StudyPlan plan) {
        return plan.week * 7 + plan.dayOfWeek;
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
}
