package com.polaris.timetable.importer.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AiExternalImportReturnControllerTest {
    private final AiExternalImportReturnController controller =
            new AiExternalImportReturnController();

    @Test
    public void resumeBeforeExternalPauseDoesNotReadClipboard() {
        controller.begin("before");

        assertFalse(controller.shouldCheckClipboardOnResume());

        AiExternalImportReturnController.ReturnResult result =
                controller.onHostResumed("after");

        assertFalse(result.returnedFromExternalApp);
        assertFalse(result.hasNewClipboardText());
    }

    @Test
    public void changedClipboardAfterRoundTripIsReturned() {
        controller.begin("before");
        controller.onHostPaused();

        assertTrue(controller.shouldCheckClipboardOnResume());

        AiExternalImportReturnController.ReturnResult result =
                controller.onHostResumed("POLARIS_SCHEDULE_V1\n{}");

        assertTrue(result.returnedFromExternalApp);
        assertTrue(result.hasNewClipboardText());
        assertEquals("POLARIS_SCHEDULE_V1\n{}", result.newClipboardText);
    }

    @Test
    public void unchangedClipboardDoesNotOverwriteInput() {
        controller.begin("existing result");
        controller.onHostPaused();

        AiExternalImportReturnController.ReturnResult result =
                controller.onHostResumed("existing result");

        assertTrue(result.returnedFromExternalApp);
        assertFalse(result.hasNewClipboardText());
    }

    @Test
    public void blankClipboardDoesNotProduceText() {
        controller.begin("before");
        controller.onHostPaused();

        AiExternalImportReturnController.ReturnResult result =
                controller.onHostResumed("   ");

        assertTrue(result.returnedFromExternalApp);
        assertFalse(result.hasNewClipboardText());
    }

    @Test
    public void cancelClearsPendingRoundTrip() {
        controller.begin("before");
        controller.onHostPaused();
        controller.cancel();

        AiExternalImportReturnController.ReturnResult result =
                controller.onHostResumed("after");

        assertFalse(result.returnedFromExternalApp);
    }
}
