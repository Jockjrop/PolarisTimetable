package com.polaris.timetable.storage;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScheduleRepositoryVisualThemeTest {
    @Test
    public void legacyConfigurationKeepsMinimalTheme() {
        ScheduleRepository.Config restored = ScheduleRepository.Config.fromJson(new JSONObject());

        assertEquals("极简风格", restored.visualTheme);
    }

    @Test
    public void visualThemeRoundTripsWithoutChangingOtherConfiguration() {
        ScheduleRepository.Config source = new ScheduleRepository.Config();
        source.visualTheme = "深空星河";
        source.scheduleName = "本学期";

        JSONObject json = source.toJson();
        ScheduleRepository.Config restored = ScheduleRepository.Config.fromJson(json);

        assertEquals("深空星河", restored.visualTheme);
        assertEquals("本学期", restored.scheduleName);
    }

    @Test
    public void collapseLunchBreakRoundTrips() {
        ScheduleRepository.Config source = new ScheduleRepository.Config();
        source.collapseLunchBreak = true;

        ScheduleRepository.Config restored = ScheduleRepository.Config.fromJson(source.toJson());

        assertTrue(restored.collapseLunchBreak);
    }
}
