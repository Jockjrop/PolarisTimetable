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
    private final ViewTreeObserver.OnPreDrawListener sourcePreDrawListener =
            () -> {
                invalidateBackdrop();
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
