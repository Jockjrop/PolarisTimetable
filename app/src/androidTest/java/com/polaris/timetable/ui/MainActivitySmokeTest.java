package com.polaris.timetable.ui;

import android.Manifest;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import com.polaris.timetable.MainActivity;

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
import static com.polaris.timetable.testing.TextMatchers.withNavLabel;

/**
 * 冒烟测试:App 冷启动后,空状态界面与底部导航必须完整呈现。
 * 覆盖冷启动与空课表路径。
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivitySmokeTest {

    @Rule
    public GrantPermissionRule notificationPermission =
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS);

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void clearAppState() {
        com.polaris.timetable.storage.ScheduleRepositoryTestSupport.clearAll(
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
    public void emptyState_showsImportButton() {
        launch();
        // 空课表时应显示「导入课表」主按钮
        onView(withContentDescription("导入课表")).check(matches(isDisplayed()));
    }

    @Test
    public void bottomNav_showsThreeTabs() {
        launch();
        // 手机竖屏底部导航:课表 / 计划 / 我的 三 tab
        onView(withNavLabel("课表")).check(matches(isDisplayed()));
        onView(withNavLabel("计划")).check(matches(isDisplayed()));
        onView(withNavLabel("我的")).check(matches(isDisplayed()));
    }
}
