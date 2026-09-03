package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class BackgroundCropView extends View {
    private static final float MAX_ZOOM_MULTIPLIER = 5f;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawDestination = new RectF();
    private final RectF cropWindow = new RectF();
    private final Path shadePath = new Path();
    private final Path circlePath = new Path();
    private final ScaleGestureDetector scaleDetector;
    private final float targetAspect;
    private final boolean circularWindow;
    private Bitmap bitmap;
    private float minimumScale = 1f;
    private float imageScale = 1f;
    private float imageLeft;
    private float imageTop;
    private float lastTouchX;
    private float lastTouchY;
    private boolean positioned;

    public BackgroundCropView(Context context, Bitmap bitmap, float targetAspect) {
        this(context, bitmap, targetAspect, false);
    }

    /** 供布局预览/反射实例化使用：无图占位。 */
    public BackgroundCropView(Context context) {
        this(context, null, 0f);
    }

    public BackgroundCropView(Context context, AttributeSet attrs) {
        this(context, null, 0f);
    }

    public BackgroundCropView(
            Context context, Bitmap bitmap, float targetAspect, boolean circularWindow) {
        super(context);
        this.bitmap = bitmap;
        this.targetAspect = targetAspect > 0f ? targetAspect : 9f / 16f;
        this.circularWindow = circularWindow;
        setContentDescription("背景展示区域，可单指拖动、双指缩放");
        setFocusable(true);
        shadePaint.setColor(Color.argb(170, 8, 14, 25));
        framePaint.setColor(Color.WHITE);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(2));
        gridPaint.setColor(Color.argb(150, 255, 255, 255));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1));
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (BackgroundCropView.this.bitmap == null) {
                    return false;
                }
                float previousScale = imageScale;
                float nextScale = clamp(imageScale * detector.getScaleFactor(),
                        minimumScale, minimumScale * MAX_ZOOM_MULTIPLIER);
                if (Math.abs(nextScale - previousScale) < 0.0001f) {
                    return true;
                }
                float factor = nextScale / previousScale;
                imageLeft = detector.getFocusX() - (detector.getFocusX() - imageLeft) * factor;
                imageTop = detector.getFocusY() - (detector.getFocusY() - imageTop) * factor;
                imageScale = nextScale;
                constrainImage();
                invalidate();
                return true;
            }
        });
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        float inset = dp(10);
        float availableWidth = Math.max(1f, width - inset * 2f);
        float availableHeight = Math.max(1f, height - inset * 2f);
        float cropWidth = availableWidth;
        float cropHeight = cropWidth / targetAspect;
        if (cropHeight > availableHeight) {
            cropHeight = availableHeight;
            cropWidth = cropHeight * targetAspect;
        }
        float left = (width - cropWidth) / 2f;
        float top = (height - cropHeight) / 2f;
        cropWindow.set(left, top, left + cropWidth, top + cropHeight);
        circlePath.reset();
        circlePath.addCircle(cropWindow.centerX(), cropWindow.centerY(),
                Math.min(cropWindow.width(), cropWindow.height()) / 2f, Path.Direction.CW);
        positionImageAtCenter();
    }

    private void positionImageAtCenter() {
        if (bitmap == null || cropWindow.isEmpty()) {
            return;
        }
        minimumScale = Math.max(cropWindow.width() / bitmap.getWidth(),
                cropWindow.height() / bitmap.getHeight());
        imageScale = minimumScale;
        imageLeft = cropWindow.centerX() - bitmap.getWidth() * imageScale / 2f;
        imageTop = cropWindow.centerY() - bitmap.getHeight() * imageScale / 2f;
        positioned = true;
        constrainImage();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(16, 24, 39));
        if (bitmap != null && positioned) {
            // onDraw 内复用字段，避免每帧分配（DrawAllocation）
            drawDestination.set(imageLeft, imageTop,
                    imageLeft + bitmap.getWidth() * imageScale,
                    imageTop + bitmap.getHeight() * imageScale);
            canvas.drawBitmap(bitmap, null, drawDestination, bitmapPaint);
        }
        if (circularWindow) {
            shadePath.reset();
            shadePath.setFillType(Path.FillType.EVEN_ODD);
            shadePath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            shadePath.addPath(circlePath);
            canvas.drawPath(shadePath, shadePaint);
            canvas.drawCircle(cropWindow.centerX(), cropWindow.centerY(),
                    Math.min(cropWindow.width(), cropWindow.height()) / 2f, framePaint);
            canvas.save();
            canvas.clipPath(circlePath);
        } else {
            canvas.drawRect(0, 0, getWidth(), cropWindow.top, shadePaint);
            canvas.drawRect(0, cropWindow.bottom, getWidth(), getHeight(), shadePaint);
            canvas.drawRect(0, cropWindow.top, cropWindow.left, cropWindow.bottom, shadePaint);
            canvas.drawRect(cropWindow.right, cropWindow.top, getWidth(), cropWindow.bottom, shadePaint);
            canvas.drawRect(cropWindow, framePaint);
        }
        float thirdWidth = cropWindow.width() / 3f;
        float thirdHeight = cropWindow.height() / 3f;
        canvas.drawLine(cropWindow.left + thirdWidth, cropWindow.top,
                cropWindow.left + thirdWidth, cropWindow.bottom, gridPaint);
        canvas.drawLine(cropWindow.left + thirdWidth * 2f, cropWindow.top,
                cropWindow.left + thirdWidth * 2f, cropWindow.bottom, gridPaint);
        canvas.drawLine(cropWindow.left, cropWindow.top + thirdHeight,
                cropWindow.right, cropWindow.top + thirdHeight, gridPaint);
        canvas.drawLine(cropWindow.left, cropWindow.top + thirdHeight * 2f,
                cropWindow.right, cropWindow.top + thirdHeight * 2f, gridPaint);
        if (circularWindow) {
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }
        getParent().requestDisallowInterceptTouchEvent(true);
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    float x = event.getX();
                    float y = event.getY();
                    imageLeft += x - lastTouchX;
                    imageTop += y - lastTouchY;
                    lastTouchX = x;
                    lastTouchY = y;
                    constrainImage();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
                if (remainingIndex < event.getPointerCount()) {
                    lastTouchX = event.getX(remainingIndex);
                    lastTouchY = event.getY(remainingIndex);
                }
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public BackgroundImageCrop getCropSelection() {
        if (bitmap == null || !positioned || imageScale <= 0f) {
            return BackgroundImageCrop.full();
        }
        float left = (cropWindow.left - imageLeft) / (bitmap.getWidth() * imageScale);
        float top = (cropWindow.top - imageTop) / (bitmap.getHeight() * imageScale);
        float right = (cropWindow.right - imageLeft) / (bitmap.getWidth() * imageScale);
        float bottom = (cropWindow.bottom - imageTop) / (bitmap.getHeight() * imageScale);
        return BackgroundImageCrop.of(left, top, right, bottom);
    }

    public void releaseBitmap() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmap = null;
    }

    private void constrainImage() {
        if (bitmap == null || cropWindow.isEmpty()) {
            return;
        }
        float imageWidth = bitmap.getWidth() * imageScale;
        float imageHeight = bitmap.getHeight() * imageScale;
        float minimumLeft = cropWindow.right - imageWidth;
        float maximumLeft = cropWindow.left;
        float minimumTop = cropWindow.bottom - imageHeight;
        float maximumTop = cropWindow.top;
        imageLeft = clamp(imageLeft, minimumLeft, maximumLeft);
        imageTop = clamp(imageTop, minimumTop, maximumTop);
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
