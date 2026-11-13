package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/**
 * 自绘开关控件：设置页与对话框共用同一份实现。
 *
 * <p>视觉约定：选中态固定使用品牌蓝（不随视觉主题变化），未选中态按明暗分叉。
 * 提供 {@link #setDark(boolean)} 供主题/深色模式切换时就地换肤，
 * 避免为了更新一个开关而重建整页视图树。
 */
public class PolarisSwitchView extends View {
    private static final int TRACK_CHECKED = Color.parseColor("#3E8BFF");
    private static final int TRACK_OFF_DARK = Color.parseColor("#252B36");
    private static final int TRACK_OFF_LIGHT = Color.parseColor("#D8DDE6");
    private static final int STROKE_CHECKED = Color.parseColor("#63A4FF");
    private static final int STROKE_OFF_DARK = Color.parseColor("#3B424D");
    private static final int STROKE_OFF_LIGHT = Color.parseColor("#C9D0DA");

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;
    private boolean dark;

    /** 供布局预览与通用场景使用的默认构造（关闭态、浅色）。 */
    public PolarisSwitchView(Context context) {
        this(context, false, false);
    }

    public PolarisSwitchView(Context context, boolean checked, boolean dark) {
        super(context);
        this.checked = checked;
        this.dark = dark;
    }

    public void setChecked(boolean checked) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        invalidate();
    }

    public boolean isChecked() {
        return checked;
    }

    public void setDark(boolean dark) {
        if (this.dark == dark) {
            return;
        }
        this.dark = dark;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float radius = height / 2f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? TRACK_CHECKED : dark ? TRACK_OFF_DARK : TRACK_OFF_LIGHT);
        canvas.drawRoundRect(0f, 0f, width, height, radius, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(checked ? STROKE_CHECKED : dark ? STROKE_OFF_DARK : STROKE_OFF_LIGHT);
        float inset = dp(0.5f);
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        float thumbRadius = height / 2f - dp(4);
        float centerX = checked ? width - radius : radius;
        canvas.drawCircle(centerX, height / 2f, thumbRadius, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
