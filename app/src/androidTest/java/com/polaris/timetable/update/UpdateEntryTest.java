package com.polaris.timetable.update;

import android.Manifest;

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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.polaris.timetable.testing.TextMatchers.withNavLabel;

/**
 * 应用内更新入口测试（计划 14.4，冒烟层）：
 * “更多 → 关于 → 检查更新/自动检查更新”可达，连点不崩溃；
 * 自动检查开关默认关闭由 UpdatePreferences 默认值单测保证，这里覆盖 UI 可达性。
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
}
