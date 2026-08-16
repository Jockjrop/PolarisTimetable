package com.polaris.timetable.importer.ai;

/**
 * Tracks one explicit round trip from Polaris to an external AI application.
 * This class deliberately has no Android dependencies so lifecycle edge cases
 * can be covered by local unit tests.
 */
public final class AiExternalImportReturnController {
    private boolean awaitingReturn;
    private boolean hostPaused;
    private String clipboardBeforeLaunch = "";

    public void begin(String currentClipboardText) {
        awaitingReturn = true;
        hostPaused = false;
        clipboardBeforeLaunch = normalize(currentClipboardText);
    }

    public void onHostPaused() {
        if (awaitingReturn) {
            hostPaused = true;
        }
    }

    public boolean shouldCheckClipboardOnResume() {
        return awaitingReturn && hostPaused;
    }

    public ReturnResult onHostResumed(String currentClipboardText) {
        if (!awaitingReturn || !hostPaused) {
            return ReturnResult.notReturned();
        }

        awaitingReturn = false;
        hostPaused = false;
        String current = normalize(currentClipboardText);
        if (current.trim().isEmpty() || current.equals(clipboardBeforeLaunch)) {
            clipboardBeforeLaunch = "";
            return ReturnResult.returnedWithoutNewText();
        }

        clipboardBeforeLaunch = "";
        return ReturnResult.returnedWithText(current);
    }

    public void cancel() {
        awaitingReturn = false;
        hostPaused = false;
        clipboardBeforeLaunch = "";
    }

    private static String normalize(String text) {
        return text == null ? "" : text;
    }

    public static final class ReturnResult {
        public final boolean returnedFromExternalApp;
        public final String newClipboardText;

        private ReturnResult(boolean returnedFromExternalApp, String newClipboardText) {
            this.returnedFromExternalApp = returnedFromExternalApp;
            this.newClipboardText = normalize(newClipboardText);
        }

        public boolean hasNewClipboardText() {
            return !newClipboardText.trim().isEmpty();
        }

        private static ReturnResult notReturned() {
            return new ReturnResult(false, "");
        }

        private static ReturnResult returnedWithoutNewText() {
            return new ReturnResult(true, "");
        }

        private static ReturnResult returnedWithText(String text) {
            return new ReturnResult(true, text);
        }
    }
}
