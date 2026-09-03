package com.polaris.timetable.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

public final class BackgroundImageLoader {
    private static final long MAX_DECODE_PIXELS = 4_000_000L;
    private static final int ORIENTATION_NORMAL = 1;
    private static final int ORIENTATION_FLIP_HORIZONTAL = 2;
    private static final int ORIENTATION_ROTATE_180 = 3;
    private static final int ORIENTATION_FLIP_VERTICAL = 4;
    private static final int ORIENTATION_TRANSPOSE = 5;
    private static final int ORIENTATION_ROTATE_90 = 6;
    private static final int ORIENTATION_TRANSVERSE = 7;
    private static final int ORIENTATION_ROTATE_270 = 8;

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
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            return applyExifOrientation(resolver, uri, bitmap);
        }
    }

    private static Bitmap applyExifOrientation(ContentResolver resolver, Uri uri, Bitmap bitmap) {
        if (bitmap == null) {
            return bitmap;
        }
        int orientation = StreamExifReader.readOrientation(resolver, uri);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1f, 1f);
                break;
            case ORIENTATION_ROTATE_180:
                matrix.setRotate(180f);
                break;
            case ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1f, -1f);
                break;
            case ORIENTATION_TRANSPOSE:
                matrix.setRotate(90f);
                matrix.postScale(-1f, 1f);
                break;
            case ORIENTATION_ROTATE_90:
                matrix.setRotate(90f);
                break;
            case ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90f);
                matrix.postScale(-1f, 1f);
                break;
            case ORIENTATION_ROTATE_270:
                matrix.setRotate(-90f);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap oriented = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (oriented != bitmap) {
                bitmap.recycle();
            }
            return oriented;
        } catch (OutOfMemoryError error) {
            return bitmap;
        }
    }

    /** androidx ExifInterface 自带流式解析实现，无最低 API 限制。 */
    private static final class StreamExifReader {
        static int readOrientation(ContentResolver resolver, Uri uri) {
            try (InputStream stream = resolver.openInputStream(uri)) {
                if (stream == null) {
                    return ORIENTATION_NORMAL;
                }
                return new androidx.exifinterface.media.ExifInterface(stream).getAttributeInt(
                        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                        ORIENTATION_NORMAL);
            } catch (IOException | RuntimeException exception) {
                return ORIENTATION_NORMAL;
            }
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
