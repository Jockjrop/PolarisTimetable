package com.polaris.timetable.ui;

import android.Manifest;
import android.content.pm.ActivityInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import com.polaris.timetable.MainActivity;
import com.polaris.timetable.storage.ScheduleRepositoryTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * 旋转测试:横竖屏切换触发 onConfigurationChanged → rebuildLayout() 全量重建,
 * 重建后空状态导入按钮与底部导航必须仍然完整可见(不依赖 Activity recreate)。
 * 覆盖重构计划阶段 2(Edge-to-Edge/Insets)与阶段 6 旋转回归项。
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class RotationTest {

    @Rule
    public GrantPermissionRule notificationPermission =
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS);

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void clearAppState() {
        ScheduleRepositoryTestSupport.clearAll(
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .getTargetContext());
    }

    @After
    public void closeScenario() {
        if (scenario != null) {
            scenario.close();
        }
    }

    private void launch() {
        scenario = ActivityScenario.launch(MainActivity.class);
        Espresso.onIdle();
    }

    @Test
    public void rotateToLandscape_uiStillRenders() {
        launch();
        scenario.onActivity(activity ->
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        Espresso.onIdle();
        // 横屏重建后核心 UI 完整
        onView(withContentDescription("导入课表")).check(matches(isDisplayed()));
        onView(withText("课表")).check(matches(isDisplayed()));
        onView(withText("我的")).check(matches(isDisplayed()));
    }

    @Test
    public void rotateBackToPortrait_uiStillRenders() {
        launch();
        scenario.onActivity(activity ->
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
        Espresso.onIdle();
        scenario.onActivity(activity ->
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        Espresso.onIdle();
        // 竖屏恢复后核心 UI 完整
        onView(withContentDescription("导入课表")).check(matches(isDisplayed()));
        onView(withText("计划")).check(matches(isDisplayed()));
        onView(withText("我的")).check(matches(isDisplayed()));
    }
}
