package com.polaris.timetable.importer;

import java.util.Objects;

/** Pure Java OCR output with image-space coordinates. */
public final class OcrTextBlock {
    public static final float CONFIDENCE_UNKNOWN = -1f;
    public static final int SOURCE_GROUP_UNKNOWN = -1;

    public final String text;
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;
    public final float confidence;
    /** Lines emitted from the same OCR paragraph share this id. */
    public final int sourceGroupId;

    public OcrTextBlock(String text, int left, int top, int right, int bottom,
                        float confidence) {
        this(text, left, top, right, bottom, confidence, SOURCE_GROUP_UNKNOWN);
    }

    public OcrTextBlock(String text, int left, int top, int right, int bottom,
                        float confidence, int sourceGroupId) {
        if (right < left || bottom < top) {
            throw new IllegalArgumentException("OCR block bounds are inverted");
        }
        this.text = text == null ? "" : text.trim();
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.confidence = confidence < 0f
                ? CONFIDENCE_UNKNOWN : Math.min(1f, confidence);
        this.sourceGroupId = sourceGroupId < 0 ? SOURCE_GROUP_UNKNOWN : sourceGroupId;
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) / 2f;
    }

    public float centerY() {
        return (top + bottom) / 2f;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OcrTextBlock)) {
            return false;
        }
        OcrTextBlock block = (OcrTextBlock) other;
        return left == block.left && top == block.top && right == block.right
                && bottom == block.bottom
                && sourceGroupId == block.sourceGroupId
                && Float.compare(confidence, block.confidence) == 0
                && text.equals(block.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, left, top, right, bottom, confidence, sourceGroupId);
    }
}
