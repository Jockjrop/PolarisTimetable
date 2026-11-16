package com.polaris.timetable.diagnostics;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 诊断期临时组件：未捕获异常与关键流程打点写入应用私有目录，
 * 随「导出诊断包」一起导出，用于定位只在特定机型复现的闪退。
 */
public final class CrashRecorder {

    private static final String CRASH_FILE = "diagnostics-crash.txt";
    private static final String TRACE_FILE = "diagnostics-trace.txt";
    private static final int TRACE_LIMIT_BYTES = 64 * 1024;
    private static final int READ_LIMIT_BYTES = 128 * 1024;

    private static volatile Context appContext;
    private static boolean installed;

    private CrashRecorder() {
    }

    public static synchronized void install(Context context) {
        if (installed || context == null) {
            return;
        }
        installed = true;
        appContext = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            recordCrash(thread, throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    /** 记录一条带时间戳的流程打点；未安装时静默忽略。 */
    public static void trace(String step) {
        Context context = appContext;
        if (context == null || step == null) {
            return;
        }
        append(context, TRACE_FILE, stamp() + "  " + step + "\n", TRACE_LIMIT_BYTES);
    }

    public static String readCrash(Context context) {
        return read(context, CRASH_FILE);
    }

    public static String readTrace(Context context) {
        return read(context, TRACE_FILE);
    }

    private static void recordCrash(Thread thread, Throwable throwable) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        StringWriter body = new StringWriter();
        PrintWriter writer = new PrintWriter(body);
        writer.println("时间：" + stamp());
        writer.println("版本：" + versionName(context));
        writer.println("系统：Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")");
        writer.println("设备：" + Build.MANUFACTURER + " " + Build.MODEL);
        writer.println("线程：" + (thread == null ? "未知" : thread.getName()));
        writer.println();
        throwable.printStackTrace(writer);
        writer.flush();
        append(context, CRASH_FILE, body.toString(), 0);
    }

    private static void append(Context context, String name, String text, int limitBytes) {
        File file = new File(context.getFilesDir(), name);
        try {
            if (limitBytes > 0 && file.length() + text.length() > limitBytes) {
                File previous = new File(file.getParentFile(), name + ".old");
                if (previous.exists() && !previous.delete()) {
                    Log.w("CrashRecorder", "cannot delete previous trace");
                }
                if (!file.renameTo(previous)) {
                    Log.w("CrashRecorder", "cannot rotate trace file");
                }
            }
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            Log.w("CrashRecorder", "append failed", exception);
        }
    }

    private static String read(Context context, String name) {
        if (context == null) {
            return "";
        }
        File file = new File(context.getFilesDir(), name);
        if (!file.isFile()) {
            return "";
        }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), READ_LIMIT_BYTES)];
            int count = input.read(buffer);
            return count > 0 ? new String(buffer, 0, count, StandardCharsets.UTF_8) : "";
        } catch (IOException exception) {
            return "";
        }
    }

    private static String versionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "?" : info.versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            return "?";
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date());
    }
}
