package com.polaris.timetable.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.model.StructuredCourse;

import org.junit.Test;

import java.util.Collections;

public class ScheduleImportConfirmationTest {
    @Test
    public void cancelPreventsAnyLaterSave() {
        ScheduleImportConfirmation confirmation = new ScheduleImportConfirmation();
        int[] saves = {0};

        assertTrue(confirmation.cancel());
        assertFalse(confirmation.confirm(preview(), courses -> saves[0]++));

        assertEquals(0, saves[0]);
        assertFalse(confirmation.isCommitted());
    }

    @Test
    public void emptyPreviewCannotSave() {
        ScheduleImportConfirmation confirmation = new ScheduleImportConfirmation();
        int[] saves = {0};
        ScheduleImportPreviewData empty = ScheduleImportPreviewData.basic(
                "AI 识别", null, Collections.emptyList(), Collections.emptyList());

        assertFalse(confirmation.confirm(empty, courses -> saves[0]++));

        assertEquals(0, saves[0]);
        assertFalse(confirmation.isCommitted());
    }

    @Test
    public void confirmedPreviewSavesAndRefreshesOnce() {
        ScheduleImportConfirmation confirmation = new ScheduleImportConfirmation();
        int[] saves = {0};
        int[] refreshes = {0};

        boolean committed = confirmation.confirm(preview(), courses -> {
            saves[0]++;
            refreshes[0]++;
        });

        assertTrue(committed);
        assertEquals(1, saves[0]);
        assertEquals(1, refreshes[0]);
        assertTrue(confirmation.isCommitted());
    }

    @Test
    public void rapidDoubleConfirmationInvokesCommitOnlyOnce() {
        ScheduleImportConfirmation confirmation = new ScheduleImportConfirmation();
        int[] saves = {0};

        assertTrue(confirmation.confirm(preview(), courses -> saves[0]++));
        assertFalse(confirmation.confirm(preview(), courses -> saves[0]++));

        assertEquals(1, saves[0]);
    }

    private ScheduleImportPreviewData preview() {
        StructuredCourse course = new StructuredCourse(
                "11111111-1111-4111-8111-111111111111",
                "测试课程", "", "", Collections.emptyList(), "");
        return ScheduleImportPreviewData.basic(
                "AI 识别", null, Collections.singletonList(course),
                Collections.emptyList());
    }
}
