package com.polaris.timetable.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Exports the accepted weekly table layout as a shareable A4 PDF. */
public final class SchedulePdfExporter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int PAGE_MARGIN = 24;

    private SchedulePdfExporter() {
    }

    public static File exportToCache(Context context, ScheduleImageExporter.Request request)
            throws IOException {
        if (context == null) {
            throw new IOException("导出参数不完整");
        }
        Bitmap bitmap = ScheduleImageExporter.render(request);
        File directory = new File(context.getCacheDir(), ExportFileProvider.EXPORT_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            bitmap.recycle();
            throw new IOException("无法创建导出缓存目录");
        }
        File output = new File(directory, "Polaris-week-" + request.week + ".pdf");
        PdfDocument document = new PdfDocument();
        try {
            writePages(document, bitmap, request.sectionCount <= 11);
            try (FileOutputStream stream = new FileOutputStream(output, false)) {
                document.writeTo(stream);
            }
            return output;
        } catch (IOException exception) {
            if (output.exists()) {
                output.delete();
            }
            throw exception;
        } catch (RuntimeException exception) {
            if (output.exists()) {
                output.delete();
            }
            throw new IOException("课表 PDF 生成失败", exception);
        } finally {
            document.close();
            bitmap.recycle();
        }
    }

    private static void writePages(PdfDocument document, Bitmap bitmap,
                                   boolean fitSinglePage) {
        int contentWidth = PAGE_WIDTH - PAGE_MARGIN * 2;
        int contentHeight = PAGE_HEIGHT - PAGE_MARGIN * 2;
        List<SchedulePdfPagination.Slice> slices = SchedulePdfPagination.paginate(
                bitmap.getWidth(), bitmap.getHeight(), contentWidth, contentHeight,
                fitSinglePage);
        float scale = contentWidth / (float) bitmap.getWidth();
        if (fitSinglePage) {
            scale = Math.min(scale, contentHeight / (float) bitmap.getHeight());
        }
        for (int index = 0; index < slices.size(); index++) {
            SchedulePdfPagination.Slice slice = slices.get(index);
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH, PAGE_HEIGHT, index + 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Canvas canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            float drawnHeight = slice.height() * scale;
            Rect source = new Rect(0, slice.top, bitmap.getWidth(), slice.bottom);
            float drawnWidth = bitmap.getWidth() * scale;
            float left = (PAGE_WIDTH - drawnWidth) / 2f;
            RectF destination = new RectF(left, PAGE_MARGIN,
                    left + drawnWidth, PAGE_MARGIN + drawnHeight);
            Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(bitmap, source, destination, bitmapPaint);
            if (slices.size() > 1) {
                Paint pageNumber = new Paint(Paint.ANTI_ALIAS_FLAG);
                pageNumber.setColor(Color.rgb(102, 112, 133));
                pageNumber.setTextSize(9);
                pageNumber.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText((index + 1) + " / " + slices.size(),
                        PAGE_WIDTH - PAGE_MARGIN, PAGE_HEIGHT - 8, pageNumber);
            }
            document.finishPage(page);
        }
    }
}
