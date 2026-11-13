package com.polaris.timetable.time;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AcademicCalendarTest {

    private static final String BUILT_IN = "{"
            + "\"days\":["
            + "{\"date\":\"2026/10/1\",\"kind\":\"holiday\",\"label\":\"国庆\"},"
            + "{\"date\":\"2026/10/2\",\"kind\":\"holiday\",\"label\":\"国庆\"},"
            + "{\"date\":\"2026/10/10\",\"kind\":\"makeup\",\"label\":\"国庆补班\"}"
            + "]}";

    @Test
    public void holidayReturnsNoWeekday() {
        AcademicCalendar calendar = AcademicCalendar.parse(BUILT_IN, "");

        assertTrue(calendar.isHoliday("2026/10/1"));
        assertEquals(AcademicCalendar.WEEKDAY_UNSPECIFIED,
                calendar.effectiveWeekday("2026/10/1", 3));
    }

    @Test
    public void makeupWithoutWeekdayFallsBackToActualWeekday() {
        AcademicCalendar calendar = AcademicCalendar.parse(BUILT_IN, "");

        assertTrue(calendar.isMakeup("2026/10/10"));
        assertTrue(calendar.needsMakeupWeekday("2026/10/10"));
        // 未指定补哪一天时不臆测，按真实星期六显示并提示用户去设置。
        assertEquals(5, calendar.effectiveWeekday("2026/10/10", 5));
    }

    @Test
    public void overrideCanSpecifyMakeupWeekday() {
        String overrides = AcademicCalendar.withOverride("", "2026/10/10", "makeup", 3);
        AcademicCalendar calendar = AcademicCalendar.parse(BUILT_IN, overrides);

        assertFalse(calendar.needsMakeupWeekday("2026/10/10"));
        assertEquals(3, calendar.effectiveWeekday("2026/10/10", 5));
    }

    @Test
    public void overrideCanCancelBuiltInHoliday() {
        String overrides = AcademicCalendar.withOverride("", "2026/10/1", "normal", -1);
        AcademicCalendar calendar = AcademicCalendar.parse(BUILT_IN, overrides);

        assertFalse(calendar.isHoliday("2026/10/1"));
        assertEquals(3, calendar.effectiveWeekday("2026/10/1", 3));
    }

    @Test
    public void overrideReplacesSameDateInsteadOfAppending() {
        String first = AcademicCalendar.withOverride("", "2026/10/10", "makeup", 1);
        String second = AcademicCalendar.withOverride(first, "2026/10/10", "makeup", 4);

        AcademicCalendar calendar = AcademicCalendar.parse(BUILT_IN, second);

        assertEquals(4, calendar.effectiveWeekday("2026/10/10", 5));
    }

    @Test
    public void withoutOverrideRestoresBuiltInRule() {
        String overrides = AcademicCalendar.withOverride("", "2026/10/10", "makeup", 2);
        String cleared = AcademicCalendar.withoutOverride(overrides, "2026/10/10");

        AcademicCalendar calendar = AcademicCalendar.parse(BUILT_IN, cleared);

        assertTrue(calendar.needsMakeupWeekday("2026/10/10"));
    }

    @Test
    public void unknownDateKeepsActualWeekday() {
        AcademicCalendar calendar = AcademicCalendar.parse(BUILT_IN, "");

        assertEquals(2, calendar.effectiveWeekday("2026/11/11", 2));
        assertFalse(calendar.isHoliday("2026/11/11"));
    }

    @Test
    public void malformedJsonDegradesToEmptyCalendar() {
        AcademicCalendar calendar = AcademicCalendar.parse("{not json", "[1,2,3]");

        assertEquals(1, calendar.effectiveWeekday("2026/10/1", 1));
        assertFalse(calendar.isHoliday("2026/10/1"));
    }

    @Test
    public void signatureChangesWhenRuleChanges() {
        AcademicCalendar plain = AcademicCalendar.parse(BUILT_IN, "");
        AcademicCalendar changed = AcademicCalendar.parse(BUILT_IN,
                AcademicCalendar.withOverride("", "2026/10/10", "makeup", 3));

        assertTrue(plain.signature() != changed.signature());
    }

    @Test
    public void allRulesSortsByRealDateOrder() {
        // 字符串序会把「10/10」排到「2/14」前面，这里必须按 yyyyMMdd 数值排。
        AcademicCalendar calendar = AcademicCalendar.parse(
                "{\"days\":["
                        + "{\"date\":\"2026/10/10\",\"kind\":\"makeup\"},"
                        + "{\"date\":\"2026/2/14\",\"kind\":\"makeup\"},"
                        + "{\"date\":\"2026/1/4\",\"kind\":\"makeup\"}"
                        + "]}", "");

        java.util.List<AcademicCalendar.DayRule> rules = calendar.allRules();

        assertEquals("2026/1/4", rules.get(0).date);
        assertEquals("2026/2/14", rules.get(1).date);
        assertEquals("2026/10/10", rules.get(2).date);
    }

    @Test
    public void overrideKeepsBuiltInLabel() {
        String overrides = AcademicCalendar.withOverride("", "2026/10/10", "makeup", 3);

        AcademicCalendar calendar = AcademicCalendar.parse(BUILT_IN, overrides);

        assertEquals("国庆补班", calendar.ruleFor("2026/10/10").label);
        assertEquals(3, calendar.effectiveWeekday("2026/10/10", 5));
    }

    @Test
    public void dateKeyAndWeekdayFollowProjectConvention() {
        Calendar date = Calendar.getInstance();
        date.set(2026, Calendar.OCTOBER, 1);

        assertEquals("2026/10/1", AcademicCalendar.dateKey(date));
        // 2026-10-01 是周四，索引 3（周一=0）。
        assertEquals(3, AcademicCalendar.weekdayOf(date));
    }
}
