package com.polaris.timetable.importer;

import com.polaris.timetable.model.StructuredCourse;

import java.util.List;

/** One-shot human-confirmation gate shared by import preview surfaces. */
public final class ScheduleImportConfirmation {
    public interface CommitAction {
        void commit(List<StructuredCourse> courses);
    }

    private boolean resolved;
    private boolean committed;

    public synchronized boolean confirm(ScheduleImportPreviewData preview,
                                        CommitAction action) {
        if (resolved || preview == null || preview.isEmpty() || action == null) {
            return false;
        }
        resolved = true;
        committed = true;
        action.commit(preview.courses);
        return true;
    }

    public synchronized boolean cancel() {
        if (resolved) {
            return false;
        }
        resolved = true;
        committed = false;
        return true;
    }

    public synchronized boolean isResolved() {
        return resolved;
    }

    public synchronized boolean isCommitted() {
        return committed;
    }
}
