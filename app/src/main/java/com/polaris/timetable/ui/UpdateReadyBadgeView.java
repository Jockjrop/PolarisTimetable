package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * 「更新已就绪」悬浮角标：绿色圆底 + 白色向下箭头，表示新版本已下载并通过校验，
 * 可点击安装。由 MainActivity 挂载到根布局顶层，在课表 / 计划 / 我的三个主页面
 * 常驻显示（位置由宿主按页签计算），点击后由宿主弹出「是否安装」确认框。
 *
 * <p>纯自绘、无资源依赖：明暗主题使用同一组高对比配色（绿底白箭头 + 半透明白
 * 描边 + 柔和投影），在浅色页面与深色玻璃面上都保持可读。
 */
public final class UpdateReadyBadgeView extends View {

    /** 主色：完成绿（明暗主题一致，保证与“下载完成”的语义绑定）。 */
    private static final int COLOR_FILL = 0xFF22C55E;
    /** 外圈半透明白描边：在任意背景上形成玻璃质感边界。 */
    private static final int COLOR_RING = 0x59FFFFFF;
    /** 柔和投影色。 */
    private static final int COLOR_SHADOW = 0x40172838;

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrowPath = new Path();

    public UpdateReadyBadgeView(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        circlePaint.setColor(COLOR_FILL);
        circlePaint.setStyle(Paint.Style.FILL);
        // 投影经 shadowLayer 绘制，需要软件层；角标仅 40dp，开销可忽略。
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        circlePaint.setShadowLayer(3f * density, 0f, 2f * density, COLOR_SHADOW);
        ringPaint.setColor(COLOR_RING);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f * density);
        arrowPaint.setColor(Color.WHITE);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeWidth(Math.max(2f, 2.2f * density));
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - ringPaint.getStrokeWidth() / 2f;
        canvas.drawCircle(cx, cy, radius, circlePaint);
        canvas.drawCircle(cx, cy, radius - ringPaint.getStrokeWidth() / 2f, ringPaint);

        // 向下箭头：竖轴 + 下探折线（与系统“下载完成”图标语义一致）。
        arrowPath.reset();
        arrowPath.moveTo(cx, h * 0.26f);
        arrowPath.lineTo(cx, h * 0.56f);
        arrowPath.moveTo(cx - w * 0.20f, h * 0.40f);
        arrowPath.lineTo(cx, h * 0.62f);
        arrowPath.lineTo(cx + w * 0.20f, h * 0.40f);
        canvas.drawPath(arrowPath, arrowPaint);
    }
}
