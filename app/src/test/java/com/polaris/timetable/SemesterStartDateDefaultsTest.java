package com.polaris.timetable;

import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;

public class SemesterStartDateDefaultsTest {
    @Test
    public void januaryUsesPreviousAutumnSemester() {
        assertEquals("2025/9/1", resolve(2026, Calendar.JANUARY, 31));
    }

    @Test
    public void februaryFirstThroughJulyNinthUseSpringSemester() {
        assertEquals("2026/3/1", resolve(2026, Calendar.FEBRUARY, 1));
        assertEquals("2026/3/1", resolve(2026, Calendar.JULY, 9));
    }

    @Test
    public void julyTenthAndLaterUseAutumnSemester() {
        assertEquals("2026/9/1", resolve(2026, Calendar.JULY, 10));
        assertEquals("2026/9/1", resolve(2026, Calendar.DECEMBER, 31));
    }

    @Test
    public void createsMatchingAcademicSemesterNames() {
        assertEquals("2025-2026学年第1学期", resolveSemester(2026, Calendar.JANUARY, 31));
        assertEquals("2025-2026学年第2学期", resolveSemester(2026, Calendar.FEBRUARY, 1));
        assertEquals("2026-2027学年第1学期", resolveSemester(2026, Calendar.JULY, 10));
    }

    private String resolve(int year, int month, int day) {
        Calendar date = Calendar.getInstance();
        date.clear();
        date.set(year, month, day);
        return SemesterStartDateDefaults.resolve(date);
    }

    private String resolveSemester(int year, int month, int day) {
        Calendar date = Calendar.getInstance();
        date.clear();
        date.set(year, month, day);
        return SemesterStartDateDefaults.resolveSemesterName(date);
    }
}
