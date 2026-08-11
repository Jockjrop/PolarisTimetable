package com.polaris.timetable.model;

/** Describes how a course meeting obtains its time within a day. */
public enum CourseTimeMode {
    SECTION,
    CLOCK,
    NONE;

    public static CourseTimeMode fromStorage(
            String value,
            int day,
            int startSection,
            int endSection,
            int startMinuteOfDay,
            int endMinuteOfDay) {
        CourseTimeMode parsed = null;
        try {
            parsed = value == null || value.trim().isEmpty()
                    ? null : CourseTimeMode.valueOf(value.trim());
        } catch (IllegalArgumentException ignored) {
            // Invalid persisted values fall through to the shape-based inference below.
        }
        return normalize(parsed, day, startSection, endSection,
                startMinuteOfDay, endMinuteOfDay);
    }

    public static CourseTimeMode normalize(
            CourseTimeMode requested,
            int day,
            int startSection,
            int endSection,
            int startMinuteOfDay,
            int endMinuteOfDay) {
        boolean validDay = day >= 0 && day <= 6;
        boolean validClock = validDay
                && startMinuteOfDay >= 0
                && startMinuteOfDay < endMinuteOfDay
                && endMinuteOfDay <= 24 * 60;
        boolean validSections = validDay
                && startSection >= 1
                && endSection >= startSection;
        if (requested == CLOCK && validClock) {
            return CLOCK;
        }
        if (requested == SECTION && validSections) {
            return SECTION;
        }
        if (requested == NONE && !validClock && !validSections) {
            return NONE;
        }
        if (validClock) {
            return CLOCK;
        }
        return validSections ? SECTION : NONE;
    }
}
