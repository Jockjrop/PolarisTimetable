package com.polaris.timetable.ui;

import android.Manifest;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.polaris.timetable.Course;
import com.polaris.timetable.MainActivity;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.storage.ScheduleRepositoryTestSupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;

/**
 * 课表渲染与周切换测试:预置课程数据后,课程块必须渲染、左右滑动可切换周视图。
 * 覆盖重构计划阶段 6 矩阵中的「多节课叠加」「周切换」路径(经 ScheduleRepository 写入,
 * 与 PDF 导入后的渲染路径一致)。
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ScheduleBoardRenderTest {

    @Rule
    public GrantPermissionRule notificationPermission =
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS);

    private ActivityScenario<MainActivity> scenario;

    @Before
    public void seedCourseData() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ScheduleRepositoryTestSupport.clearAll(context);
        ScheduleRepository repository = new ScheduleRepository(context);
        // 第 1-20 周、周一第 1-2 节的高等数学,覆盖整个学期任意当前周
        repository.replaceFromLegacyCourses("default",
                Collections.singletonList(new Course(
                        0, 1, 2, "高等数学A", "1-20",
                        "教学楼101", "张老师", "")));
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
    public void seededCourse_rendersOnBoard() {
        launch();
        // 课程块 contentDescription 包含课程名
        onView(allOf(withContentDescription(containsString("高等数学A")), isDisplayed()))
                .check(matches(isDisplayed()));
    }

    @Test
    public void swipeRight_switchesWeekAndShowsReturnButton() {
        launch();
        onView(allOf(withContentDescription(containsString("高等数学A")), isDisplayed()))
                .check(matches(isDisplayed()));
        // 当前周为学期末(20),向左滑会越界;向右滑切到上一周(19)
        onView(withClassName(endsWith("ScheduleBoardView")))
                .perform(swipeRight());
        Espresso.onIdle();
        // 离开当前周后「回到本周」按钮出现,证明周切换生效
        onView(withContentDescription("回到本周"))
                .check(matches(isDisplayed()));
        // 课程仍在(课程覆盖 1-20 周)
        onView(allOf(withContentDescription(containsString("高等数学A")), isDisplayed()))
                .check(matches(isDisplayed()));
    }
}
