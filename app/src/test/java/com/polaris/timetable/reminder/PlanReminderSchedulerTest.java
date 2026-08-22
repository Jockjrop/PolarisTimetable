package com.polaris.timetable.reminder;

import static org.junit.Assert.assertEquals;

import com.polaris.timetable.model.StudyPlan;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class PlanReminderSchedulerTest {
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Shanghai");

    private static long millis(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(TZ);
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    @Test
    public void firstWeekStart_parsesDate_andAlignsToMonday() {
        long start = PlanReminderScheduler.firstWeekStartMillis("2026/3/3");
        // 2026-03-03 是周二，周一为 03-02 00:00。
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(start);
        assertEquals(2026, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, calendar.get(Calendar.MONTH));
        assertEquals(2, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK));
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY));
    }

    @Test
    public void firstWeekStart_invalidDate_fallsBackToDefaultSemesterStartMonday() {
        long start = PlanReminderScheduler.firstWeekStartMillis("not-a-date");
        // 统一委托 CourseTimeResolver：损坏文本回退默认学期起点 2026-03-03（周二），
        // 对齐到周一 03-02，与课表 UI 的周次锚点一致。
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(start);
        assertEquals(2026, calendar.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, calendar.get(Calendar.MONTH));
        assertEquals(2, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.MONDAY, calendar.get(Calendar.DAY_OF_WEEK));
    }

    @Test
    public void planTrigger_week1MondayDefaultTime_isEightAM() {
        StudyPlan plan = new StudyPlan("p", "预习", "", 1, 0,
                false, true, 8 * 60, 0L);
        long trigger = PlanReminderScheduler.planTriggerMillisInTimeZone(
                plan, millis(2026, 3, 2, 0, 0), TZ);
        assertEquals(millis(2026, 3, 2, 8, 0), trigger);
    }

    @Test
    public void planTrigger_week3Friday1430_isCorrectDayAndTime() {
        StudyPlan plan = new StudyPlan("p", "交报告", "", 3, 4,
                false, true, 14 * 60 + 30, 0L);
        long trigger = PlanReminderScheduler.planTriggerMillisInTimeZone(
                plan, millis(2026, 3, 2, 0, 0), TZ);
        // 第一周周一 03-02，第 3 周周五 = 03-02 + 2*7 + 4 = 03-20（周五）。
        assertEquals(millis(2026, 3, 20, 14, 30), trigger);
    }

    @Test
    public void planTrigger_weekEnds_rollsIntoNextMonth() {
        StudyPlan plan = new StudyPlan("p", "月末任务", "", 4, 6,
                false, true, 21 * 60, 0L);
        long trigger = PlanReminderScheduler.planTriggerMillisInTimeZone(
                plan, millis(2026, 3, 2, 0, 0), TZ);
        // 第 4 周周日 = 03-02 + 3*7 + 6 = 03-29（周日）。
        assertEquals(millis(2026, 3, 29, 21, 0), trigger);
    }

    @Test
    public void donePlan_hasNoReminder() {
        StudyPlan plan = new StudyPlan("p", "任务", "", 1, 0,
                true, true, 8 * 60, 0L);
        assertEquals(false, plan.hasReminder());
    }

    @Test
    public void disabledReminder_hasNoReminder() {
        StudyPlan plan = new StudyPlan("p", "任务", "", 1, 0,
                false, false, 8 * 60, 0L);
        assertEquals(false, plan.hasReminder());
    }
}
