package com.polaris.timetable.widget;

final class ScheduleWidgetEntry {
    final String name;
    final String time;
    final String location;
    final int stableId;
    final int color;

    ScheduleWidgetEntry(String name, String time, String location, int stableId, int color) {
        this.name = name;
        this.time = time;
        this.location = location;
        this.stableId = stableId;
        this.color = color;
    }
}
