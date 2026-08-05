package com.polaris.timetable.importer;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import com.polaris.timetable.ScheduleParser;
import com.polaris.timetable.model.ParseResult;
import com.polaris.timetable.parser.SchoolParserModel;

public class PdfImportCoordinator {
    private static final String TAG = "PdfImportCoordinator";
    public interface Callback {
        void onImportStarted(String displayName);

        void onImportParsed(ParseResult result);

        void onImportFailed(Exception exception);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PdfImportCoordinator(Context context) {
        this.context = context.getApplicationContext();
    }

    public void importPdf(Uri uri, Callback callback) {
        importPdf(uri, SchoolParserModel.XUPT, callback);
    }

    public void importPdf(Uri uri, SchoolParserModel parserModel, Callback callback) {
        persistReadPermission(uri);
        callback.onImportStarted(displayName(uri));
        new Thread(() -> {
            try {
                ParseResult result = new ScheduleParser().parseDetailed(context, uri, parserModel);
                mainHandler.post(() -> callback.onImportParsed(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onImportFailed(exception));
            }
        }).start();
    }

    private void persistReadPermission(Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "PDF provider granted only temporary read access", exception);
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (SecurityException exception) {
            Log.w(TAG, "Unable to read the selected PDF display name", exception);
            return "已导入PDF课表";
        }
        return "已导入PDF课表";
    }
}
