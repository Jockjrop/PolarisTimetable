package com.polaris.timetable.ui.shell;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.polaris.timetable.ui.dialog.GlassDialogFactory;

/**
 * 底部导航视图:手机竖屏为课表/计划/我的三 tab 悬浮条,
 * 横屏平板为课表/我的两 tab 居中限宽条。
 * 外观与状态全部经 {@link Host} 按需读取,视图自身不持有可变配置。
 */
public class BottomNavView extends LinearLayout {

    /** BottomNavView 外框到实际玻璃导航卡片的水平距离。 */
    public static final int VISUAL_HORIZONTAL_INSET_DP = 10;

    /** 宿主回调:由 MainActivity 实现,提供外观参数、状态与交互。 */
    public interface Host {
        boolean isLandscapeTablet();

        boolean navDarkMode();

        boolean navMinimalTheme();

        String navVisualTheme();

        boolean navBlurEnabled();

        int navOpacity();

        int navRadius();

        int navHeight();

        int navBottomInset();

        int navInkColor();

        int navMutedColor();

        boolean navTabActive(int tab);

        void onNavTabSelected(int tab);

        View navContentSource();

        CharSequence navLabel(int tab, boolean active);

        void attachNavPressFeedback(View item);
    }

    private final Host host;
    private TextView scheduleNav;
    private TextView planNav;
    private TextView myNav;

    public BottomNavView(Context context, Host host) {
        super(context);
        this.host = host;
        setOrientation(LinearLayout.HORIZONTAL);
        setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        if (host.isLandscapeTablet()) {
            buildTabletBar();
        } else {
            buildPhoneBar();
        }
    }

    public BottomNavView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.host = null;
    }

    private void buildPhoneBar() {
        setPadding(dp(VISUAL_HORIZONTAL_INSET_DP), 0,
                dp(VISUAL_HORIZONTAL_INSET_DP), 0);
        setBackground(null);
        scheduleNav = navItem(0);
        planNav = navItem(1);
        myNav = navItem(2);
        scheduleNav.setBackgroundColor(Color.TRANSPARENT);
        planNav.setBackgroundColor(Color.TRANSPARENT);
        myNav.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout container = new FrameLayout(getContext());
        container.addView(glassLayer(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(10), 0, dp(10), 0);
        row.addView(scheduleNav);
        row.addView(planNav);
        row.addView(myNav);
        container.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(host.navHeight())));
    }

    private void buildTabletBar() {
        scheduleNav = navItem(0);
        myNav = navItem(2);
        scheduleNav.setPadding(0, 0, 0, 0);
        myNav.setPadding(0, 0, 0, 0);
        FrameLayout container = new FrameLayout(getContext());
        container.addView(glassLayer(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(dp(10), 0, dp(10), 0);
        row.addView(scheduleNav);
        row.addView(myNav);
        container.addView(row, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(host.navHeight())));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView navItem(int tab) {
        TextView item = new TextView(getContext());
        item.setText(host.navLabel(tab, host.navTabActive(tab)));
        item.setGravity(Gravity.CENTER);
        item.setTextSize(14);
        item.setLineSpacing(0f, 0.92f);
        boolean active = host.navTabActive(tab);
        item.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        item.setTextColor(active ? host.navInkColor() : host.navMutedColor());
        item.setOnClickListener(v -> host.onNavTabSelected(tab));
        host.attachNavPressFeedback(item);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        item.setLayoutParams(params);
        return item;
    }

    private View glassLayer() {
        GlassDialogFactory.Config cfg = new GlassDialogFactory.Config(
                getContext(), host.navBlurEnabled(), host.navDarkMode(),
                host.navMinimalTheme(), host.navVisualTheme());
        return GlassDialogFactory.glassLayer(cfg, host.navContentSource(),
                GlassDialogFactory.floatingPanelBg(cfg, host.navOpacity(), host.navRadius()),
                host.navRadius());
    }

    /** tab 状态变化后更新三个入口的文本与字重。 */
    public void updateTabs(boolean schedule, boolean plan, boolean mine) {
        if (scheduleNav != null) {
            scheduleNav.setText(host.navLabel(0, schedule));
            scheduleNav.setTypeface(schedule ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (planNav != null) {
            planNav.setText(host.navLabel(1, plan));
            planNav.setTypeface(plan ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (myNav != null) {
            myNav.setText(host.navLabel(2, mine));
            myNav.setTypeface(mine ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    /** 主题/明暗变化后刷新入口颜色。 */
    public void applyTabColors(boolean scheduleActive, boolean mineActive) {
        if (scheduleNav != null) {
            scheduleNav.setTextColor(scheduleActive ? host.navInkColor() : host.navMutedColor());
        }
        if (myNav != null) {
            myNav.setTextColor(mineActive ? host.navInkColor() : host.navMutedColor());
        }
    }
}
