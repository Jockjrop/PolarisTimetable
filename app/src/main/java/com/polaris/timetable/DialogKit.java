package com.polaris.timetable;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.polaris.timetable.ui.DesignTokens;
import com.polaris.timetable.ui.dialog.DialogWindowHelper;
import com.polaris.timetable.ui.dialog.GlassDialogFactory;

/**
 * 对话框基础设施：玻璃拟态面板、动作按钮、透明化与主题上下文的统一封装。
 * 由 MainActivity 提供主题快照与取样源视图，不持有独立状态。
 * 阶段 2-1 从 MainActivity 抽出的公用对话框部件。
 */
public class DialogKit {

    protected final MainActivity host;

    public DialogKit(MainActivity host) {
        this.host = host;
    }

    /** 布尔值回写回调（阶段 2-1 从 MainActivity 迁入，同包裸引用兼容）。 */
    public interface BooleanSetter {
        void set(boolean value);
    }

    /** 字符串回写回调。 */
    public interface StringSetter {
        void set(String value);
    }

    /** 整数回写回调。 */
    public interface IntSetter {
        void set(int value);
    }

    /** 长整数回写回调（日期毫秒等）。 */
    public interface LongSetter {
        void set(long value);
    }

    public LinearLayout dialogPanel(String titleText) {
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        panel.setPadding(host.dp(18), host.dp(18), host.dp(18), host.dp(18));
        panel.setMinimumWidth(host.dp(292));
        panel.setBackground(host.roundedBg(host.cardColorHex(), DesignTokens.RADIUS_DIALOG_SHEET));
        TextView titleView = new TextView(host);
        titleView.setText(titleText);
        titleView.setTextColor(host.inkColor());
        titleView.setTextSize(20);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setPadding(0, 0, 0, host.dp(10));
        panel.addView(titleView);
        return panel;
    }

    /**
     * 与 MainActivity 原版 dialogAction 保持一致的视觉（17sp / 48dp / 6dp 边距），
     * 保证搬移后的对话框按钮渲染不变。
     */
    public TextView dialogAction(String text, View.OnClickListener listener) {
        TextView item = new TextView(host);
        item.setText(text);
        item.setGravity(android.view.Gravity.CENTER);
        item.setTextSize(17);
        item.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        item.setTextColor(host.inkColor());
        item.setBackground(host.roundedBg(host.cardColorHex(), DesignTokens.RADIUS_CARD));
        item.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, host.dp(48));
        params.setMargins(0, host.dp(6), 0, host.dp(6));
        item.setLayoutParams(params);
        return item;
    }

    public TextView compactDialogAction(String text, View.OnClickListener listener) {
        TextView item = new TextView(host);
        item.setText(text);
        item.setGravity(android.view.Gravity.CENTER);
        item.setTextSize(14);
        item.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        item.setTextColor(host.inkColor());
        item.setBackground(host.roundedBg(host.cardColorHex(), DesignTokens.RADIUS_CHIP));
        item.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, host.dp(40), 1f);
        params.setMargins(host.dp(3), host.dp(8), host.dp(3), host.dp(4));
        item.setLayoutParams(params);
        return item;
    }

    public View glassDialogContent(LinearLayout panel, int radius) {
        return glassDialogContent(panel, radius, -1);
    }

    public View glassDialogContent(LinearLayout panel, int radius, int opacityPercent) {
        return GlassDialogFactory.dialogContent(panel, glassConfig(),
                dialogBlurSource(), radius, opacityPercent);
    }

    public View glassDialogContent(ScrollView scrollView, LinearLayout panel, int radius) {
        return GlassDialogFactory.dialogContent(scrollView, glassConfig(),
                dialogBlurSource(), radius);
    }

    public View dialogBlurSource() {
        if (!host.scheduleViewState.shellBarsBlurEnabled || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            return null;
        }
        return host.rootView != null ? host.rootView : host.getWindow().getDecorView();
    }

    public GlassDialogFactory.Config glassConfig() {
        return new GlassDialogFactory.Config(host, host.scheduleViewState.shellBarsBlurEnabled, host.isDarkModeActive(),
                host.isMinimalVisualTheme(), host.scheduleViewState.visualTheme);
    }

    public GradientDrawable dialogGlassBg(int radius) {
        return GlassDialogFactory.dialogGlassBg(glassConfig(), radius);
    }

    public GradientDrawable dialogGlassBg(int radius, int opacityPercent) {
        return GlassDialogFactory.dialogGlassBg(glassConfig(), radius, opacityPercent);
    }

    public void transparentDialog(Dialog dialog) {
        DialogWindowHelper.transparentDialog(dialog, host.isDarkModeActive(), host);
    }

    /**
     * 横屏平板：透明化之外再把对话框靠左放置，与左侧空状态「导入课表」
     * 卡片同侧（1.27.7 导入弹窗同步居左）；其他场景仍走居中的 transparentDialog。
     */
    public void transparentDialogLeft(Dialog dialog) {
        transparentDialog(dialog);
        if (com.polaris.timetable.ui.WindowSizeClass.isLandscapeTablet(host)) {
            DialogWindowHelper.alignDialogStart(dialog.getWindow(), host);
        }
    }

    public void makeDialogStill(Window window) {
        DialogWindowHelper.makeDialogStill(window);
    }

    public Context themedControlContext() {
        int theme = host.isDarkModeActive()
                ? android.R.style.Theme_Material
                : android.R.style.Theme_Material_Light;
        return new android.view.ContextThemeWrapper(host, theme);
    }
}
