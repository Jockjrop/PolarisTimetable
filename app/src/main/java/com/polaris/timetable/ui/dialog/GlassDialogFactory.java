package com.polaris.timetable.ui.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.polaris.timetable.ui.PolarisVisualTheme;
import com.polaris.timetable.ui.BackdropBlurView;

/**
 * 对话框与壳层毛玻璃容器的统一工厂。
 * 全部方法显式接收 {@link Config} 快照与取样源视图,不缓存任何运行时状态,
 * 便于设置项(模糊开关、深色模式、主题)即时生效。
 */
public final class GlassDialogFactory {

    /** 毛玻璃外观快照:由调用方在每次构建时读取当前设置生成。 */
    public static final class Config {
        public final Context context;
        public final boolean blurEnabled;
        public final boolean darkMode;
        public final boolean minimalTheme;
        public final String visualTheme;

        public Config(Context context, boolean blurEnabled, boolean darkMode,
                      boolean minimalTheme, String visualTheme) {
            this.context = context;
            this.blurEnabled = blurEnabled;
            this.darkMode = darkMode;
            this.minimalTheme = minimalTheme;
            this.visualTheme = visualTheme;
        }

        public int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }

    private GlassDialogFactory() {
    }

    private static boolean realBlurEnabled(Config cfg) {
        return cfg.blurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /** 对话框玻璃容器:包裹 LinearLayout 面板。 */
    public static View dialogContent(LinearLayout panel, Config cfg, View blurSource,
                                     int radius, int opacityPercent) {
        if (!realBlurEnabled(cfg)) {
            return panel;
        }
        panel.setBackgroundColor(Color.TRANSPARENT);
        BackdropBlurView glass = new BackdropBlurView(cfg.context);
        glass.setSourceView(blurSource);
        glass.setGlassBackground(dialogGlassBg(cfg, radius, opacityPercent), cfg.dp(radius));
        glass.setBlurEnabled(true, cfg.dp(22));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        glass.addView(panel, params);
        return glass;
    }

    /** 对话框玻璃容器:包裹可滚动内容。 */
    public static View dialogContent(ScrollView scrollView, Config cfg, View blurSource, int radius) {
        if (!realBlurEnabled(cfg)) {
            return scrollView;
        }
        scrollView.setBackgroundColor(Color.TRANSPARENT);
        BackdropBlurView glass = new BackdropBlurView(cfg.context);
        glass.setSourceView(blurSource);
        glass.setGlassBackground(dialogGlassBg(cfg, radius), cfg.dp(radius));
        glass.setBlurEnabled(true, cfg.dp(22));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        glass.addView(scrollView, params);
        return glass;
    }

    public static GradientDrawable dialogGlassBg(Config cfg, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        if (cfg.darkMode) {
            drawable.setColor(Color.argb(142, 24, 34, 53));
            drawable.setStroke(cfg.dp(1), Color.argb(85, 255, 255, 255));
        } else {
            drawable.setColor(Color.argb(172, 248, 251, 255));
            drawable.setStroke(cfg.dp(1), Color.argb(150, 255, 255, 255));
        }
        drawable.setCornerRadius(cfg.dp(radius));
        return drawable;
    }

    public static GradientDrawable dialogGlassBg(Config cfg, int radius, int opacityPercent) {
        GradientDrawable drawable = new GradientDrawable();
        int boundedOpacity = Math.max(0, Math.min(100, opacityPercent));
        int alpha = Math.round(255f * boundedOpacity / 100f);
        if (cfg.darkMode) {
            drawable.setColor(Color.argb(alpha, 24, 34, 53));
            drawable.setStroke(cfg.dp(1), Color.argb(85, 255, 255, 255));
        } else {
            drawable.setColor(Color.argb(alpha, 248, 251, 255));
            drawable.setStroke(cfg.dp(1), Color.argb(150, 255, 255, 255));
        }
        drawable.setCornerRadius(cfg.dp(radius));
        return drawable;
    }

    public static GradientDrawable liquidGlassBg(Config cfg, int opacityPercent) {
        return floatingPanelBg(cfg, opacityPercent, 24);
    }

    /** 壳层毛玻璃面板背景(顶栏/侧板/底部导航)。 */
    public static GradientDrawable floatingPanelBg(Config cfg, int opacityPercent, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        int boundedOpacity = Math.max(40, Math.min(100, opacityPercent));
        boolean realBlur = realBlurEnabled(cfg);
        int alphaPercent;
        if (realBlur && cfg.darkMode) {
            alphaPercent = Math.round(32f + boundedOpacity * 0.42f);
        } else if (realBlur) {
            alphaPercent = Math.round(26f + boundedOpacity * 0.40f);
        } else {
            alphaPercent = boundedOpacity;
        }
        int alpha = Math.round(255f * alphaPercent / 100f);
        boolean dark = cfg.darkMode;
        int neutralSurface = dark ? Color.rgb(24, 34, 53) : Color.rgb(248, 251, 255);
        int neutralStroke = dark
                ? Color.argb(cfg.blurEnabled ? 90 : 130, 255, 255, 255)
                : Color.argb(cfg.blurEnabled ? 150 : 190, 103, 116, 138);
        if (cfg.minimalTheme) {
            drawable.setColor(Color.argb(alpha,
                    Color.red(neutralSurface), Color.green(neutralSurface), Color.blue(neutralSurface)));
            drawable.setStroke(cfg.dp(1), neutralStroke);
        } else {
            int[] themeTints = PolarisVisualTheme.glassTintColors(
                    cfg.visualTheme, dark, neutralSurface);
            drawable.setColors(new int[]{
                    Color.argb(alpha, Color.red(themeTints[0]),
                            Color.green(themeTints[0]), Color.blue(themeTints[0])),
                    Color.argb(alpha, Color.red(themeTints[1]),
                            Color.green(themeTints[1]), Color.blue(themeTints[1]))
            });
            drawable.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            drawable.setStroke(cfg.dp(1),
                    PolarisVisualTheme.glassStrokeColor(cfg.visualTheme, dark, neutralStroke));
        }
        drawable.setCornerRadius(cfg.dp(radius));
        return drawable;
    }

    /** 壳层玻璃层:取样源为主窗口内容层。 */
    public static View glassLayer(Config cfg, View source,
                                  GradientDrawable background, int radius) {
        BackdropBlurView layer = new BackdropBlurView(cfg.context);
        applyLayer(layer, cfg, source, background, radius);
        return layer;
    }

    /** 就地刷新已存在的玻璃层;非玻璃视图则退化为直接设背景。 */
    public static void updateGlassLayer(View layer, Config cfg, View source,
                                        GradientDrawable background, int radius) {
        if (layer instanceof BackdropBlurView) {
            applyLayer((BackdropBlurView) layer, cfg, source, background, radius);
        } else {
            layer.setBackground(background);
        }
    }

    private static void applyLayer(BackdropBlurView layer, Config cfg, View source,
                                   GradientDrawable background, int radius) {
        layer.setSourceView(source);
        layer.setGlassBackground(background, cfg.dp(radius));
        layer.setBlurEnabled(cfg.blurEnabled, cfg.dp(28));
    }
}
