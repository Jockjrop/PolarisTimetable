package com.polaris.timetable.ui;

import android.Manifest;

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
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * 底部导航测试:手机竖屏三 tab(课表/计划/我的)切换后对应页面正确呈现。
 * 覆盖重构计划阶段 4-2 BottomNavView 的 Host 回调路由。
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class BottomNavNavigationTest {

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
    public void tapMy_thenMyPageVisible() {
        launch();
        onView(withText("我的")).perform(click());
        Espresso.onIdle();
        // 我的页至少呈现一个入口卡片(课表设置)
        onView(withText("课表设置")).check(matches(isDisplayed()));
    }

    @Test
    public void tapPlan_thenPlanPageVisible() {
        launch();
        onView(withText("计划")).perform(click());
        Espresso.onIdle();
        // 计划页呈现「新建计划」按钮(空状态时提示文字也可见)
        onView(withText("＋ 新建计划")).check(matches(isDisplayed()));
    }

    @Test
    public void tabRoundTrip_returnsToSchedule() {
        launch();
        onView(withText("我的")).perform(click());
        Espresso.onIdle();
        onView(withText("课表")).perform(click());
        Espresso.onIdle();
        // 回到课表后,空课表导入按钮重新可见
        onView(withContentDescription("导入课表")).check(matches(isDisplayed()));
    }
}
