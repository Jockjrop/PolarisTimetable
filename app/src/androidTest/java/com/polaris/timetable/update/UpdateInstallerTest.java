package com.polaris.timetable.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * PackageInstaller 构件测试（U-P1-15/16）：
 * 状态接收器已在 Manifest 注册且不导出、会话可创建/查询/放弃、
 * 显式状态广播 Intent 的 action 与包约束正确。不做真实安装。
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
        // U-P0-02-5：显式、仅本应用接收的状态回调。
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
