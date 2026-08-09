package com.polaris.timetable.ui;

public final class BackgroundImageCrop {
    private static final float MIN_SPAN = 0.01f;
    private static final float EPSILON = 0.0001f;

    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    private BackgroundImageCrop(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public static BackgroundImageCrop full() {
        return new BackgroundImageCrop(0f, 0f, 1f, 1f);
    }

    public static BackgroundImageCrop of(float left, float top, float right, float bottom) {
        if (!isFinite(left) || !isFinite(top) || !isFinite(right) || !isFinite(bottom)) {
            return full();
        }
        float safeLeft = clamp(left);
        float safeTop = clamp(top);
        float safeRight = clamp(right);
        float safeBottom = clamp(bottom);
        if (safeRight - safeLeft < MIN_SPAN || safeBottom - safeTop < MIN_SPAN) {
            return full();
        }
        return new BackgroundImageCrop(safeLeft, safeTop, safeRight, safeBottom);
    }

    public BackgroundImageCrop fitToAspect(float sourceWidth, float sourceHeight, float targetAspect) {
        if (sourceWidth <= 0f || sourceHeight <= 0f
                || targetAspect <= 0f || !isFinite(targetAspect)) {
            return this;
        }
        float selectedWidth = (right - left) * sourceWidth;
        float selectedHeight = (bottom - top) * sourceHeight;
        float selectedAspect = selectedWidth / selectedHeight;
        if (selectedAspect > targetAspect) {
            float normalizedWidth = selectedHeight * targetAspect / sourceWidth;
            float centerX = (left + right) / 2f;
            return of(centerX - normalizedWidth / 2f, top,
                    centerX + normalizedWidth / 2f, bottom);
        }
        float normalizedHeight = selectedWidth / targetAspect / sourceHeight;
        float centerY = (top + bottom) / 2f;
        return of(left, centerY - normalizedHeight / 2f,
                right, centerY + normalizedHeight / 2f);
    }

    public boolean sameAs(BackgroundImageCrop other) {
        return other != null
                && Math.abs(left - other.left) < EPSILON
                && Math.abs(top - other.top) < EPSILON
                && Math.abs(right - other.right) < EPSILON
                && Math.abs(bottom - other.bottom) < EPSILON;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
