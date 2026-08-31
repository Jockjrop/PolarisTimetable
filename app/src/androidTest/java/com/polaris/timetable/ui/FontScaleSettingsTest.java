package com.polaris.timetable.ui;

import android.Manifest;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.polaris.timetable.MainActivity;
import com.polaris.timetable.storage.ScheduleRepositoryTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * 字号 1.3x 下设置页渲染测试:模拟系统字体设为 1.3x 后,
 * 打开「课表设置」面板验证关键文本可见且不崩溃。
 * 覆盖重构计划阶段 6 矩阵中的「fontScale 1.3」回归项。
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class FontScaleSettingsTest {

    @Rule
    public GrantPermissionRule notificationPermission =
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS);

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void setFontScaleAndClear() {
        ScheduleRepositoryTestSupport.clearAll(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        // 设置系统字体缩放为 1.3x,模拟用户启用大字体
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("settings put system font_scale 1.3");
    }

    @After
    public void restoreFontScaleAndClose() {
        // 恢复系统字体缩放为 1.0x,避免影响其他测试
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("settings put system font_scale 1.0");
        if (scenario != null) {
            scenario.close();
        }
    }

    private void launch() {
        scenario = ActivityScenario.launch(MainActivity.class);
        Espresso.onIdle();
    }

    @Test
    public void settingsPageAtFontScale13_rendersWithoutCrash() {
        launch();
        // 导航到我的页
        onView(withText("我的")).perform(click());
        Espresso.onIdle();
        // 我的页卡片「课表设置」可见(≈dp 缩放后卡片应完整显示)
        onView(withText("课表设置")).check(matches(isDisplayed()));
        // 点击进入课表设置面板
        onView(withText("课表设置")).perform(click());
        Espresso.onIdle();
        // 设置页独有行「课表名称」可见,表明设置页面已渲染无崩溃
        onView(withText("课表名称")).check(matches(isDisplayed()));
    }
}