package com.polaris.timetable.export;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only provider limited to schedule files created in the dedicated export cache. */
public final class ExportFileProvider extends ContentProvider {
    public static final String EXPORT_DIRECTORY = "schedule_exports";

    public static Uri uriForFile(android.content.Context context, File file) {
        if (context == null || file == null) {
            throw new IllegalArgumentException("文件不能为空");
        }
        File safeFile = requireExportFile(context.getCacheDir(), file);
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".exports")
                .appendPath(safeFile.getName())
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        File file = fileForUri(uri);
        return file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")
                ? "application/pdf" : "image/png";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = fileForUri(uri);
        String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) {
                values[index] = file.getName();
            } else if (OpenableColumns.SIZE.equals(columns[index])) {
                values[index] = file.length();
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("导出文件仅支持读取");
        }
        File file = fileForUri(uri);
        if (!file.isFile()) {
            throw new FileNotFoundException("导出文件不存在");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("不支持写入");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("不支持写入");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("不支持删除");
    }

    private File fileForUri(Uri uri) {
        if (getContext() == null || uri == null || uri.getPathSegments().size() != 1) {
            throw new IllegalArgumentException("无效的导出文件地址");
        }
        return requireExportFile(getContext().getCacheDir(),
                new File(new File(getContext().getCacheDir(), EXPORT_DIRECTORY),
                        uri.getLastPathSegment()));
    }

    private static File requireExportFile(File cacheDirectory, File file) {
        try {
            File root = new File(cacheDirectory, EXPORT_DIRECTORY).getCanonicalFile();
            File candidate = file.getCanonicalFile();
            String lowerName = candidate.getName().toLowerCase(java.util.Locale.ROOT);
            if (!root.equals(candidate.getParentFile())
                    || (!lowerName.endsWith(".png") && !lowerName.endsWith(".pdf"))) {
                throw new IllegalArgumentException("文件不在允许的导出目录中");
            }
            return candidate;
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取导出文件路径", exception);
        }
    }
}
