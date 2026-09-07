package com.polaris.timetable.ui;

/**
 * 周导航控制器：集中四条周切换路径（按钮翻周、选择器跳周、板面滑动回写、
 * 回到本周）的目标周钳制与「无变化即不动作」判定。周值决策为纯静态方法
 * （无 Android 依赖）便于单测；当前周状态仍归 MainActivity 所有，宿主通过
 * {@link Host} 提供读取/写回与各路径差异化的刷新序列，行为零变化。
 *
 * <p>路径差异（与抽取前逐一对应）：
 * <ul>
 *   <li>{@code changeWeek}：捕获板面快照 → 换周 → 标题+重渲染 → 按传入 delta 播放过渡动画；</li>
 *   <li>{@code returnToCurrentWeek}：与 changeWeek 同路径，动画 delta 取实际差值，无变化时宿主仅刷新悬浮按钮；</li>
 *   <li>{@code switchToWeek}：无快照与动画，额外把板面定位到目标周，再走周依赖 UI 刷新序列；</li>
 *   <li>{@code onBoardWeekChanged}：板面已自行落位，只做钳制判定后走周依赖 UI 刷新序列。</li>
 * </ul>
 */
public final class WeekNavigationController {

    private final Host host;

    public WeekNavigationController(Host host) {
        this.host = host;
    }

    /** 宿主回调：由 MainActivity 实现，提供当前周读写、板面控制与刷新序列。 */
    public interface Host {
        int currentWeek();

        /** 今天对应的学期周（currentWeekFromDate），供「回到本周」使用。 */
        int todayWeek();

        int semesterWeeks();

        /** 写回 MainActivity.currentWeek（单一事实源仍在宿主）。 */
        void applyCurrentWeek(int week);

        /** switchToWeek 专用：把课表板定位到目标周（onBoardWeekChanged 路径不调用）。 */
        void positionBoardAt(int week);

        void captureBoardForTransition();

        void playWeekTransition(int delta);

        /** changeWeek / returnToCurrentWeek 的刷新序列：标题 + 重渲染。 */
        void refreshAnimatedWeekSwitch();

        /** switchToWeek / onBoardWeekChanged 的刷新序列：悬浮按钮、标题、今日概览、冲突摘要等。 */
        void refreshWeekDependentUi();
    }

    /** 把周号钳制到 [1, semesterWeeks]；semesterWeeks 非法（&lt;1）时返回 1。 */
    public static int clampWeek(int week, int semesterWeeks) {
        int max = Math.max(1, semesterWeeks);
        return Math.max(1, Math.min(max, week));
    }

    /**
     * 「回到本周」悬浮按钮的箭头方向：true = 指向左。板面为标准翻页，
     * 页码随周号递增（向左翻去更早的周）：浏览的周在今天所在周之后时，
     * 本周页面在左侧，箭头向左；浏览过去周时本周在右侧，箭头向右。
     */
    public static boolean returnArrowPointsLeft(int currentWeek, int todayWeek) {
        return currentWeek > todayWeek;
    }

    /** 按钮翻周：目标 = 当前 + delta 钳制；返回是否发生变更。动画使用传入的原始 delta。 */
    public boolean changeWeek(int delta) {
        int from = host.currentWeek();
        int target = clampWeek(from + delta, host.semesterWeeks());
        if (target == from) {
            return false;
        }
        host.captureBoardForTransition();
        host.applyCurrentWeek(target);
        host.refreshAnimatedWeekSwitch();
        host.playWeekTransition(delta);
        return true;
    }

    /** 回到本周：目标 = todayWeek；返回是否发生变更（false 时宿主刷新悬浮按钮可见性）。 */
    public boolean returnToCurrentWeek() {
        int from = host.currentWeek();
        int target = clampWeek(host.todayWeek(), host.semesterWeeks());
        if (target == from) {
            return false;
        }
        host.captureBoardForTransition();
        host.applyCurrentWeek(target);
        host.refreshAnimatedWeekSwitch();
        host.playWeekTransition(target - from);
        return true;
    }

    /** 选择器跳周：钳制后定位板面并走周依赖刷新序列；返回是否发生变更。 */
    public boolean switchToWeek(int week) {
        int from = host.currentWeek();
        int target = clampWeek(week, host.semesterWeeks());
        if (target == from) {
            return false;
        }
        host.applyCurrentWeek(target);
        host.positionBoardAt(target);
        host.refreshWeekDependentUi();
        return true;
    }

    /** 板面滑动落位回写：delta==0 或钳制后无变化时不做任何动作；返回是否发生变更。 */
    public boolean onBoardWeekChanged(int delta) {
        if (delta == 0) {
            return false;
        }
        int from = host.currentWeek();
        int target = clampWeek(from + delta, host.semesterWeeks());
        if (target == from) {
            return false;
        }
        host.applyCurrentWeek(target);
        host.refreshWeekDependentUi();
        return true;
    }
}
