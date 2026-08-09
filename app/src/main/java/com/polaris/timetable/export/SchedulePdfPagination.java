package com.polaris.timetable.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Calculates continuous source-image slices for fixed-size PDF pages. */
public final class SchedulePdfPagination {
    private SchedulePdfPagination() {
    }

    public static final class Slice {
        public final int top;
        public final int bottom;

        Slice(int top, int bottom) {
            this.top = top;
            this.bottom = bottom;
        }

        public int height() {
            return bottom - top;
        }
    }

    public static List<Slice> paginate(int sourceWidth, int sourceHeight,
                                       int contentWidth, int contentHeight,
                                       boolean fitSinglePage) {
        if (sourceWidth <= 0 || sourceHeight <= 0
                || contentWidth <= 0 || contentHeight <= 0) {
            throw new IllegalArgumentException("分页尺寸必须大于零");
        }
        float scale = contentWidth / (float) sourceWidth;
        if (fitSinglePage) {
            scale = Math.min(scale, contentHeight / (float) sourceHeight);
        }
        int sliceHeight = Math.max(1, (int) Math.floor(contentHeight / scale));
        List<Slice> slices = new ArrayList<>();
        int top = 0;
        while (top < sourceHeight) {
            int bottom = Math.min(sourceHeight, top + sliceHeight);
            slices.add(new Slice(top, bottom));
            top = bottom;
        }
        return Collections.unmodifiableList(slices);
    }
}
