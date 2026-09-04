package com.polaris.timetable.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 基于 PackageInstaller Session 的现代安装链（改进计划 U-P0-02）：
 * - MODE_FULL_INSTALL + setAppPackageName/setSize 创建会话。
 * - 已验证 APK 流式写入 openWrite 并 fsync 后 commit。
 * - 状态回调用显式、仅本应用接收的广播（UpdateInstallStatusReceiver）。
 * - targetSdk 31+ 使用 FLAG_MUTABLE PendingIntent（系统需要回填确认 Intent）。
 * - commit 完成即删除本地 APK（Session 已接管数据）。
 * - 任何失败 abandon 会话，不留悬挂会话。
 */
public final class UpdateInstaller {

    /** 安装状态广播 action；receiver 在 Manifest 中声明且 exported=false，仅本应用可发。 */
    public static final String ACTION_INSTALL_STATUS =
            "com.polaris.timetable.action.INSTALL_STATUS";
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_VERSION_CODE = "version_code";

    private UpdateInstaller() {
    }

    /**
     * 创建会话、写入 APK 并提交安装。成功返回 sessionId；失败时已清理会话并抛出
     * 携带错误种类的异常，本地 APK 文件仅在提交成功后删除。
     */
    public static int createAndCommitSession(Context context, File apk, String packageName)
            throws InstallException {
        PackageManager packageManager = context.getPackageManager();
        PackageInstaller packageInstaller = packageManager.getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        params.setSize(apk.length());
        int sessionId = -1;
        PackageInstaller.Session session = null;
        OutputStream output = null;
        InputStream input = null;
        try {
            sessionId = packageInstaller.createSession(params);
            session = packageInstaller.openSession(sessionId);
            output = session.openWrite(apk.getName(), 0L, apk.length());
            input = new FileInputStream(apk);
            byte[] chunk = new byte[65536];
            int read;
            while ((read = input.read(chunk)) != -1) {
                output.write(chunk, 0, read);
            }
            output.flush();
            // openWrite 返回 OutputStream；底层为可 fsync 的文件流时强制落盘（U-P0-02-4）。
            if (output instanceof FileOutputStream) {
                ((FileOutputStream) output).getFD().sync();
            }
            closeQuietly(output);
            output = null;
            // PendingIntent 不是 IntentSender，必须取其底层 IntentSender 提交。
            session.commit(createStatusIntentSender(context, sessionId).getIntentSender());
            closeQuietly(session);
            session = null;
            // Session 已完整接管 APK，本地文件不再需要。
            if (!apk.delete() && apk.exists()) {
                apk.deleteOnExit();
            }
            return sessionId;
        } catch (IOException exception) {
            abandonQuietly(session, context, sessionId);
            throw new InstallException(UpdateError.STORAGE_WRITE_FAILED, sessionId);
        } catch (SecurityException exception) {
            abandonQuietly(session, context, sessionId);
            throw new InstallException(UpdateError.INSTALL_BLOCKED, sessionId);
        } catch (RuntimeException exception) {
            abandonQuietly(session, context, sessionId);
            throw new InstallException(UpdateError.NO_INSTALLER, sessionId);
        } finally {
            closeQuietly(input);
            closeQuietly(output);
            if (session != null) {
                // 会话已提交（成功路径已在上面置空），此处仅收尾未关闭的会话。
                try {
                    session.close();
                } catch (RuntimeException ignored) {
                    // 会话已结束时的收尾失败可忽略。
                }
            }
        }
    }

    private static void abandonQuietly(PackageInstaller.Session session, Context context,
                                       int sessionId) {
        if (session != null) {
            try {
                session.abandon();
            } catch (Exception ignored) {
                // 放弃失败不掩盖原始错误。
            }
            return;
        }
        abandonSession(context, sessionId);
    }

    public static void abandonSession(Context context, int sessionId) {
        if (sessionId < 0 || context == null) {
            return;
        }
        try {
            context.getPackageManager().getPackageInstaller().abandonSession(sessionId);
        } catch (RuntimeException ignored) {
            // 会话不存在或已结束时忽略。
        }
    }

    public static PackageInstaller.SessionInfo sessionInfo(Context context, int sessionId) {
        if (sessionId < 0 || context == null) {
            return null;
        }
        try {
            return context.getPackageManager().getPackageInstaller().getSessionInfo(sessionId);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static PendingIntent createStatusIntentSender(Context context, int sessionId) {
        Intent status = new Intent(ACTION_INSTALL_STATUS);
        status.setPackage(context.getPackageName());
        status.putExtra(EXTRA_SESSION_ID, sessionId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // targetSdk 31+ 必须显式声明可变性：系统需要向该 PendingIntent 回填确认 Intent。
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        return PendingIntent.getBroadcast(context, sessionId, status, flags);
    }

    /** 安装链失败：携带错误种类与（可能无效的）sessionId。 */
    public static final class InstallException extends IOException {
        public final UpdateError error;
        public final int sessionId;

        InstallException(UpdateError error, int sessionId) {
            super(error.name());
            this.error = error;
            this.sessionId = sessionId;
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // 关闭失败不影响结果。
        }
    }
}