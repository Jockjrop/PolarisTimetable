package com.polaris.timetable.importer;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/** Bundled, on-device Chinese/Latin ML Kit OCR followed by the pure Java grid parser. */
public final class LocalOcrScheduleRecognizer implements ImageScheduleRecognizer {
    private static final String TAG = "LocalOcrSchedule";

    private final TextRecognizer textRecognizer;
    private final ScheduleImageParser parser;

    public LocalOcrScheduleRecognizer() {
        this(new ScheduleImageParser());
    }

    LocalOcrScheduleRecognizer(ScheduleImageParser parser) {
        this.parser = parser;
        this.textRecognizer = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());
    }

    @Override
    public void recognize(Bitmap image, Callback callback) {
        if (callback == null) {
            return;
        }
        if (image == null || image.isRecycled()) {
            callback.onFailure(new IllegalArgumentException("图片数据不可用"));
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        InputImage input = InputImage.fromBitmap(image, 0);
        textRecognizer.process(input)
                .addOnSuccessListener(text -> {
                    List<OcrTextBlock> lines = toBlocks(text);
                    ImageScheduleRecognitionResult result = parser.parse(lines, width, height);
                    Log.i(TAG, "recognized image=" + width + "x" + height
                            + ", lines=" + lines.size()
                            + ", courses=" + result.structuredCourses.size()
                            + ", meetings=" + result.meetingCount()
                            + ", confidence=" + Math.round(result.confidence * 100f) + "%");
                    callback.onResult(result);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private List<OcrTextBlock> toBlocks(Text text) {
        List<OcrTextBlock> blocks = new ArrayList<>();
        int sourceGroupId = 0;
        for (Text.TextBlock textBlock : text.getTextBlocks()) {
            boolean emittedLine = false;
            for (Text.Line line : textBlock.getLines()) {
                Rect bounds = line.getBoundingBox();
                String value = line.getText();
                if (bounds == null || value == null || value.trim().isEmpty()) {
                    continue;
                }
                blocks.add(new OcrTextBlock(
                        value, bounds.left, bounds.top, bounds.right, bounds.bottom,
                        averageConfidence(line), sourceGroupId));
                emittedLine = true;
            }
            if (!emittedLine) {
                Rect bounds = textBlock.getBoundingBox();
                String value = textBlock.getText();
                if (bounds != null && value != null && !value.trim().isEmpty()) {
                    blocks.add(new OcrTextBlock(
                            value, bounds.left, bounds.top, bounds.right, bounds.bottom,
                            averageConfidence(textBlock), sourceGroupId));
                }
            }
            sourceGroupId++;
        }
        return blocks;
    }

    private float averageConfidence(Text.Line line) {
        float total = 0f;
        int count = 0;
        for (Text.Element element : line.getElements()) {
            float confidence = element.getConfidence();
            if (confidence > 0f) {
                total += confidence;
                count++;
            }
        }
        return count == 0 ? OcrTextBlock.CONFIDENCE_UNKNOWN : total / count;
    }

    private float averageConfidence(Text.TextBlock block) {
        float total = 0f;
        int count = 0;
        for (Text.Line line : block.getLines()) {
            for (Text.Element element : line.getElements()) {
                float confidence = element.getConfidence();
                if (confidence > 0f) {
                    total += confidence;
                    count++;
                }
            }
        }
        return count == 0 ? OcrTextBlock.CONFIDENCE_UNKNOWN : total / count;
    }

    @Override
    public void close() {
        textRecognizer.close();
    }
}
