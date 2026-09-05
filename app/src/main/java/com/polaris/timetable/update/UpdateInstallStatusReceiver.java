package com.polaris.timetable.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.content.IntentCompat;

/**
 * PackageInstaller 安装状态接收器。
 * Manifest 中声明且 exported=false，配合 setPackage 的显式广播，仅接收本应用安装会话状态。
 * 收到状态后交给 UpdateCoordinator 处理（前台启动确认 / 后台暂存确认 Intent / 收尾清理）。
 */
public final class UpdateInstallStatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !UpdateInstaller.ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            return;
        }
        int sessionId = intent.getIntExtra(UpdateInstaller.EXTRA_SESSION_ID, -1);
        int status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,
                android.content.pm.PackageInstaller.STATUS_FAILURE);
        // 系统通过可变 PendingIntent 回填的确认 Intent（STATUS_PENDING_USER_ACTION 时有效）。
        Intent confirmIntent = IntentCompat.getParcelableExtra(intent,
                Intent.EXTRA_INTENT, Intent.class);
        UpdateCoordinator.handleInstallStatus(context.getApplicationContext(),
                sessionId, status, confirmIntent);
    }
}
