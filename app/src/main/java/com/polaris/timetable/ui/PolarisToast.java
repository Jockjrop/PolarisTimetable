package com.polaris.timetable.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * 应用内主题化轻提示：完成类提示（已保存 / 已导入 / 已复制 / 已应用等）统一使用，
 * 替代系统 Toast。设计约束：
 * <ul>
 *   <li>明暗双主题配色；深浅色由宿主显式传入（应用内「深色/浅色」覆盖优先于
 *       系统 uiMode，直接读 uiMode 会与实际主题不一致）；</li>
 *   <li>前置状态图标（成功 = 绿色对勾，信息 = 蓝点）+ 出场 / 退场动画；</li>
 *   <li>展示时长远短于系统 Toast（常规 1.6s，长文案 2.3s）；</li>
 *   <li>全局单实例：新提示直接替换旧提示，不排队；</li>
 *   <li>只承载「完成 / 信息」类短提示；错误与警告类仍用系统 Toast（需要更长的
 *       可见时间，不应被快速收回）。</li>
 * </ul>
 */
public final class PolarisToast {

    /** 常规完成提示时长（系统 Toast.LENGTH_SHORT 约为 2s，这里再收紧）。 */
    private static final long DURATION_DEFAULT_MS = 1600L;
    /** 长文案提示时长。 */
    private static final long DURATION_LONG_MS = 2300L;
    /** 文案超过该字符数视为长文案。 */
    private static final int LONG_TEXT_THRESHOLD = 20;

    private static final int COLOR_SUCCESS = 0xFF22C55E;
    private static final int COLOR_INFO = 0xFF3B82F6;
    private static final int COLOR_INK_DARK = 0xFFF2F7FF;
    private static final int COLOR_INK_LIGHT = 0xFF172033;
    private static final int COLOR_BG_DARK = 0xF21E2A3C;
    private static final int COLOR_BG_LIGHT = 0xFAFFFFFF;
    private static final int COLOR_STROKE_DARK = 0x2EFFFFFF;
    private static final int COLOR_STROKE_LIGHT = 0x1A172033;

    /** 本组件专用 Handler：不做 removeCallbacksAndMessages(null) 之外的全局清理。 */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** 只保留弱引用，避免提示动画或宿主 Activity 被全局状态长期持有。 */
    private static WeakReference<View> current = new WeakReference<>(null);

    private PolarisToast() {
    }

    /** 成功 / 完成提示；深浅色由宿主 uiMode 自动判定。 */
    public static void success(Activity host, CharSequence text) {
        show(host, text, Icon.SUCCESS, resolveDark(host), -1);
    }

    /** 成功 / 完成提示；dark 由宿主显式传入。bottomMarginPx 传负数使用默认避让。 */
    public static void success(Activity host, CharSequence text, boolean dark, int bottomMarginPx) {
        show(host, text, Icon.SUCCESS, dark, bottomMarginPx);
    }

    /** 中性信息提示（已开始 / 进行中类）；深浅色由宿主 uiMode 自动判定。 */
    public static void info(Activity host, CharSequence text) {
        show(host, text, Icon.INFO, resolveDark(host), -1);
    }

    /** 中性信息提示；dark 由宿主显式传入。 */
    public static void info(Activity host, CharSequence text, boolean dark, int bottomMarginPx) {
        show(host, text, Icon.INFO, dark, bottomMarginPx);
    }

    /**
     * 展示一条提示。bottomMarginPx 为提示底部到屏幕底部的距离（px），
     * 传负数使用内置默认避让（悬浮底栏上方约 110dp）。
     */
    public static void show(Activity host, CharSequence text, Icon icon, boolean dark,
                            int bottomMarginPx) {
        if (host == null || host.isFinishing() || host.isDestroyed()) {
            return;
        }
        ViewGroup content = host.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        MAIN.removeCallbacksAndMessages(null);
        removeCurrent();
        long duration = text != null && text.length() > LONG_TEXT_THRESHOLD
                ? DURATION_LONG_MS
                : DURATION_DEFAULT_MS;
        View pill = buildPill(host, text == null ? "" : text, icon, dark, bottomMarginPx);
        current = new WeakReference<>(pill);
        content.addView(pill);
        pill.setAlpha(0f);
        pill.setTranslationY(dp(host, 10));
        pill.animate().alpha(1f).translationY(0f).setDuration(150L)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        MAIN.postDelayed(() -> dismiss(pill), duration);
    }

    /** 立即收回当前提示（新提示替换时由 show 内部调用，无需外部触发）。 */
    public static void dismissCurrent() {
        MAIN.removeCallbacksAndMessages(null);
        removeCurrent();
    }

    /** 成功状态小图标（18dp 绿圆白勾）：供保存撤销条等宿主界面复用，保持完成类提示视觉一致。 */
    public static View successIcon(Context context) {
        return new StatusIconView(context, Icon.SUCCESS);
    }

    private static View buildPill(Activity host, CharSequence text, Icon icon, boolean dark,
                                  int bottomMarginPx) {
        LinearLayout pill = new LinearLayout(host);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int padV = dp(host, 9);
        pill.setPadding(dp(host, 13), padV, dp(host, 16), padV);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(host, 26));
        background.setColor(dark ? COLOR_BG_DARK : COLOR_BG_LIGHT);
        background.setStroke(dp(host, 1), dark ? COLOR_STROKE_DARK : COLOR_STROKE_LIGHT);
        pill.setBackground(background);
        pill.setElevation(dp(host, 6));

        View iconView = new StatusIconView(host, icon);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(host, 18), dp(host, 18));
        iconParams.rightMargin = dp(host, 8);
        pill.addView(iconView, iconParams);

        TextView label = new TextView(host);
        label.setText(text);
        label.setTextColor(dark ? COLOR_INK_DARK : COLOR_INK_LIGHT);
        label.setTextSize(14);
        label.setMaxLines(2);
        pill.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = bottomMarginPx >= 0 ? bottomMarginPx : dp(host, 110);
        pill.setLayoutParams(params);
        return pill;
    }

    private static void dismiss(View pill) {
        View active = current.get();
        if (active != pill) {
            return;
        }
        current.clear();
        float dy = pill.getResources().getDisplayMetrics().density * 8;
        pill.animate().cancel();
        pill.animate().alpha(0f).translationY(dy).setDuration(130L)
                .withEndAction(() -> {
                    if (pill.getParent() instanceof ViewGroup) {
                        ((ViewGroup) pill.getParent()).removeView(pill);
                    }
                })
                .start();
    }

    private static void removeCurrent() {
        View active = current.get();
        if (active != null) {
            active.animate().cancel();
            if (active.getParent() instanceof ViewGroup) {
                ((ViewGroup) active.getParent()).removeView(active);
            }
        }
        current.clear();
    }

    private static boolean resolveDark(Activity host) {
        int mode = host.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    /** 提示类型。 */
    public enum Icon {SUCCESS, INFO}

    /** 前置状态小图标：圆底 + 白色对勾（成功）/ 白点（信息）。 */
    private static final class StatusIconView extends View {

        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyphStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyphFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path checkPath = new Path();
        private final boolean success;

        StatusIconView(Context context, Icon icon) {
            super(context);
            circlePaint.setColor(icon == Icon.SUCCESS ? COLOR_SUCCESS : COLOR_INFO);
            glyphStroke.setColor(Color.WHITE);
            glyphStroke.setStyle(Paint.Style.STROKE);
            float width = Math.max(1.5f, context.getResources().getDisplayMetrics().density * 1.8f);
            glyphStroke.setStrokeWidth(width);
            glyphStroke.setStrokeCap(Paint.Cap.ROUND);
            glyphStroke.setStrokeJoin(Paint.Join.ROUND);
            glyphFill.setColor(Color.WHITE);
            success = icon == Icon.SUCCESS;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float radius = Math.min(w, h) / 2f;
            canvas.drawCircle(w / 2f, h / 2f, radius, circlePaint);
            if (success) {
                checkPath.reset();
                checkPath.moveTo(w * 0.28f, h * 0.52f);
                checkPath.lineTo(w * 0.44f, h * 0.68f);
                checkPath.lineTo(w * 0.74f, h * 0.34f);
                canvas.drawPath(checkPath, glyphStroke);
            } else {
                canvas.drawCircle(w / 2f, h / 2f, radius * 0.24f, glyphFill);
            }
        }
    }
}
