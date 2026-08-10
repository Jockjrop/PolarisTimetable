package com.polaris.timetable.importer;

/** Keeps cancellation, empty OCR and unreliable layouts outside the persistence boundary. */
public final class ImageImportCommitPolicy {
    private ImageImportCommitPolicy() {
    }

    public static boolean canCommit(boolean confirmed,
                                    ImageScheduleRecognitionResult result) {
        return confirmed && result != null && result.isImportable();
    }
}
