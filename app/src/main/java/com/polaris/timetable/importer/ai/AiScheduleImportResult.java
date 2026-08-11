package com.polaris.timetable.importer.ai;

import com.polaris.timetable.importer.ai.dto.AiScheduleImport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Final result of extraction, JSON decoding, and validation. */
public abstract class AiScheduleImportResult {
    private AiScheduleImportResult() {
    }

    public abstract boolean isSuccess();

    public static final class Success extends AiScheduleImportResult {
        public final AiScheduleImport data;
        public final List<AiImportIssue> warnings;

        public Success(AiScheduleImport data, List<AiImportIssue> warnings) {
            this.data = data;
            this.warnings = immutableCopy(warnings);
        }

        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    public static final class Failure extends AiScheduleImportResult {
        public final List<AiImportIssue> errors;

        public Failure(List<AiImportIssue> errors) {
            this.errors = immutableCopy(errors);
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return values == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
