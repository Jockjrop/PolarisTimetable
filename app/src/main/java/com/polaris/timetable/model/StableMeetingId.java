package com.polaris.timetable.model;

import java.util.UUID;

public final class StableMeetingId {
    private StableMeetingId() {
    }

    public static String create() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
