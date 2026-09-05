package com.polaris.timetable.update;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import com.polaris.timetable.MainActivity;
import com.polaris.timetable.R;
import com.polaris.timetable.storage.ScheduleRepositoryTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.polaris.timetable.testing.TextMatchers.withNavLabel;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 应用内更新入口测试（计划 14.4，冒烟层）：
 * “更多 → 关于 → 检查更新/自动检查更新”可达，连点不崩溃；
 * 自动检查开关默认关闭由 UpdatePreferences 默认值单测保证，这里覆盖 UI 可达性。
 * 前台确认页启动路径为端到端回归：模拟系统回传 PENDING_USER_ACTION 广播，
 * 断言确认页真的被启动到前台（修复“点安装没反应”的回归防护）。
 * 检查结果依赖网络环境，不做网络态断言，避免仪器测试抖动。
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class UpdateEntryTest {

    @Rule
    public GrantPermissionRule notificationPermission =
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS);

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void clearAppState() {
        ScheduleRepositoryTestSupport.clearAll(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .getTargetContext());
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext()
                .getSharedPreferences(UpdatePreferences.FILE_NAME,
                        android.content.Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @After
    public void closeScenario() {
        if (scenario != null) {
            scenario.close();
        }
    }

    @Test
    public void checkUpdateRowsAreReachableInMorePage() {
        scenario = ActivityScenario.launch(MainActivity.class);
        onView(withNavLabel("我的")).perform(click());
        onView(withText(R.string.my_card_more)).perform(click());
        onView(withText(R.string.settings_row_check_update)).check(matches(isDisplayed()));
        onView(withText(R.string.settings_row_auto_check_update)).check(matches(isDisplayed()));
        onView(withText(R.string.settings_row_contact)).check(matches(isDisplayed()));
        onView(withText(R.string.settings_row_github)).check(matches(isDisplayed()));
        onView(withText(R.string.settings_row_gitee)).check(matches(isDisplayed()));
    }

    @Test
    public void tappingCheckRowRepeatedlyDoesNotCrash() {
        scenario = ActivityScenario.launch(MainActivity.class);
        onView(withNavLabel("我的")).perform(click());
        onView(withText(R.string.my_card_more)).perform(click());
        onView(withText(R.string.settings_row_check_update)).perform(click());
        // 防重复：立即再点一次不应崩溃（协调器会忽略进行中的重复检查）。
        onView(withText(R.string.settings_row_check_update)).perform(click());
        onView(withText(R.string.settings_row_version)).check(matches(isDisplayed()));
    }

    @Test
    public void pendingUserActionStartsConfirmActivityWhenForeground() throws Exception {
        // 端到端回归（修复“点安装没反应”）：前台收到系统回传的 PENDING_USER_ACTION
        // 广播时，必须把确认页（此处用系统设置页代替安装确认页）启动到前台，
        // 而不是把确认 Intent 暂存或静默丢弃。修复前 Manifest 缺 intent-filter，
        // 广播被系统解析不到而丢弃，本测试会在第一步超时失败。
        wakeScreenAndDismissKeyguard();
        scenario = ActivityScenario.launch(MainActivity.class);
        scenario.onActivity(activity -> {
            // 确保 Activity 已附着并完成 onResumed 绑定。
        });
        UpdatePreferences prefs = new UpdatePreferences(
                UpdatePreferences.sharedPreferenceStore(targetContext()));
        prefs.clearPendingConfirmIntent();
        Intent confirm = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + targetContext().getPackageName()));
        Intent status = new Intent(UpdateInstaller.ACTION_INSTALL_STATUS);
        status.setPackage(targetContext().getPackageName());
        status.putExtra(UpdateInstaller.EXTRA_SESSION_ID, 9102);
        status.putExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_PENDING_USER_ACTION);
        status.putExtra(Intent.EXTRA_INTENT, confirm);
        targetContext().sendBroadcast(status);

        long deadline = SystemClock.uptimeMillis() + 5000L;
        String resumed = "";
        while (SystemClock.uptimeMillis() < deadline) {
            resumed = resumedActivityName();
            if (resumed.contains("com.android.settings")) {
                break;
            }
            Thread.sleep(200L);
        }
        assertTrue("确认页未被启动到前台（mResumedActivity: " + resumed + "）",
                resumed.contains("com.android.settings"));
        assertNull("前台场景不应把确认 Intent 暂存为后台恢复", prefs.pendingConfirmIntent());
    }

    /** 息屏/锁屏会吞掉 startActivity 的可见效果，测试前强制亮屏并解锁。 */
    private static void wakeScreenAndDismissKeyguard() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("input keyevent KEYCODE_WAKEUP");
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("wm dismiss-keyguard");
        try {
            Thread.sleep(500L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** 读取 mResumedActivity 行，返回当前栈顶已恢复的 Activity 记录。 */
    private static String resumedActivityName() {
        ParcelFileDescriptor pfd = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getUiAutomation()
                .executeShellCommand("dumpsys activity activities | grep -i resumedactivity");
        try (ParcelFileDescriptor.AutoCloseInputStream input =
                     new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            StringBuilder builder = new StringBuilder();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            return builder.toString();
        } catch (IOException exception) {
            return "";
        }
    }

    private static android.content.Context targetContext() {
        return androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
    }
}
