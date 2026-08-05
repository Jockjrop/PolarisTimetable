package com.polaris.timetable.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

public final class BackgroundImageLoader {
    private static final long MAX_DECODE_PIXELS = 4_000_000L;

    private BackgroundImageLoader() {
    }

    public static Bitmap decode(Context context, Uri uri, int targetWidth, int targetHeight)
            throws IOException {
        ContentResolver resolver = context.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) {
                return null;
            }
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                Math.max(1, targetWidth),
                Math.max(1, targetHeight));
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) {
                return null;
            }
            return BitmapFactory.decodeStream(stream, null, options);
        }
    }

    static int calculateSampleSize(
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight) {
        double coverScale = Math.max(
                (double) targetWidth / sourceWidth,
                (double) targetHeight / sourceHeight);
        double minimumWidth = sourceWidth * coverScale / 2.0;
        double minimumHeight = sourceHeight * coverScale / 2.0;
        int sampleSize = 1;
        while (sourceWidth / (sampleSize * 2.0) >= minimumWidth
                && sourceHeight / (sampleSize * 2.0) >= minimumHeight) {
            sampleSize *= 2;
        }
        while ((long) Math.ceil((double) sourceWidth / sampleSize)
                * (long) Math.ceil((double) sourceHeight / sampleSize)
                > MAX_DECODE_PIXELS) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}
