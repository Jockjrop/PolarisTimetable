package com.polaris.timetable.ui;

import android.Manifest;

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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.polaris.timetable.testing.TextMatchers.withNavLabel;
import static org.hamcrest.Matchers.not;

/**
 * 计划页悬浮新增菜单测试（1.26.0）：覆盖展开/收起、返回键、切页收起与页面隔离。
 * 交互语义断言放在本文件，避免继续堆入 {@link BottomNavNavigationTest}。
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PlanAddMenuTest {

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

    private void launchOnPlanTab() {
        scenario = ActivityScenario.launch(MainActivity.class);
        Espresso.onIdle();
        onView(withNavLabel("计划")).perform(click());
        Espresso.onIdle();
    }

    @Test
    public void planTab_showsCollapsedFabOnly() {
        launchOnPlanTab();
        onView(withContentDescription(R.string.plan_fab_cd_collapsed))
                .check(matches(isDisplayed()));
        // 收起态菜单项为 GONE（仍视图树内），因此断言不可见而不是不存在。
        onView(withText(R.string.plan_add_action_plan)).check(matches(not(isDisplayed())));
    }

    @Test
    public void tapFab_expandsThreeActions() {
        launchOnPlanTab();
        onView(withContentDescription(R.string.plan_fab_cd_collapsed)).perform(click());
        Espresso.onIdle();
        onView(withText(R.string.plan_add_action_plan)).check(matches(isDisplayed()));
        onView(withText(R.string.plan_add_action_deadline)).check(matches(isDisplayed()));
        onView(withText(R.string.plan_add_action_exam)).check(matches(isDisplayed()));
        // 展开后 FAB 语义切换为「收起」，展开状态不只靠旋转表达。
        onView(withContentDescription(R.string.plan_fab_cd_expanded))
                .check(matches(isDisplayed()));
    }

    @Test
    public void tapFabTwice_collapsesMenu() {
        launchOnPlanTab();
        onView(withContentDescription(R.string.plan_fab_cd_collapsed)).perform(click());
        Espresso.onIdle();
        onView(withContentDescription(R.string.plan_fab_cd_expanded)).perform(click());
        Espresso.onIdle();
        onView(withContentDescription(R.string.plan_fab_cd_collapsed))
                .check(matches(isDisplayed()));
        onView(withText(R.string.plan_add_action_plan)).check(matches(not(isDisplayed())));
    }

    @Test
    public void pressBack_collapsesMenu() {
        launchOnPlanTab();
        onView(withContentDescription(R.string.plan_fab_cd_collapsed)).perform(click());
        Espresso.onIdle();
        Espresso.pressBack();
        Espresso.onIdle();
        onView(withContentDescription(R.string.plan_fab_cd_collapsed))
                .check(matches(isDisplayed()));
        onView(withText(R.string.plan_add_action_plan)).check(matches(not(isDisplayed())));
    }

    @Test
    public void switchTab_collapsesMenuAndHidesFab() {
        launchOnPlanTab();
        onView(withContentDescription(R.string.plan_fab_cd_collapsed)).perform(click());
        Espresso.onIdle();
        onView(withNavLabel("课表")).perform(click());
        Espresso.onIdle();
        // 离开计划页后整页不可见，悬浮层随页面一起隐藏。
        onView(withContentDescription(R.string.plan_fab_cd_collapsed))
                .check(matches(not(isDisplayed())));
    }

    @Test
    public void tapAddExam_opensAcademicEventEditor() {
        launchOnPlanTab();
        onView(withContentDescription(R.string.plan_fab_cd_collapsed)).perform(click());
        Espresso.onIdle();
        onView(withText(R.string.plan_add_action_exam)).perform(click());
        Espresso.onIdle();
        // 直达事件编辑器，而不是先进入时间线。
        onView(withText(R.string.academic_editor_new))
                .inRoot(isDialog())
                .check(matches(isDisplayed()));
    }

    @Test
    public void tapNewPlan_opensPlanEditor() {
        launchOnPlanTab();
        onView(withContentDescription(R.string.plan_fab_cd_collapsed)).perform(click());
        Espresso.onIdle();
        onView(withText(R.string.plan_add_action_plan)).perform(click());
        Espresso.onIdle();
        // 计划编辑器标题与菜单项文案同名，故限定在对话框窗口内匹配。
        onView(withText(R.string.plan_editor_new))
                .inRoot(isDialog())
                .check(matches(isDisplayed()));
    }
}
