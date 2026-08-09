package com.polaris.timetable.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BackgroundImageCropTest {
    @Test
    public void invalidCropFallsBackToFullImage() {
        BackgroundImageCrop crop = BackgroundImageCrop.of(0.5f, 0.5f, 0.5f, 0.8f);

        assertTrue(crop.sameAs(BackgroundImageCrop.full()));
    }

    @Test
    public void fitToAspectCenterCropsWithoutStretching() {
        BackgroundImageCrop crop = BackgroundImageCrop.full()
                .fitToAspect(1600f, 900f, 9f / 16f);

        assertEquals(0f, crop.top, 0.0001f);
        assertEquals(1f, crop.bottom, 0.0001f);
        assertEquals(9f / 16f,
                (crop.right - crop.left) * 1600f / ((crop.bottom - crop.top) * 900f),
                0.0001f);
        assertEquals(0.5f, (crop.left + crop.right) / 2f, 0.0001f);
    }

    @Test
    public void fitToAspectKeepsSelectedCenter() {
        BackgroundImageCrop crop = BackgroundImageCrop.of(0.2f, 0.1f, 0.8f, 0.9f)
                .fitToAspect(1000f, 1000f, 1f);

        assertEquals(0.5f, (crop.left + crop.right) / 2f, 0.0001f);
        assertEquals(0.5f, (crop.top + crop.bottom) / 2f, 0.0001f);
        assertEquals(crop.right - crop.left, crop.bottom - crop.top, 0.0001f);
    }
}
