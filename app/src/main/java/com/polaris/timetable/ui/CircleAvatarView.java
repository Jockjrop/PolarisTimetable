package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.view.View;

public class CircleAvatarView extends View {
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint initialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path circlePath = new Path();
    private final Rect source = new Rect();
    private final RectF target = new RectF();
    private Bitmap bitmap;
    private BackgroundImageCrop crop = BackgroundImageCrop.full();
    private String accountName = "管理员";

    public CircleAvatarView(Context context) {
        super(context);
        placeholderPaint.setColor(Color.rgb(23, 32, 51));
        initialPaint.setColor(Color.WHITE);
        initialPaint.setTextAlign(Paint.Align.CENTER);
        initialPaint.setFakeBoldText(true);
        strokePaint.setColor(Color.argb(90, 255, 255, 255));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
    }

    public void setProfile(String name, String uriText, BackgroundImageCrop imageCrop) {
        accountName = name == null || name.trim().isEmpty() ? "管理员" : name.trim();
        crop = imageCrop == null ? BackgroundImageCrop.full() : imageCrop;
        recycleBitmap();
        String safeUri = uriText == null ? "" : uriText.trim();
        if (!safeUri.isEmpty()) {
            try {
                int size = Math.max(dp(160), Math.max(getWidth(), getHeight()) * 2);
                bitmap = BackgroundImageLoader.decode(
                        getContext(), Uri.parse(safeUri), size, size);
            } catch (Exception | OutOfMemoryError ignored) {
                bitmap = null;
            }
        }
        invalidate();
    }

    public void setPlaceholderColor(int color) {
        placeholderPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = Math.max(0f, Math.min(getWidth(), getHeight()) / 2f - dp(1));
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        circlePath.reset();
        circlePath.addCircle(centerX, centerY, radius, Path.Direction.CW);
        target.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        if (bitmap != null && !bitmap.isRecycled()) {
            BackgroundImageCrop fittedCrop = crop.fitToAspect(
                    bitmap.getWidth(), bitmap.getHeight(), 1f);
            source.set(
                    Math.max(0, Math.round(fittedCrop.left * bitmap.getWidth())),
                    Math.max(0, Math.round(fittedCrop.top * bitmap.getHeight())),
                    Math.min(bitmap.getWidth(), Math.round(fittedCrop.right * bitmap.getWidth())),
                    Math.min(bitmap.getHeight(), Math.round(fittedCrop.bottom * bitmap.getHeight())));
            canvas.save();
            canvas.clipPath(circlePath);
            canvas.drawBitmap(bitmap, source, target, imagePaint);
            canvas.restore();
        } else {
            canvas.drawCircle(centerX, centerY, radius, placeholderPaint);
            initialPaint.setTextSize(radius * 0.82f);
            Paint.FontMetrics metrics = initialPaint.getFontMetrics();
            float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(accountInitial(), centerX, baseline, initialPaint);
        }
        canvas.drawCircle(centerX, centerY, radius, strokePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        recycleBitmap();
        super.onDetachedFromWindow();
    }

    private String accountInitial() {
        if (accountName.isEmpty()) {
            return "管";
        }
        int end = accountName.offsetByCodePoints(0, 1);
        return accountName.substring(0, end);
    }

    private void recycleBitmap() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmap = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
