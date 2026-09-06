package com.polaris.timetable.update;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
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
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.polaris.timetable.testing.TextMatchers.withNavLabel;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 应用内更新入口测试（计划 14.4，冒烟层）：
 * “更多 → 更新 → 检查更新/自动检查更新”可达，连点不崩溃；
 * 自动检查默认开启由 UpdatePreferences 默认值单测保证，这里显式关闭并断网，
 * 覆盖 UI 可达性而不受远端发布状态影响。
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

    /**
     * 失败瞬间把当前活动窗口视图树（类名/文本/坐标）打印到测试 stdout。
     * 2026-09-06 教训：TestWatcher.failed() 在 @After closeScenario 与 GrantPermissionRule
     * 清理之后才执行，此时 Activity 必已销毁，onActivity 转储只会 NPE（CI 与本地一致，
     * 即 09da89b 的转储从未生效）。改为 Espresso FailureHandler：失败抛出前
     * Activity 仍存活，用 UiAutomation.getRootInActiveWindow() 直接抓当前窗口。
     */
    @Before
    public void installFailureDumper() {
        Espresso.setFailureHandler((error, matcher) -> {
            try {
                android.view.accessibility.AccessibilityNodeInfo root =
                        androidx.test.platform.app.InstrumentationRegistry
                                .getInstrumentation().getUiAutomation().getRootInActiveWindow();
                if (root != null) {
                    StringBuilder sb = new StringBuilder();
                    dumpNode(root, sb, 0);
                    System.out.println("==== ACTIVE WINDOW ON FAILURE (len=" + sb.length() + ") ====");
                    for (int i = 0; i < sb.length(); i += 3800) {
                        System.out.println(sb.substring(i, Math.min(sb.length(), i + 3800)));
                    }
                } else {
                    System.out.println("==== ACTIVE WINDOW ON FAILURE: getRootInActiveWindow()=null（无焦点窗口）====");
                }
            } catch (Throwable t) {
                System.out.println("==== ACTIVE WINDOW DUMP FAILED: " + t);
            }
            new androidx.test.espresso.base.DefaultFailureHandler(
                    androidx.test.platform.app.InstrumentationRegistry
                            .getInstrumentation().getTargetContext())
                    .handle(error, matcher);
        });
    }

    /** 转储无障碍节点树：类名/文本/描述/屏幕坐标/可见性。 */
    private static void dumpNode(android.view.accessibility.AccessibilityNodeInfo node,
                                 StringBuilder sb, int depth) {
        if (node == null) {
            return;
        }
        for (int i = 0; i < depth; i++) {
            sb.append(' ');
        }
        sb.append(node.getClassName());
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            sb.append(" text=\"").append(text).append('"');
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0) {
            sb.append(" cd=\"").append(desc).append('"');
        }
        android.graphics.Rect r = new android.graphics.Rect();
        node.getBoundsInScreen(r);
        sb.append(" @(").append(r.left).append(',').append(r.top).append(',')
                .append(r.right).append(',').append(r.bottom).append(')');
        sb.append(" shown=").append(node.isVisibleToUser());
        sb.append('\n');
        if (sb.length() > 60000) {
            sb.append("...(truncated)\n");
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            dumpNode(node.getChild(i), sb, depth + 1);
        }
    }

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
        // 自动检查默认开启（1.27.9）：显式关闭，避免启动 5s 后的自动检查在
        // 远端存在旧发布时弹出「发现新版本」对话框遮挡被测页面；
        // 同时开飞行模式让手动检查静默失败——本类只测入口可达性，不做网络态断言。
        new UpdatePreferences(UpdatePreferences.sharedPreferenceStore(targetContext()))
                .setAutoCheckEnabled(false);
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("cmd connectivity airplane-mode enable");
        // CI 模拟器在 Gradle 构建期间闲置会休眠失焦（RootViewWithoutFocusException：
        // has-window-focus=false 等 10s 超时），且失败形态随窗口焦点漂移。
        // 测试前点亮+解锁+充电时保持唤醒，把焦点状态固定下来。
        wakeScreenAndDismissKeyguard();
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("settings put global stay_on_while_plugged_in 7");
    }

    @After
    public void closeScenario() {
        if (scenario != null) {
            scenario.close();
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("cmd connectivity airplane-mode disable");
    }

    /**
     * 打开「更多」设置页：先滚到卡片可见，再点卡片顶部。
     * 2026-09-06 修复：小屏（含 CI 模拟器）上 scrollTo 会把卡片停在视口底部，
     * 而悬浮底部导航是后加的浮层、z 轴更高，卡片下半部与导航区域重叠；
     * 点卡片中心会命中导航的「计划」页签，app 直接切到计划页，设置行永远找不到
     * （表现为仅 CI 复现的「行缺失」NoMatchingViewException）。点卡片顶部可避开导航。
     */
    private void openMoreSettingsPage() {
        // 不走「点我的页→点更多卡」的坐标点击：悬浮底部导航是后加的浮层、z 轴更高，
        // 小屏上「更多」卡滚到位后仍与导航区域重叠，Espresso 点其中心会命中
        // 导航的「计划」页签、app 直接切走，设置行永远找不到（2026-09-06 定位的
        // 「仅 CI 复现行缺失」根因）。导航可用性由 app 侧底部留白修复保证，
        // 本测试只负责断言「更多」页内更新入口行可达，不测坐标几何。
        scenario.onActivity(MainActivity::openMoreSettings);
    }

    @Test
    public void checkUpdateRowsAreReachableInMorePage() {
        scenario = ActivityScenario.launch(MainActivity.class);
        onView(withNavLabel("我的")).perform(click());
        openMoreSettingsPage();
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
        openMoreSettingsPage();
        onView(withText(R.string.settings_row_check_update)).perform(scrollTo(), click());
        // 防重复：立即再点一次不应崩溃（协调器会忽略进行中的重复检查）。
        onView(withText(R.string.settings_row_check_update)).perform(scrollTo(), click());
        onView(withText(R.string.settings_row_version)).perform(scrollTo());
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
