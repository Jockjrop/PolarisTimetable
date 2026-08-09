package com.polaris.timetable.export;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SchedulePdfPaginationTest {
    @Test
    public void paginate_keepsNormalScheduleOnOnePage() {
        List<SchedulePdfPagination.Slice> slices = SchedulePdfPagination.paginate(
                1600, 2764, 547, 794, true);

        assertEquals(1, slices.size());
        assertEquals(0, slices.get(0).top);
        assertEquals(2764, slices.get(0).bottom);
    }

    @Test
    public void paginate_splitsTallScheduleWithoutGaps() {
        List<SchedulePdfPagination.Slice> slices = SchedulePdfPagination.paginate(
                2000, 4700, 547, 794, false);

        assertEquals(2, slices.size());
        assertEquals(0, slices.get(0).top);
        assertEquals(slices.get(0).bottom, slices.get(1).top);
        assertEquals(4700, slices.get(1).bottom);
    }

    @Test(expected = IllegalArgumentException.class)
    public void paginate_rejectsInvalidDimensions() {
        SchedulePdfPagination.paginate(0, 100, 547, 794, true);
    }
}
