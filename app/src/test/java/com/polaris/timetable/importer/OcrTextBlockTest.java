package com.polaris.timetable.importer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OcrTextBlockTest {
    @Test
    public void coordinatesAndConfidenceArePreserved() {
        OcrTextBlock block = new OcrTextBlock(" 高等数学 ", 10, 20, 110, 80, 0.87f);

        assertEquals("高等数学", block.text);
        assertEquals(10, block.left);
        assertEquals(20, block.top);
        assertEquals(110, block.right);
        assertEquals(80, block.bottom);
        assertEquals(100, block.width());
        assertEquals(60, block.height());
        assertEquals(60f, block.centerX(), 0.001f);
        assertEquals(50f, block.centerY(), 0.001f);
        assertEquals(0.87f, block.confidence, 0.001f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invertedCoordinatesAreRejected() {
        new OcrTextBlock("课程", 100, 20, 10, 80, 0.9f);
    }
}
