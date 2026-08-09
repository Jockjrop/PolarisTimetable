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

/** Exports all semester courses together in one weekly timetable grid. */
public final class SemesterPdfExporter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int PAGE_MARGIN = 24;

    private SemesterPdfExporter() {
    }

    public static File exportToCache(Context context,
                                     ScheduleImageExporter.Request request,
                                     int semesterWeeks) throws IOException {
        if (context == null || request == null) {
            throw new IOException("导出参数不完整");
        }
        Bitmap bitmap = ScheduleImageExporter.renderSemester(request, semesterWeeks);
        File directory = new File(context.getCacheDir(), ExportFileProvider.EXPORT_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            bitmap.recycle();
            throw new IOException("无法创建导出缓存目录");
        }
        File output = new File(directory, "Polaris-semester.pdf");
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
            throw new IOException("整学期课表 PDF 生成失败", exception);
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
            float drawnWidth = bitmap.getWidth() * scale;
            float drawnHeight = slice.height() * scale;
            float left = (PAGE_WIDTH - drawnWidth) / 2f;
            Rect source = new Rect(0, slice.top, bitmap.getWidth(), slice.bottom);
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
