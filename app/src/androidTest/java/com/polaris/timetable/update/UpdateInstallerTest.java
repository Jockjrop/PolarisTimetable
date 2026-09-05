package com.polaris.timetable.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * PackageInstaller 构件测试：
 * 状态接收器已在 Manifest 注册且不导出、按 action 可被系统解析、
 * 会话可创建/查询/放弃、显式状态广播 Intent 的 action 与包约束正确。
 * 端到端投递测试覆盖“系统回传 → 接收器 → 协调器暂存确认 Intent”链路。
 * 不做真实安装。
 */
@RunWith(AndroidJUnit4.class)
public class UpdateInstallerTest {

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences(UpdatePreferences.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void installStatusReceiverIsDeclaredAndNotExported() throws Exception {
        // 显式、仅本应用接收的状态回调。
        ComponentName component = new ComponentName(context, UpdateInstallStatusReceiver.class);
        PackageManager pm = context.getPackageManager();
        android.content.pm.ComponentInfo info = pm.getReceiverInfo(component, 0);
        assertNotNull(info);
        assertTrue(!info.exported);
    }

    @Test
    public void statusBroadcastActionIsExplicitAndPackageScoped() {
        Intent status = new Intent(UpdateInstaller.ACTION_INSTALL_STATUS);
        status.setPackage(context.getPackageName());
        status.putExtra(UpdateInstaller.EXTRA_SESSION_ID, 12345);
        assertEquals(UpdateInstaller.ACTION_INSTALL_STATUS, status.getAction());
        assertEquals(context.getPackageName(), status.getPackage());
        assertEquals(12345, status.getIntExtra(UpdateInstaller.EXTRA_SESSION_ID, -1));
    }

    @Test
    public void statusBroadcastActionIsResolvableBySystem() {
        // 系统经 PendingIntent 回传的广播只带 action + 包名（无显式组件），
        // Manifest 接收器必须声明 intent-filter 才能被解析到，否则广播被静默丢弃，
        // 表现为“点安装没反应”。此处直接验证系统解析结果。
        Intent status = new Intent(UpdateInstaller.ACTION_INSTALL_STATUS);
        status.setPackage(context.getPackageName());
        List<ResolveInfo> resolved = context.getPackageManager()
                .queryBroadcastReceivers(status, 0);
        ComponentName expected = new ComponentName(context, UpdateInstallStatusReceiver.class);
        boolean found = false;
        for (ResolveInfo info : resolved) {
            // 广播接收器的解析结果填充 activityInfo（接收器即特殊 Activity 记录）。
            if (info.activityInfo != null
                    && expected.equals(new ComponentName(info.activityInfo.packageName,
                    info.activityInfo.name))) {
                found = true;
                break;
            }
        }
        assertTrue("安装状态广播无法解析到 UpdateInstallStatusReceiver（缺 intent-filter）", found);
    }

    @Test
    public void statusBroadcastReachesReceiverAndStoresConfirmIntent() throws Exception {
        // 端到端回归：本应用 uid 发送与系统回传一致的广播，
        // 接收器必须收到并交协调器在后台场景暂存确认 Intent。
        UpdateCoordinator.acquire(context, null);
        context.getSharedPreferences(UpdatePreferences.FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        try {
            Intent confirm = new Intent("com.polaris.timetable.testing.CONFIRM_INSTALL")
                    .setData(Uri.parse("package:" + context.getPackageName()));
            Intent status = new Intent(UpdateInstaller.ACTION_INSTALL_STATUS);
            status.setPackage(context.getPackageName());
            status.putExtra(UpdateInstaller.EXTRA_SESSION_ID, 9101);
            status.putExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_PENDING_USER_ACTION);
            status.putExtra(Intent.EXTRA_INTENT, confirm);
            context.sendBroadcast(status);
            UpdatePreferences prefs = new UpdatePreferences(
                    UpdatePreferences.sharedPreferenceStore(context));
            long deadline = SystemClock.uptimeMillis() + 5000L;
            while (SystemClock.uptimeMillis() < deadline
                    && prefs.pendingConfirmIntent() == null) {
                Thread.sleep(100L);
            }
            String stored = prefs.pendingConfirmIntent();
            assertNotNull("状态广播未被接收器消费：确认 Intent 未暂存", stored);
            assertTrue(stored.contains("com.polaris.timetable.testing.CONFIRM_INSTALL"));
        } finally {
            context.getSharedPreferences(UpdatePreferences.FILE_NAME, Context.MODE_PRIVATE)
                    .edit().clear().commit();
        }
    }

    @Test
    public void sessionCanBeCreatedQueriedAndAbandoned() throws java.io.IOException {
        PackageInstaller installer =
                context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(16L);
        int sessionId = installer.createSession(params);
        PackageInstaller.SessionInfo info = installer.getSessionInfo(sessionId);
        assertNotNull(info);
        UpdateInstaller.abandonSession(context, sessionId);
        // abandon 后查询不到该会话（或状态已终结）。
        PackageInstaller.SessionInfo after = installer.getSessionInfo(sessionId);
        assertTrue(after == null || !after.isActive());
    }

    @Test
    public void abandonUnknownSessionDoesNotCrash() {
        UpdateInstaller.abandonSession(context, -1);
        UpdateInstaller.abandonSession(context, Integer.MAX_VALUE);
        // 不抛异常即通过（会话不存在时应静默）。
    }
}
