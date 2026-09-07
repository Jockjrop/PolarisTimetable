package com.polaris.timetable.state;

import androidx.lifecycle.ViewModel;

/**
 * 会话级视图状态：跨配置变更（深浅色切换、字号/语言/分屏调整等未在
 * Manifest configChanges 声明的场景）的 Activity 重建存活。旋转由
 * configChanges 处理，不经过重建，因此不依赖本类。
 * 仅承载无需持久化、但不应因重建而丢失的视图会话状态。
 */
public class ScheduleSessionViewModel extends ViewModel {

    /** 用户正在查看的周；0 表示尚未初始化（调用方回退到 currentWeekFromDate）。 */
    public int currentWeek = 0;

    /** 底部导航激活页签：0 课表 / 1 计划 / 2 我的。 */
    public int activeTab = 0;
}
