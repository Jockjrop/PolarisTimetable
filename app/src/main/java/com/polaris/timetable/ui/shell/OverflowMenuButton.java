package com.polaris.timetable.ui.shell;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;

import com.polaris.timetable.ui.DesignTokens;

/**
 * 顶栏「···」入口：常驻圆角正方形描边框 + 三个圆点，按下时绘制内容轻微收缩再回弹。
 *
 * <p>本 View 自身绝不改变尺寸、位置或 transform：它同时是操作菜单的锚点，
 * 1.30.1 的菜单跳位事故就是按钮旋转/缩放动画让 showAsDropDown 读到变形中的
 * 锚点。这里只对 Canvas 绘制内容做缩放与变色，getLocationOnScreen() 恒定。
 * 三个点手绘而非用「…」字形，避免字形基线偏下导致与同行按钮对不齐。
 */
public final class OverflowMenuButton extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;

    private int inkColor;
    private int outlineColor;
    private int accentColor;
    private float pressProgress;
    private ValueAnimator pressAnimator;

    /**
     * 布局编辑器预览用构造：运行时的主题色由三参构造传入，这里只给中性占位色，
     * 避免自定义 View 缺少 (Context, AttributeSet) 构造而被 lint 警告。
     */
    public OverflowMenuButton(Context context, AttributeSet attrs) {
        this(context, Color.WHITE, Color.GRAY, Color.rgb(140, 200, 255));
    }

    @SuppressLint("ClickableViewAccessibility")
    public OverflowMenuButton(Context context, int ink, int outline, int accent) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.inkColor = ink;
        this.outlineColor = outline;
        this.accentColor = accent;
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(1f, dp(1.5f)));
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(ink);
        setClickable(true);
        // 抑制原因：这里只读取按下/抬起做纯视觉动画，返回 false 不消费事件，
        // 点击仍由 OnClickListener 与 View.onTouchEvent 承接，不会破坏无障碍激活。
        setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                animatePress(1f, false);
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                animatePress(0f, true);
            }
            return false;
        });
    }

    /** 主题/明暗切换后刷新固有色。 */
    public void applyColors(int ink, int outline, int accent) {
        this.inkColor = ink;
        this.outlineColor = outline;
        this.accentColor = accent;
        dotPaint.setColor(ink);
        invalidate();
    }

    private void animatePress(float target, boolean bounce) {
        if (pressAnimator != null) {
            pressAnimator.cancel();
        }
        pressAnimator = ValueAnimator.ofFloat(pressProgress, target);
        pressAnimator.setDuration(bounce ? 260L : 110L);
        pressAnimator.setInterpolator(bounce
                ? new OvershootInterpolator(2.4f) : new DecelerateInterpolator());
        pressAnimator.addUpdateListener(animation -> {
            pressProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        pressAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float p = Math.max(0f, Math.min(1f, pressProgress));
        // OvershootInterpolator 会让回弹瞬间的 progress 短暂为负，
        // 绘制内容随之略微放大，形成「弹一下」；View 几何不受影响。
        float scale = Math.max(0.88f, Math.min(1.04f, 1f - 0.09f * pressProgress));
        // 圆角正方形框：边长取容器短边减去统一内缩，宽高相等且以 View 中心
        // 居中，三点画在 View 中心即框中心；框只影响绘制，锚点几何不变。
        float inset = dp(6) + strokePaint.getStrokeWidth() / 2f;
        float side = Math.min(w, h) - 2 * inset;
        float left = (w - side) / 2f;
        float top = (h - side) / 2f;
        box.set(left, top, left + side, top + side);
        float radius = dp(DesignTokens.RADIUS_ICON_BUTTON);

        // 常驻描边掺 35% 强调色：浅色主题下纯 outlineColor 接近背景色会看不见，
        // 混合后深/浅两套主题都能明确圈出按钮；按下再渐变到纯强调色。
        int idleStroke = blend(outlineColor, accentColor, 0.35f);
        strokePaint.setColor(blend(idleStroke, accentColor, p));
        fillPaint.setColor(Color.argb(Math.round(30 * p),
                Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        dotPaint.setColor(inkColor);

        canvas.save();
        canvas.scale(scale, scale, w / 2f, h / 2f);
        canvas.drawRoundRect(box, radius, radius, fillPaint);
        canvas.drawRoundRect(box, radius, radius, strokePaint);
        float cx = w / 2f;
        float cy = h / 2f;
        float gap = dp(6);
        float dotRadius = dp(1.9f);
        canvas.drawCircle(cx - gap, cy, dotRadius, dotPaint);
        canvas.drawCircle(cx, cy, dotRadius, dotPaint);
        canvas.drawCircle(cx + gap, cy, dotRadius, dotPaint);
        canvas.restore();
    }

    private static int blend(int from, int to, float fraction) {
        return Color.argb(
                Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * fraction),
                Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * fraction),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * fraction),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction));
    }

    private float dp(float value) {
        return value * density;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pressAnimator != null) {
            pressAnimator.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Button.class.getName());
    }
}
