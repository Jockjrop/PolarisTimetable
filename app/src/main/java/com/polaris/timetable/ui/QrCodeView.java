package com.polaris.timetable.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * 分享链接二维码。始终「白底深码」——二维码印在深色背景上会明显降低识别率，
 * 所以这里不跟随主题换色，只在四周留出浅色留白。
 *
 * <p>四周固定留 4 个模块宽的静默区（QR 规范要求 ≥4）：屏幕上看着更透气，
 * 保存成图片后别人用相机扫也不至于因为贴边而识别失败。
 */
public final class QrCodeView extends View {

    /** 编码分辨率；实际绘制按控件尺寸缩放，与扫描无关。 */
    private static final int ENCODE_SIZE = 640;
    /** QR 规范要求的静默区宽度（模块数）。 */
    private static final int QUIET_MODULES = 4;
    private static final int FOREGROUND = Color.rgb(16, 20, 26);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private BitMatrix matrix;

    public QrCodeView(Context context) {
        this(context, null);
    }

    public QrCodeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * 设置二维码内容。
     *
     * @return 生成失败（内容为空或超长）时返回 false，调用方应退化为只给链接
     */
    public boolean setContent(String content) {
        if (content == null || content.isEmpty()) {
            matrix = null;
            invalidate();
            return false;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            // M 级纠错：容许约 15% 破损，扫码成功率与容量的平衡点。
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 0);
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE,
                    ENCODE_SIZE, ENCODE_SIZE, hints);
            invalidate();
            return true;
        } catch (WriterException | IllegalArgumentException exception) {
            matrix = null;
            invalidate();
            return false;
        }
    }

    public boolean hasContent() {
        return matrix != null;
    }

    /**
     * 渲染成正方形位图，供保存到相册或分享图片使用。
     *
     * @return 未设置内容或尺寸非法时返回 null
     */
    public Bitmap renderToBitmap(int sizePx) {
        if (matrix == null || sizePx <= 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        drawMatrix(canvas, sizePx, sizePx);
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (matrix == null || Math.min(getWidth(), getHeight()) <= 0) {
            return;
        }
        canvas.drawColor(Color.WHITE);
        drawMatrix(canvas, getWidth(), getHeight());
    }

    private void drawMatrix(Canvas canvas, float width, float height) {
        int modules = matrix.getWidth();
        float total = modules + QUIET_MODULES * 2f;
        float moduleSize = Math.min(width, height) / total;
        float originX = (width - moduleSize * total) / 2f + moduleSize * QUIET_MODULES;
        float originY = (height - moduleSize * total) / 2f + moduleSize * QUIET_MODULES;
        paint.setColor(FOREGROUND);
        for (int y = 0; y < modules; y++) {
            for (int x = 0; x < modules; x++) {
                if (!matrix.get(x, y)) {
                    continue;
                }
                canvas.drawRect(
                        originX + x * moduleSize,
                        originY + y * moduleSize,
                        originX + (x + 1) * moduleSize,
                        originY + (y + 1) * moduleSize,
                        paint);
            }
        }
    }
}
