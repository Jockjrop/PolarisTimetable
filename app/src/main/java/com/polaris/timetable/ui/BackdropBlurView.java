package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

public class BackdropBlurView extends FrameLayout {
    private static final float DOWNSAMPLE = 0.24f;

    private final View blurLayer;
    private final View overlayLayer;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final int[] sourceLocation = new int[2];
    private final int[] selfLocation = new int[2];
    private final RectF clipRect = new RectF();
    private final Path clipPath = new Path();

    private View sourceView;
    // 源视图上一次采样时的几何/滚动状态：{left, top, width, height, scrollX, scrollY}
    private final int[] lastSourceState = new int[6];
    private boolean hasLastSourceState;
    private final ViewTreeObserver.OnPreDrawListener sourcePreDrawListener =
            () -> {
                // 仅在源视图几何或滚动状态真的变化时刷新背景采样。
                // 无条件 invalidate 会与本视图自己的绘制请求构成 preDraw→invalidate→
                // preDraw 自激环，使画面完全静止时仍以 60fps 持续重绘：既白烧 GPU/电量，
                // 又让主线程永不 idle（Espresso.onIdle() 因此无限阻塞，见 CI 记录）。
                if (sourceStateChanged()) {
                    invalidateBackdrop();
                }
                return true;
            };
    private Bitmap buffer;
    private Canvas bufferCanvas;
    private float cornerRadius;
    private float blurRadius;
    private boolean blurEnabled = true;
    private boolean listenerAttached;

    public BackdropBlurView(Context context) {
        super(context);
        setWillNotDraw(false);
        blurLayer = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                drawBackdrop(canvas);
            }
        };
        overlayLayer = new View(context);
        addView(blurLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(overlayLayer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setSourceView(View sourceView) {
        removeSourcePreDrawListener();
        this.sourceView = sourceView;
        hasLastSourceState = false; // 换源后强制下一次 preDraw 重新采样
        addSourcePreDrawListener();
        invalidateBackdrop();
    }

    public void setGlassBackground(GradientDrawable background, float cornerRadius) {
        this.cornerRadius = cornerRadius;
        overlayLayer.setBackground(background);
        invalidate();
    }

    public void setBlurEnabled(boolean enabled, float radius) {
        blurEnabled = enabled;
        blurRadius = radius;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurEnabled) {
            blurLayer.setVisibility(VISIBLE);
            blurLayer.setRenderEffect(RenderEffect.createBlurEffect(
                    blurRadius, blurRadius, Shader.TileMode.CLAMP));
        } else {
            blurLayer.setVisibility(GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurLayer.setRenderEffect(null);
            }
        }
        invalidateBackdrop();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int contentWidth = 0;
        int contentHeight = 0;
        int childState = 0;

        for (int i = 2; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            contentWidth = Math.max(contentWidth, child.getMeasuredWidth());
            contentHeight = Math.max(contentHeight, child.getMeasuredHeight());
            childState = combineMeasuredStates(childState, child.getMeasuredState());
        }

        if (getChildCount() <= 2) {
            contentWidth = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            contentHeight = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        }

        int measuredWidth = resolveSizeAndState(
                Math.max(contentWidth, getSuggestedMinimumWidth()), widthMeasureSpec, childState);
        int measuredHeight = resolveSizeAndState(
                Math.max(contentHeight, getSuggestedMinimumHeight()), heightMeasureSpec,
                childState << MEASURED_HEIGHT_STATE_SHIFT);
        setMeasuredDimension(measuredWidth, measuredHeight);

        int exactWidth = MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY);
        int exactHeight = MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY);
        blurLayer.measure(exactWidth, exactHeight);
        overlayLayer.measure(exactWidth, exactHeight);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (cornerRadius > 0f) {
            clipRect.set(0f, 0f, getWidth(), getHeight());
            clipPath.reset();
            clipPath.addRoundRect(clipRect, cornerRadius, cornerRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            super.dispatchDraw(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        addSourcePreDrawListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeSourcePreDrawListener();
        releaseBuffer();
        hasLastSourceState = false; // buffer 已释放，重新挂载后需重新采样
    }

    private void drawBackdrop(Canvas canvas) {
        if (!blurEnabled || sourceView == null || getWidth() <= 0 || getHeight() <= 0
                || sourceView.getWidth() <= 0 || sourceView.getHeight() <= 0) {
            canvas.drawColor(Color.TRANSPARENT);
            return;
        }

        int bufferWidth = Math.max(1, Math.round(getWidth() * DOWNSAMPLE));
        int bufferHeight = Math.max(1, Math.round(getHeight() * DOWNSAMPLE));
        ensureBuffer(bufferWidth, bufferHeight);
        if (buffer == null || bufferCanvas == null) {
            return;
        }

        sourceView.getLocationOnScreen(sourceLocation);
        getLocationOnScreen(selfLocation);

        buffer.eraseColor(Color.TRANSPARENT);
        bufferCanvas.save();
        bufferCanvas.scale(DOWNSAMPLE, DOWNSAMPLE);
        bufferCanvas.translate(sourceLocation[0] - selfLocation[0],
                sourceLocation[1] - selfLocation[1]);
        sourceView.draw(bufferCanvas);
        bufferCanvas.restore();

        canvas.drawBitmap(buffer, null, clipRectForCanvas(), bitmapPaint);
    }

    private RectF clipRectForCanvas() {
        clipRect.set(0f, 0f, getWidth(), getHeight());
        return clipRect;
    }

    private void ensureBuffer(int width, int height) {
        if (buffer != null && buffer.getWidth() == width && buffer.getHeight() == height) {
            return;
        }
        if (buffer != null) {
            buffer.recycle();
        }
        buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bufferCanvas = new Canvas(buffer);
    }

    private void releaseBuffer() {
        if (buffer != null) {
            buffer.recycle();
            buffer = null;
            bufferCanvas = null;
        }
    }

    private void invalidateBackdrop() {
        blurLayer.invalidate();
    }

    /**
     * 源视图是否需要重新采样：几何（位置/尺寸）或滚动状态发生变化。
     * 变化后更新缓存并返回 true；无变化返回 false，用于断开 preDraw 自激环。
     *
     * 已知权衡：源视图「仅内容重绘、几何与滚动不变」时不会被判定为变化，
     * 模糊背景会滞后一帧。该层是 0.24x 降采样 + 半透明面板下的装饰性模糊，
     * 此代价显著低于静止时持续 60fps 重绘的电量开销，故接受该取舍。
     */
    private boolean sourceStateChanged() {
        if (sourceView == null) {
            return false;
        }
        sourceView.getLocationOnScreen(sourceLocation);
        if (!hasLastSourceState) {
            hasLastSourceState = true;
            recordSourceState();
            return true;
        }
        if (lastSourceState[0] != sourceLocation[0]
                || lastSourceState[1] != sourceLocation[1]
                || lastSourceState[2] != sourceView.getWidth()
                || lastSourceState[3] != sourceView.getHeight()
                || lastSourceState[4] != sourceView.getScrollX()
                || lastSourceState[5] != sourceView.getScrollY()) {
            recordSourceState();
            return true;
        }
        return false;
    }

    private void recordSourceState() {
        lastSourceState[0] = sourceLocation[0];
        lastSourceState[1] = sourceLocation[1];
        lastSourceState[2] = sourceView.getWidth();
        lastSourceState[3] = sourceView.getHeight();
        lastSourceState[4] = sourceView.getScrollX();
        lastSourceState[5] = sourceView.getScrollY();
    }

    private void addSourcePreDrawListener() {
        if (sourceView == null || listenerAttached) {
            return;
        }
        ViewTreeObserver observer = sourceView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.addOnPreDrawListener(sourcePreDrawListener);
            listenerAttached = true;
        }
    }

    private void removeSourcePreDrawListener() {
        if (sourceView == null || !listenerAttached) {
            return;
        }
        ViewTreeObserver observer = sourceView.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnPreDrawListener(sourcePreDrawListener);
        }
        listenerAttached = false;
    }
}
