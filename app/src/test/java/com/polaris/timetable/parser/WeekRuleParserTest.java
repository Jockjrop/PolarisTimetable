package com.polaris.timetable.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.model.WeekRule;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WeekRuleParserTest {
    private final WeekRuleParser parser = new WeekRuleParser();

    @Test
    public void parsesRequiredWeekExpressions() {
        assertWeeks("1-8周", 8, 1, 2, 3, 4, 5, 6, 7, 8);
        assertWeeks("1-8周 单周", 8, 1, 3, 5, 7);
        assertWeeks("1-8周 双周", 8, 2, 4, 6, 8);
        assertWeeks("1-8周(单)", 8, 1, 3, 5, 7);
        assertWeeks("1-8周（双）", 8, 2, 4, 6, 8);
        assertWeeks("2、5-6周", 8, 2, 5, 6);
        assertWeeks("1,3,5周", 8, 1, 3, 5);
        assertWeeks("1，3，5周", 8, 1, 3, 5);
        assertWeeks("5周", 8, 5);
    }

    @Test
    public void parsesMultipleRangesAndCommonRangeConnectors() {
        assertWeeks("1-2周，5–6周、9至10周", 12, 1, 2, 5, 6, 9, 10);
        assertWeeks("1—2周,5～6周", 8, 1, 2, 5, 6);
        assertWeeks("1－2周", 4, 1, 2);
    }

    @Test
    public void parityAlsoAppliesToExplicitAndMixedWeekLists() {
        assertWeeks("2、5-6周 单周", 8, 5);
        assertWeeks("2周 单周", 8);
        assertWeeks("1、4-6周 双周", 8, 4, 6);
        assertWeeks("1-4周(单)，4-8周（双）", 8, 1, 3, 4, 6, 8);
    }

    @Test
    public void explicitWeekListsAreStoredInExplicitWeeks() {
        WeekRule mixed = parser.parse("2、5-6周");
        WeekRule list = parser.parse("1,3,5周");
        WeekRule single = parser.parse("5周");

        assertEquals(Arrays.asList(2, 5, 6), mixed.explicitWeeks);
        assertEquals(Arrays.asList(1, 3, 5), list.explicitWeeks);
        assertEquals(Collections.singletonList(5), single.explicitWeeks);
    }

    @Test
    public void allAndProjectWeeksPreserveExistingAlwaysVisibleBehavior() {
        WeekRule all = parser.parse("全周");
        WeekRule project = parser.parse("项目周");

        for (int week = 1; week <= 20; week++) {
            assertTrue(all.containsWeek(week));
            assertTrue(project.containsWeek(week));
        }
        assertEquals(0, all.lastReferencedWeek());
        assertEquals(0, project.lastReferencedWeek());
    }

    private void assertWeeks(String text, int lastWeek, Integer... expectedWeeks) {
        WeekRule rule = parser.parse(text);
        List<Integer> expected = Arrays.asList(expectedWeeks);
        for (int week = 1; week <= lastWeek; week++) {
            if (expected.contains(week)) {
                assertTrue(text + " should contain week " + week, rule.containsWeek(week));
            } else {
                assertFalse(text + " should not contain week " + week, rule.containsWeek(week));
            }
        }
    }
}
