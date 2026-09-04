package com.polaris.timetable.ui.page;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;

import com.polaris.timetable.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 计划页悬浮新增菜单：右下角主 FAB + 向上展开的三项动作行（新建计划 / 添加DDL / 添加考试）。
 *
 * <p>本视图只负责布局、展开收起动效与无障碍；颜色、按压反馈、动作路由与定位数值
 * 全部由 {@link Host} 提供。手机计划页与横屏平板计划管理浮层各持一个实例，
 * 只共享配置与回调，不共享视图引用。
 *
 * <p>展开语义不能只靠旋转或颜色表达：FAB 的 contentDescription 会随状态切换，
 * 并通过 TalkBack 播报展开/收起。
 */
// ViewConstructor：本视图只以 (Context, Host) 编程构造（需 Host 提供主题与回调），
// 从不作为 XML 标签膨胀，(Context)/(Context, AttributeSet) 构造器无真实用途。
@android.annotation.SuppressLint("ViewConstructor")
public final class PlanAddMenuView extends FrameLayout {

    /** 悬浮菜单支持的三种新增动作。 */
    public enum Action { NEW_PLAN, ADD_DDL, ADD_EXAM }

    /** 宿主回调：主题取色、按压反馈、动作路由。 */
    public interface Host {
        int accentColor();

        /** 强调色填充之上的前景色（按亮度选择深/浅，不写死白色）。 */
        int onAccentColor();

        int inkColor();

        String cardColorHex();

        GradientDrawable roundedBg(String hex, int radiusDp);

        void attachPressFeedback(View view);

        void onPlanAddAction(Action action);
    }

    private static final long DURATION_EXPAND_MS = 180L;
    private static final long DURATION_COLLAPSE_MS = 140L;
    private static final long STAGGER_MS = 28L;
    /** 遮罩强度：低强度浅遮罩，不使用重黑遮罩。 */
    private static final float SCRIM_ALPHA = 0.10f;
    private static final float ROW_COLLAPSED_SCALE = 0.96f;

    private final Host host;
    private final View scrim;
    private final LinearLayout menuColumn;
    private final FrameLayout fab;
    private final ImageView fabIcon;
    private final GradientDrawable fabBackground;
    /** 无障碍遍历顺序：新建计划 → 添加DDL → 添加考试（与视觉自下而上的顺序一致）。 */
    private final List<LinearLayout> rowsInTraversalOrder = new ArrayList<>();

    private boolean expanded;
    private int bottomOffsetPx;
    private int rightOffsetPx;
    private int scrimTopInsetPx;

    public PlanAddMenuView(Context context, Host host) {
        super(context);
        this.host = host;
        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        scrim = new View(context);
        scrim.setBackgroundColor(Color.BLACK);
        scrim.setAlpha(0f);
        scrim.setVisibility(View.GONE);
        scrim.setContentDescription(context.getString(R.string.plan_add_menu_scrim_cd));
        // 遮罩只作为点按收起的触摸层，不进无障碍树：TalkBack 用返回键或再次点击 FAB 收起。
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scrim.setOnClickListener(v -> collapse());
        addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        menuColumn = new LinearLayout(context);
        menuColumn.setOrientation(LinearLayout.VERTICAL);
        menuColumn.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END));
        addView(menuColumn);

        // 视觉自下而上为 新建计划 / 添加DDL / 添加考试，故布局自上而下按相反顺序添加。
        LinearLayout examRow = buildRow(Action.ADD_EXAM, R.drawable.ic_plan_exam,
                R.string.plan_add_action_exam, true);
        LinearLayout ddlRow = buildRow(Action.ADD_DDL, R.drawable.ic_plan_deadline,
                R.string.plan_add_action_deadline, true);
        LinearLayout planRow = buildRow(Action.NEW_PLAN, R.drawable.ic_plan_item,
                R.string.plan_add_action_plan, false);
        menuColumn.addView(examRow);
        menuColumn.addView(ddlRow);
        menuColumn.addView(planRow);

        fabBackground = new GradientDrawable();
        fabBackground.setCornerRadius(dimen(R.dimen.plan_fab_size) / 2f);
        fabBackground.setColor(host.accentColor());
        fab = new FrameLayout(context);
        fab.setId(View.generateViewId());
        fab.setLayoutParams(new FrameLayout.LayoutParams(
                dimen(R.dimen.plan_fab_size), dimen(R.dimen.plan_fab_size),
                Gravity.BOTTOM | Gravity.END));
        fab.setBackground(fabBackground);
        fab.setContentDescription(context.getString(R.string.plan_fab_cd_collapsed));
        fab.setOnClickListener(v -> setExpanded(!expanded));
        fabIcon = new ImageView(context);
        fabIcon.setImageDrawable(tinted(context, R.drawable.ic_plan_add, host.onAccentColor()));
        int iconSize = dimen(R.dimen.plan_fab_icon_size);
        fab.addView(fabIcon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        host.attachPressFeedback(fab);
        addView(fab);

        rowsInTraversalOrder.add(planRow);
        rowsInTraversalOrder.add(ddlRow);
        rowsInTraversalOrder.add(examRow);
        bindTraversalOrder();

        applyPlacement();
        applyCollapsedState(false, false);
    }

    /**
     * 定位：由宿主按实时布局计算后下发，不使用固定值，避免自定义底栏高度时重叠。
     *
     * @param bottomOffsetPx  FAB 底边距 = 系统底部 inset + 导航悬浮边距 + 导航视觉高 + FAB 与导航间隙
     * @param rightOffsetPx   FAB 右边距 = 系统右侧安全距 + 页面边距 + 导航视觉内缩
     * @param scrimTopInsetPx 遮罩顶部留出的系统栏高度，避免给状态栏区域上色
     */
    public void setPlacement(int bottomOffsetPx, int rightOffsetPx, int scrimTopInsetPx) {
        this.bottomOffsetPx = bottomOffsetPx;
        this.rightOffsetPx = rightOffsetPx;
        this.scrimTopInsetPx = scrimTopInsetPx;
        applyPlacement();
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** 收起菜单（唯一收起入口，所有生命周期与交互节点都走这里）。 */
    public void collapse() {
        setExpanded(false);
    }

    /** 展开或收起菜单；状态相同则无操作。 */
    public void setExpanded(boolean expand) {
        if (expand == expanded) {
            return;
        }
        expanded = expand;
        if (expand) {
            applyExpandedState(animationsEnabled());
        } else {
            applyCollapsedState(animationsEnabled(), true);
        }
    }

    /** 主题或深色模式切换后刷新固有色，不重建视图。 */
    public void refreshTheme(Context context) {
        fabBackground.setColor(host.accentColor());
        fabIcon.setImageDrawable(tinted(context, R.drawable.ic_plan_add, host.onAccentColor()));
        for (LinearLayout row : rowsInTraversalOrder) {
            row.setBackground(host.roundedBg(host.cardColorHex(), menuRowRadiusDp()));
            ImageView icon = row.findViewById(R.id.plan_add_menu_icon);
            TextView label = row.findViewById(R.id.plan_add_menu_label);
            if (icon != null && icon.getTag() instanceof Integer) {
                icon.setImageDrawable(tinted(context, (Integer) icon.getTag(),
                        host.accentColor()));
            }
            if (label != null) {
                label.setTextColor(host.inkColor());
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // 脱离窗口（旋转重建、页面销毁）时结束动画，避免遮罩或菜单项残留在旧视图上。
        for (LinearLayout row : rowsInTraversalOrder) {
            row.animate().cancel();
        }
        scrim.animate().cancel();
        fabIcon.animate().cancel();
        super.onDetachedFromWindow();
    }

    private void applyPlacement() {
        FrameLayout.LayoutParams fabParams = (FrameLayout.LayoutParams) fab.getLayoutParams();
        fabParams.bottomMargin = bottomOffsetPx;
        fabParams.rightMargin = rightOffsetPx;
        fab.setLayoutParams(fabParams);

        FrameLayout.LayoutParams columnParams =
                (FrameLayout.LayoutParams) menuColumn.getLayoutParams();
        columnParams.bottomMargin = bottomOffsetPx
                + dimen(R.dimen.plan_fab_size) + dimen(R.dimen.plan_fab_menu_gap);
        columnParams.rightMargin = rightOffsetPx;
        menuColumn.setLayoutParams(columnParams);

        FrameLayout.LayoutParams scrimParams = (FrameLayout.LayoutParams) scrim.getLayoutParams();
        scrimParams.topMargin = scrimTopInsetPx;
        scrim.setLayoutParams(scrimParams);
    }

    private void applyExpandedState(boolean animate) {
        scrim.animate().cancel();
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(SCRIM_ALPHA).setDuration(animate ? DURATION_EXPAND_MS : 0L).start();
        for (int i = 0; i < rowsInTraversalOrder.size(); i++) {
            LinearLayout row = rowsInTraversalOrder.get(i);
            row.animate().cancel();
            row.setVisibility(View.VISIBLE);
            row.setAlpha(0f);
            row.setTranslationY(dimen(R.dimen.plan_add_menu_row_gap));
            row.setScaleX(ROW_COLLAPSED_SCALE);
            row.setScaleY(ROW_COLLAPSED_SCALE);
            row.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setStartDelay(animate ? i * STAGGER_MS : 0L)
                    .setDuration(animate ? DURATION_EXPAND_MS : 0L)
                    .start();
        }
        fabIcon.animate().cancel();
        fabIcon.animate().rotation(45f).setDuration(animate ? DURATION_EXPAND_MS : 0L).start();
        fab.setContentDescription(getContext().getString(R.string.plan_fab_cd_expanded));
        announceForAccessibility(
                getContext().getString(R.string.plan_add_menu_expanded_announce));
        // 展开后把无障碍焦点交给最靠近 FAB 的高频项。
        rowsInTraversalOrder.get(0).sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private void applyCollapsedState(boolean animate, boolean announce) {
        scrim.animate().cancel();
        scrim.animate()
                .alpha(0f)
                .setDuration(animate ? DURATION_COLLAPSE_MS : 0L)
                .withEndAction(() -> scrim.setVisibility(View.GONE))
                .start();
        for (LinearLayout row : rowsInTraversalOrder) {
            row.animate().cancel();
            row.animate()
                    .alpha(0f)
                    .translationY(dimen(R.dimen.plan_add_menu_row_gap))
                    .scaleX(ROW_COLLAPSED_SCALE)
                    .scaleY(ROW_COLLAPSED_SCALE)
                    .setDuration(animate ? DURATION_COLLAPSE_MS : 0L)
                    .withEndAction(() -> {
                        if (expanded) {
                            return;
                        }
                        row.setVisibility(View.GONE);
                        row.setTranslationY(0f);
                        row.setScaleX(1f);
                        row.setScaleY(1f);
                    })
                    .start();
        }
        fabIcon.animate().cancel();
        fabIcon.animate().rotation(0f).setDuration(animate ? DURATION_COLLAPSE_MS : 0L).start();
        fab.setContentDescription(getContext().getString(R.string.plan_fab_cd_collapsed));
        if (announce) {
            announceForAccessibility(
                    getContext().getString(R.string.plan_add_menu_collapsed_announce));
        }
        fab.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    /**
     * 构造一个动作行：矢量图标 + 文字的胶囊，最小高度 48dp，文字随系统字号缩放。
     *
     * @param hasRowBelow 该行下方还有菜单项时需要补 8dp 间距
     */
    private LinearLayout buildRow(Action action, @DrawableRes int iconRes,
                                  @StringRes int labelRes, boolean hasRowBelow) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setId(View.generateViewId());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dimen(R.dimen.plan_add_menu_row_min_height));
        int paddingH = dimen(R.dimen.plan_add_menu_row_padding_h);
        row.setPadding(paddingH, 0, paddingH, 0);
        row.setBackground(host.roundedBg(host.cardColorHex(), menuRowRadiusDp()));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            collapse();
            host.onPlanAddAction(action);
        });

        ImageView icon = new ImageView(context);
        icon.setId(R.id.plan_add_menu_icon);
        icon.setImageDrawable(tinted(context, iconRes, host.accentColor()));
        icon.setTag(iconRes);
        // 图标是装饰，行内已有文字标签；保留空描述以满足 ImageView 的无障碍要求。
        icon.setContentDescription("");
        int iconSize = dimen(R.dimen.plan_fab_icon_size);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.rightMargin = dimen(R.dimen.plan_add_menu_row_icon_gap);
        row.addView(icon, iconParams);

        TextView label = new TextView(context);
        label.setId(R.id.plan_add_menu_label);
        label.setText(context.getString(labelRes));
        label.setTextColor(host.inkColor());
        label.setTextSize(15);
        label.setSingleLine(true);
        row.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (hasRowBelow) {
            params.bottomMargin = dimen(R.dimen.plan_add_menu_row_gap);
        }
        row.setLayoutParams(params);
        host.attachPressFeedback(row);
        return row;
    }

    /** 无障碍遍历顺序：主 FAB → 新建计划 → 添加DDL → 添加考试。 */
    private void bindTraversalOrder() {
        LinearLayout planRow = rowsInTraversalOrder.get(0);
        LinearLayout ddlRow = rowsInTraversalOrder.get(1);
        LinearLayout examRow = rowsInTraversalOrder.get(2);
        planRow.setAccessibilityTraversalAfter(fab.getId());
        ddlRow.setAccessibilityTraversalAfter(planRow.getId());
        examRow.setAccessibilityTraversalAfter(ddlRow.getId());
    }

    /** 系统关闭动画时立即完成状态切换，不保留延迟。 */
    private boolean animationsEnabled() {
        return Settings.Global.getFloat(getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
    }

    private int dimen(int resId) {
        return getResources().getDimensionPixelSize(resId);
    }

    /** 胶囊行圆角（dp）：取行高一半，保证胶囊两端为半圆。 */
    private int menuRowRadiusDp() {
        return Math.round(dimen(R.dimen.plan_add_menu_row_radius)
                / getResources().getDisplayMetrics().density);
    }

    /** 生成按指定颜色着色的矢量图标副本，避免污染资源缓存中的共享实例。 */
    private static Drawable tinted(Context context, @DrawableRes int resId, int color) {
        Drawable drawable = AppCompatResources.getDrawable(context, resId);
        if (drawable == null) {
            return null;
        }
        Drawable wrapped = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(wrapped, color);
        return wrapped;
    }
}
