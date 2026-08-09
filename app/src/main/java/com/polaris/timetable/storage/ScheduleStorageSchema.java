package com.polaris.timetable.storage;

import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StructuredCourse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScheduleStorageSchema {
    public static final int LEGACY_VERSION = 0;
    public static final int CURRENT_VERSION = 1;
    private static final String VERSION_KEY_PREFIX = "schema_version_";

    interface IdGenerator {
        String create();
    }

    private ScheduleStorageSchema() {
    }

    public static String versionKey(String scheduleId) {
        return VERSION_KEY_PREFIX
                + (scheduleId == null || scheduleId.length() == 0 ? "default" : scheduleId);
    }

    public static MigrationResult migrate(int storedVersion, List<StructuredCourse> source) {
        return migrate(storedVersion, source, StableCourseId::create);
    }

    static MigrationResult migrate(int storedVersion, List<StructuredCourse> source,
                                   IdGenerator idGenerator) {
        List<StructuredCourse> original = source == null
                ? Collections.emptyList() : source;
        if (storedVersion > CURRENT_VERSION) {
            return new MigrationResult(original, storedVersion, false, false);
        }

        int normalizedVersion = Math.max(LEGACY_VERSION, storedVersion);
        List<StructuredCourse> migrated = new ArrayList<>(original.size());
        Set<String> usedIds = new HashSet<>();
        boolean courseDataChanged = false;
        for (StructuredCourse course : original) {
            if (course == null) {
                return new MigrationResult(original, normalizedVersion, false, false);
            }
            String id = course.id;
            if (!StableCourseId.isValid(id) || usedIds.contains(id)) {
                id = nextUniqueId(idGenerator, usedIds);
                courseDataChanged = true;
            }
            usedIds.add(id);
            migrated.add(id.equals(course.id) ? course : copyWithId(course, id));
        }

        boolean versionChanged = normalizedVersion < CURRENT_VERSION;
        return new MigrationResult(
                migrated,
                CURRENT_VERSION,
                courseDataChanged || versionChanged,
                true);
    }

    private static String nextUniqueId(IdGenerator generator, Set<String> usedIds) {
        String id;
        do {
            id = generator.create();
        } while (!StableCourseId.isValid(id) || usedIds.contains(id));
        return id;
    }

    private static StructuredCourse copyWithId(StructuredCourse course, String id) {
        return new StructuredCourse(
                id,
                course.name,
                course.teacher,
                course.defaultLocation,
                course.meetings,
                course.rawText,
                course.credit,
                course.color,
                course.courseType);
    }

    public static final class MigrationResult {
        public final List<StructuredCourse> courses;
        public final int version;
        public final boolean changed;
        public final boolean canPersist;

        MigrationResult(List<StructuredCourse> courses, int version,
                        boolean changed, boolean canPersist) {
            this.courses = Collections.unmodifiableList(new ArrayList<>(courses));
            this.version = version;
            this.changed = changed;
            this.canPersist = canPersist;
        }
    }
}
