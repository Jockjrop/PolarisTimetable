package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Lightweight procedural atmosphere used behind all app pages. */
public final class PolarisThemeBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private String visualTheme = PolarisVisualTheme.MINIMAL;
    private boolean darkMode;

    public PolarisThemeBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setVisualTheme(String theme, boolean dark) {
        String normalized = PolarisVisualTheme.normalize(theme);
        if (normalized.equals(visualTheme) && darkMode == dark) {
            return;
        }
        visualTheme = normalized;
        darkMode = dark;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (PolarisVisualTheme.MINIMAL.equals(visualTheme)) {
            canvas.drawColor(PolarisVisualTheme.pageColor(visualTheme, darkMode));
        } else if (PolarisVisualTheme.GALAXY.equals(visualTheme)) {
            drawGalaxy(canvas);
        } else if (PolarisVisualTheme.CAMPUS.equals(visualTheme)) {
            drawCampus(canvas);
        } else {
            drawAurora(canvas);
        }
    }

    private void drawAurora(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        int top = darkMode ? Color.rgb(17, 21, 48) : Color.rgb(222, 244, 255);
        int bottom = darkMode ? Color.rgb(35, 25, 57) : Color.rgb(251, 229, 255);
        paint.setShader(new LinearGradient(0f, 0f, width, height, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height, paint);

        drawGlow(canvas, width * 0.08f, height * 0.26f, width * 0.74f,
                darkMode ? Color.argb(82, 28, 184, 224) : Color.argb(148, 108, 222, 255));
        drawGlow(canvas, width * 0.96f, height * 0.18f, width * 0.72f,
                darkMode ? Color.argb(82, 182, 80, 215) : Color.argb(142, 244, 146, 229));
        drawGlow(canvas, width * 0.68f, height * 0.77f, width * 0.92f,
                darkMode ? Color.argb(52, 90, 80, 214) : Color.argb(86, 171, 191, 255));

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int index = 0; index < 4; index++) {
            float offset = index * getResources().getDisplayMetrics().density * 12f;
            path.reset();
            path.moveTo(-width * 0.12f, height * 0.68f + offset);
            path.cubicTo(width * 0.23f, height * 0.47f + offset,
                    width * 0.56f, height * 0.79f - offset,
                    width * 1.12f, height * 0.35f + offset);
            int line = index % 2 == 0
                    ? Color.argb(darkMode ? 72 : 118, 111, 231, 255)
                    : Color.argb(darkMode ? 64 : 104, 246, 160, 235);
            paint.setColor(line);
            paint.setStrokeWidth(dp(index == 0 ? 3 : 2));
            canvas.drawPath(path, paint);
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        drawSparkles(canvas, darkMode ? 62 : 78, darkMode ? Color.WHITE : Color.rgb(255, 255, 255));
    }

    private void drawGalaxy(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        int top = darkMode ? Color.rgb(2, 13, 33) : Color.rgb(225, 237, 252);
        int bottom = darkMode ? Color.rgb(6, 27, 56) : Color.rgb(239, 245, 253);
        paint.setShader(new LinearGradient(0f, 0f, 0f, height, top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        if (!darkMode) {
            drawGlow(canvas, width * 0.76f, height * 0.18f, width * 0.65f,
                    Color.argb(72, 95, 148, 232));
        } else {
            drawGlow(canvas, width * 0.66f, height * 0.84f, width * 0.82f,
                    Color.argb(72, 24, 88, 170));
        }
        drawStars(canvas, darkMode ? 82 : 34);
        drawConstellation(canvas, new float[][]{
                {0.05f, 0.28f}, {0.20f, 0.23f}, {0.34f, 0.31f},
                {0.50f, 0.22f}, {0.68f, 0.30f}, {0.87f, 0.20f}
        });
        drawConstellation(canvas, new float[][]{
                {0.13f, 0.74f}, {0.28f, 0.68f}, {0.46f, 0.76f},
                {0.63f, 0.66f}, {0.84f, 0.72f}
        });
    }

    private void drawCampus(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        int top = darkMode ? Color.rgb(9, 27, 46) : Color.rgb(227, 242, 255);
        int bottom = darkMode ? Color.rgb(18, 39, 58) : Color.rgb(249, 252, 255);
        paint.setShader(new LinearGradient(0f, 0f, width * 0.2f, height,
                top, bottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);

        drawGlow(canvas, width * 0.78f, height * 0.18f, width * 0.72f,
                darkMode ? Color.argb(42, 62, 128, 173) : Color.argb(128, 255, 255, 255));
        drawGlow(canvas, width * 0.12f, height * 0.78f, width * 0.82f,
                darkMode ? Color.argb(32, 53, 105, 151) : Color.argb(96, 170, 211, 255));

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(darkMode ? Color.argb(38, 134, 188, 225)
                : Color.argb(68, 99, 154, 204));
        float step = dp(42);
        for (float x = -height; x < width; x += step) {
            canvas.drawLine(x, 0f, x + height, height, paint);
        }
        for (float y = 0f; y < height * 0.55f; y += step) {
            canvas.drawLine(0f, y, width * 0.72f, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        drawCloud(canvas, width * 0.84f, height * 0.22f, width * 0.34f);
        drawCloud(canvas, width * 0.09f, height * 0.64f, width * 0.42f);
        drawCampusTower(canvas, width * 0.78f, height * 0.06f, width * 0.18f, height * 0.16f);
    }

    private void drawGlow(Canvas canvas, float x, float y, float radius, int centerColor) {
        paint.setShader(new RadialGradient(x, y, radius, centerColor,
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
        paint.setShader(null);
    }

    private void drawSparkles(Canvas canvas, int count, int baseColor) {
        for (int index = 0; index < count; index++) {
            float x = pseudo(index * 17 + 3) * getWidth();
            float y = pseudo(index * 31 + 7) * getHeight();
            float radius = dp(1) * (0.45f + pseudo(index * 13 + 11) * 1.15f);
            paint.setColor(Color.argb(45 + Math.round(pseudo(index + 29) * 95),
                    Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)));
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private void drawStars(Canvas canvas, int count) {
        for (int index = 0; index < count; index++) {
            float x = pseudo(index * 23 + 5) * getWidth();
            float y = pseudo(index * 41 + 17) * getHeight();
            float radius = dp(1) * (0.35f + pseudo(index * 19 + 2));
            int alpha = darkMode ? 90 + Math.round(pseudo(index + 7) * 150) : 35;
            paint.setColor(Color.argb(alpha, 185, 218, 255));
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    private void drawConstellation(Canvas canvas, float[][] points) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(darkMode ? Color.argb(70, 72, 149, 235)
                : Color.argb(38, 65, 112, 181));
        path.reset();
        for (int index = 0; index < points.length; index++) {
            float x = points[index][0] * getWidth();
            float y = points[index][1] * getHeight();
            if (index == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(darkMode ? Color.argb(185, 116, 181, 255)
                : Color.argb(80, 66, 116, 188));
        for (float[] point : points) {
            canvas.drawCircle(point[0] * getWidth(), point[1] * getHeight(), dp(1), paint);
        }
    }

    private void drawCloud(Canvas canvas, float x, float y, float size) {
        paint.setColor(darkMode ? Color.argb(36, 119, 160, 196)
                : Color.argb(168, 255, 255, 255));
        canvas.drawCircle(x, y, size * 0.22f, paint);
        canvas.drawCircle(x + size * 0.2f, y - size * 0.04f, size * 0.27f, paint);
        canvas.drawCircle(x + size * 0.45f, y + size * 0.02f, size * 0.21f, paint);
        canvas.drawRoundRect(x - size * 0.2f, y, x + size * 0.62f,
                y + size * 0.28f, size * 0.12f, size * 0.12f, paint);
    }

    private void drawCampusTower(Canvas canvas, float x, float y, float width, float height) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(darkMode ? Color.argb(60, 132, 181, 222)
                : Color.argb(92, 75, 131, 181));
        canvas.drawRect(x, y + height * 0.25f, x + width, y + height, paint);
        canvas.drawRect(x + width * 0.34f, y, x + width * 0.66f, y + height, paint);
        canvas.drawCircle(x + width * 0.5f, y + height * 0.28f, width * 0.1f, paint);
        canvas.drawLine(x - width * 0.35f, y + height, x + width * 1.35f, y + height, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private float pseudo(int seed) {
        long value = (seed * 1103515245L + 12345L) & 0x7fffffffL;
        return (value % 10000L) / 10000f;
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
