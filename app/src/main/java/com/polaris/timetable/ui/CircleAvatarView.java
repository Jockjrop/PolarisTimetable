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

import com.polaris.timetable.storage.ScheduleRepository;

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
    private String accountName = "";

    public CircleAvatarView(Context context) {
        super(context);
        placeholderPaint.setColor(placeholderColorFor(accountName));
        initialPaint.setColor(Color.WHITE);
        initialPaint.setTextAlign(Paint.Align.CENTER);
        initialPaint.setFakeBoldText(true);
        strokePaint.setColor(Color.argb(90, 255, 255, 255));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
    }

    /**
     * 头像底色（1.27.7）：由账户名哈希映射到 HSV 色环的稳定随机色——
     * 同一账户名恒定不变（重启不漂移），不同账户名分散到不同颜色。
     */
    public static int placeholderColorFor(String name) {
        String safe = name == null ? "" : name.trim();
        int seed = safe.isEmpty() ? 0 : safe.hashCode();
        int hue = (seed & 0x7fffffff) % 360;
        return Color.HSVToColor(new float[]{hue, 0.55f, 0.52f});
    }

    public void setProfile(String name, String uriText, BackgroundImageCrop imageCrop) {
        accountName = name == null ? "" : name.trim();
        placeholderPaint.setColor(placeholderColorFor(accountName));
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
        // 首字母规则收敛到 ScheduleRepository.avatarInitial（1.27.7）：
        // 默认名「用户+6位码」显示码首字符（新装必为字母），其余名字显示首字符。
        return ScheduleRepository.avatarInitial(accountName);
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
