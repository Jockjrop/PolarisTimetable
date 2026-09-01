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
    // 源视图子树上一次采样时的内容签名（自身几何 + 各层滚动位置 + 子节点数）
    private long lastSourceSignature;
    private boolean hasLastSourceState;
    private final ViewTreeObserver.OnPreDrawListener sourcePreDrawListener =
            () -> {
                // 仅在源视图子树的内容签名真的变化时刷新背景采样。
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
            // RenderEffect 需要硬件加速，显式提升为硬件层以确保在部分机型/模拟器上生效
            blurLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            blurLayer.setRenderEffect(RenderEffect.createBlurEffect(
                    blurRadius, blurRadius, Shader.TileMode.CLAMP));
        } else {
            blurLayer.setVisibility(GONE);
            blurLayer.setLayerType(View.LAYER_TYPE_NONE, null);
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
     * 源视图子树是否需要重新采样。
     *
     * 关键点：顶栏/底栏的模糊源是整个 {@code contentHost} 容器，它自身的几何与
     * scrollY 永不变化——真正在动的是它内部的 ScrollView / ViewPager。只比对源视图
     * 自身状态会漏掉「课表纵向滚动」「周次横滑」这类最常见的背景变化，导致顶栏玻璃
     * 长期定格在首帧（实测顶栏 changed% = 0.00）。
     *
     * 因此这里递归遍历源视图子树，把每层的 scrollX/scrollY、可见性、几何尺寸与子节点数
     * 折进一个 64 位签名；任一层滚动或结构变化都会改变签名，从而触发重采样。
     * 仍然不响应「纯像素级重绘」（如 Canvas 自绘动画帧），以此保留静止时零重绘的收益。
     */
    private boolean sourceStateChanged() {
        if (sourceView == null) {
            return false;
        }
        long signature = subtreeSignature(sourceView, 0);
        if (!hasLastSourceState || signature != lastSourceSignature) {
            hasLastSourceState = true;
            lastSourceSignature = signature;
            return true;
        }
        return false;
    }

    /** 递归折算子树内容签名；限制深度避免深层级布局带来的遍历成本。 */
    private long subtreeSignature(View view, int depth) {
        long hash = 31L * view.getScrollX() + 131L * view.getScrollY()
                + 17L * view.getWidth() + 19L * view.getHeight()
                + 7L * view.getVisibility();
        if (depth >= 6 || !(view instanceof android.view.ViewGroup)) {
            return hash;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        int count = group.getChildCount();
        hash = hash * 33L + count;
        for (int i = 0; i < count; i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            hash = hash * 1000003L + subtreeSignature(child, depth + 1)
                    + 3L * child.getLeft() + 5L * child.getTop();
        }
        return hash;
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
