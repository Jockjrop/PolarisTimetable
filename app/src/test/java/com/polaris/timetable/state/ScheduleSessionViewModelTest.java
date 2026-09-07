package com.polaris.timetable.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 会话级 ViewModel 默认值契约：新会话必须回退到「当前日期周 + 课表页签」，
 * 由调用方以 currentWeek &gt; 0 判断是否为恢复路径。
 */
public class ScheduleSessionViewModelTest {

    @Test
    public void newSession_currentWeekUnset() {
        assertEquals(0, new ScheduleSessionViewModel().currentWeek);
    }

    @Test
    public void newSession_activeTabDefaultsToSchedule() {
        assertEquals(0, new ScheduleSessionViewModel().activeTab);
    }

    @Test
    public void sessionStateSurvivesAssignment() {
        ScheduleSessionViewModel vm = new ScheduleSessionViewModel();
        vm.currentWeek = 7;
        vm.activeTab = 2;
        assertEquals(7, vm.currentWeek);
        assertEquals(2, vm.activeTab);
        assertTrue(vm.currentWeek > 0);
    }
}
