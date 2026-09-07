package com.polaris.timetable.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * WeekNavigationController 周值决策契约：四条切换路径（按钮翻周 / 回到本周 /
 * 选择器跳周 / 板面滑动回写）的钳制、无变化短路、板面定位与动画参数，
 * 与抽取前 MainActivity 内联行为一一对应。
 */
public class WeekNavigationControllerTest {

    /** 录制型宿主：记录调用序列与参数，周值由测试预设。 */
    private static final class RecordingHost implements WeekNavigationController.Host {
        int currentWeek = 5;
        int todayWeek = 5;
        int semesterWeeks = 18;

        final List<String> calls = new ArrayList<>();
        final List<Integer> applied = new ArrayList<>();
        final List<Integer> positioned = new ArrayList<>();
        final List<Integer> transitions = new ArrayList<>();
        boolean captured;

        @Override
        public int currentWeek() {
            return currentWeek;
        }

        @Override
        public int todayWeek() {
            return todayWeek;
        }

        @Override
        public int semesterWeeks() {
            return semesterWeeks;
        }

        @Override
        public void applyCurrentWeek(int week) {
            calls.add("apply");
            applied.add(week);
            currentWeek = week;
        }

        @Override
        public void positionBoardAt(int week) {
            calls.add("position");
            positioned.add(week);
        }

        @Override
        public void captureBoardForTransition() {
            calls.add("capture");
            captured = true;
        }

        @Override
        public void playWeekTransition(int delta) {
            calls.add("transition");
            transitions.add(delta);
        }

        @Override
        public void refreshAnimatedWeekSwitch() {
            calls.add("refreshAnimated");
        }

        @Override
        public void refreshWeekDependentUi() {
            calls.add("refreshWeekDependent");
        }
    }

    // ===== clampWeek =====

    @Test
    public void clampWeek_lowerBound() {
        assertEquals(1, WeekNavigationController.clampWeek(-3, 18));
        assertEquals(1, WeekNavigationController.clampWeek(0, 18));
    }

    @Test
    public void clampWeek_upperBound() {
        assertEquals(18, WeekNavigationController.clampWeek(19, 18));
    }

    @Test
    public void clampWeek_withinRangeUnchanged() {
        assertEquals(7, WeekNavigationController.clampWeek(7, 18));
    }

    @Test
    public void clampWeek_invalidSemesterWeeksFallsBackToOne() {
        assertEquals(1, WeekNavigationController.clampWeek(5, 0));
        assertEquals(1, WeekNavigationController.clampWeek(5, -2));
    }

    // ===== returnArrowPointsLeft =====

    @Test
    public void returnArrowPointsLeft_whenBrowsingFutureWeeks() {
        // 浏览的周在 todayWeek 之后：本周页面在左侧，箭头向左。
        assertTrue(WeekNavigationController.returnArrowPointsLeft(8, 3));
    }

    @Test
    public void returnArrowPointsRight_whenBrowsingPastWeeks() {
        // 浏览的周在 todayWeek 之前：本周页面在右侧，箭头向右。
        assertFalse(WeekNavigationController.returnArrowPointsLeft(2, 6));
    }

    // ===== changeWeek =====

    @Test
    public void changeWeek_appliesClampedTargetAndPlaysOriginalDelta() {
        RecordingHost host = new RecordingHost();
        WeekNavigationController controller = new WeekNavigationController(host);

        assertTrue(controller.changeWeek(2));

        assertEquals(7, host.currentWeek);
        assertEquals(Integer.valueOf(7), host.applied.get(0));
        assertTrue(host.captured);
        assertEquals(Integer.valueOf(2), host.transitions.get(0));
        assertEquals("capture", host.calls.get(0));
        assertEquals("apply", host.calls.get(1));
        assertEquals("refreshAnimated", host.calls.get(2));
        assertEquals("transition", host.calls.get(3));
    }

    @Test
    public void changeWeek_clampedAtSemesterEnd() {
        RecordingHost host = new RecordingHost();
        host.currentWeek = 17;
        WeekNavigationController controller = new WeekNavigationController(host);

        assertTrue(controller.changeWeek(5));

        assertEquals(18, host.currentWeek);
        // 动画 delta 与抽取前一致：使用传入的原始 delta，而非实际差值。
        assertEquals(Integer.valueOf(5), host.transitions.get(0));
    }

    @Test
    public void changeWeek_noOpWhenAlreadyAtBound() {
        RecordingHost host = new RecordingHost();
        host.currentWeek = 18;
        WeekNavigationController controller = new WeekNavigationController(host);

        assertFalse(controller.changeWeek(3));
        assertTrue(host.calls.isEmpty());
        assertFalse(host.captured);
    }

    // ===== returnToCurrentWeek =====

    @Test
    public void returnToCurrentWeek_appliesTodayWeekAndRealDelta() {
        RecordingHost host = new RecordingHost();
        host.currentWeek = 9;
        host.todayWeek = 3;
        WeekNavigationController controller = new WeekNavigationController(host);

        assertTrue(controller.returnToCurrentWeek());

        assertEquals(3, host.currentWeek);
        assertTrue(host.captured);
        assertEquals(Integer.valueOf(-6), host.transitions.get(0));
        assertEquals("refreshAnimated", host.calls.get(2));
    }

    @Test
    public void returnToCurrentWeek_noOpWhenAlreadyOnToday() {
        RecordingHost host = new RecordingHost();
        host.currentWeek = 5;
        host.todayWeek = 5;
        WeekNavigationController controller = new WeekNavigationController(host);

        assertFalse(controller.returnToCurrentWeek());
        assertTrue(host.calls.isEmpty());
    }

    // ===== switchToWeek =====

    @Test
    public void switchToWeek_positionsBoardAndRefreshesWeekDependentUi() {
        RecordingHost host = new RecordingHost();
        WeekNavigationController controller = new WeekNavigationController(host);

        assertTrue(controller.switchToWeek(12));

        assertEquals(12, host.currentWeek);
        assertEquals(Integer.valueOf(12), host.positioned.get(0));
        assertFalse(host.captured);
        assertEquals("apply", host.calls.get(0));
        assertEquals("position", host.calls.get(1));
        assertEquals("refreshWeekDependent", host.calls.get(2));
    }

    @Test
    public void switchToWeek_clampsOutOfRangeTarget() {
        RecordingHost host = new RecordingHost();
        WeekNavigationController controller = new WeekNavigationController(host);

        assertTrue(controller.switchToWeek(99));
        assertEquals(18, host.currentWeek);
        assertEquals(Integer.valueOf(18), host.positioned.get(0));
    }

    @Test
    public void switchToWeek_noOpWhenAlreadyThere() {
        RecordingHost host = new RecordingHost();
        host.currentWeek = 4;
        WeekNavigationController controller = new WeekNavigationController(host);

        assertFalse(controller.switchToWeek(4));
        assertTrue(host.calls.isEmpty());
    }

    // ===== onBoardWeekChanged =====

    @Test
    public void onBoardWeekChanged_zeroDeltaShortCircuits() {
        RecordingHost host = new RecordingHost();
        WeekNavigationController controller = new WeekNavigationController(host);

        assertFalse(controller.onBoardWeekChanged(0));
        assertTrue(host.calls.isEmpty());
    }

    @Test
    public void onBoardWeekChanged_appliesAndRefreshesWithoutPositioning() {
        RecordingHost host = new RecordingHost();
        WeekNavigationController controller = new WeekNavigationController(host);

        assertTrue(controller.onBoardWeekChanged(-1));

        assertEquals(4, host.currentWeek);
        // 板面已自行落位：不得再次定位，也不得捕获快照或播放动画。
        assertTrue(host.positioned.isEmpty());
        assertFalse(host.captured);
        assertTrue(host.transitions.isEmpty());
        assertEquals("apply", host.calls.get(0));
        assertEquals("refreshWeekDependent", host.calls.get(1));
    }

    @Test
    public void onBoardWeekChanged_noOpWhenClampedToSameWeek() {
        RecordingHost host = new RecordingHost();
        host.currentWeek = 18;
        WeekNavigationController controller = new WeekNavigationController(host);

        assertFalse(controller.onBoardWeekChanged(1));
        assertTrue(host.calls.isEmpty());
    }
}
