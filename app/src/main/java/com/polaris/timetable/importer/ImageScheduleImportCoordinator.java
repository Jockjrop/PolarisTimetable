package com.polaris.timetable.importer;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import com.polaris.timetable.ui.BackgroundImageLoader;

public final class ImageScheduleImportCoordinator implements AutoCloseable {
    private static final String TAG = "ImageScheduleImport";
    private static final int PREVIEW_WIDTH = 1080;
    private static final int PREVIEW_HEIGHT = 1440;

    public interface Callback {
        void onStarted(String displayName);

        void onPreviewReady(String displayName, Bitmap preview,
                            ImageScheduleRecognitionResult result);

        void onFailed(Exception exception);
    }

    private final Context context;
    private final ImageScheduleRecognizer recognizer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ImageScheduleImportCoordinator(Context context,
                                          ImageScheduleRecognizer recognizer) {
        this.context = context.getApplicationContext();
        this.recognizer = recognizer;
    }

    public void prepare(Uri uri, Callback callback) {
        if (uri == null || callback == null) {
            return;
        }
        persistReadPermission(uri);
        String displayName = displayName(uri);
        callback.onStarted(displayName);
        new Thread(() -> decodeAndRecognize(uri, displayName, callback),
                "image-schedule-import").start();
    }

    private void decodeAndRecognize(Uri uri, String displayName, Callback callback) {
        try {
            Bitmap preview = BackgroundImageLoader.decode(
                    context, uri, PREVIEW_WIDTH, PREVIEW_HEIGHT);
            if (preview == null) {
                throw new IllegalArgumentException("所选文件不是可读取的图片");
            }
            recognizer.recognize(preview, new ImageScheduleRecognizer.Callback() {
                @Override
                public void onResult(ImageScheduleRecognitionResult result) {
                    mainHandler.post(() -> callback.onPreviewReady(
                            displayName, preview, result));
                }

                @Override
                public void onFailure(Exception exception) {
                    if (!preview.isRecycled()) {
                        preview.recycle();
                    }
                    mainHandler.post(() -> callback.onFailed(exception));
                }
            });
        } catch (Exception exception) {
            mainHandler.post(() -> callback.onFailed(exception));
        } catch (OutOfMemoryError error) {
            mainHandler.post(() -> callback.onFailed(
                    new IllegalStateException("图片尺寸过大，无法进行本地识别", error)));
        }
    }

    private void persistReadPermission(Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Image provider granted only temporary read access", exception);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) {
                        return value.trim();
                    }
                }
            }
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unable to read selected image display name", exception);
        }
        return "所选课表图片";
    }

    @Override
    public void close() {
        recognizer.close();
    }
}
