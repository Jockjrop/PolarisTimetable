package com.polaris.timetable.importer;

import android.graphics.Bitmap;

public interface ImageScheduleRecognizer extends AutoCloseable {
    interface Callback {
        void onResult(ImageScheduleRecognitionResult result);

        void onFailure(Exception exception);
    }

    void recognize(Bitmap image, Callback callback);

    @Override
    default void close() {
    }
}
