package com.polaris.timetable.time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.parser.SchoolParserModel;
import com.polaris.timetable.state.ScheduleViewState;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassTimeTableControllerTest {

    private static CourseTimeResolver.Settings settings() {
        return new CourseTimeResolver.Settings(
                "08:00", 50, 10, 30, "14:30", "16:35", "");
    }

    @Test
    public void timeTextMinute_formatsAndClampsToDay() {
        assertEquals("08:00", ClassTimeTableController.timeTextMinute(8 * 60));
        assertEquals("09:05", ClassTimeTableController.timeTextMinute(9 * 60 + 5));
        assertEquals("00:00", ClassTimeTableController.timeTextMinute(-1));
        assertEquals("23:59", ClassTimeTableController.timeTextMinute(24 * 60));
        assertEquals("00:00", ClassTimeTableController.timeTextMinute(0));
    }

    @Test
    public void minutesFromTimeText_parsesHourAndMinute() {
        assertEquals(8 * 60, ClassTimeTableController.minutesFromTimeText("08:00"));
        assertEquals(9 * 60 + 5, ClassTimeTableController.minutesFromTimeText("09:05"));
        assertEquals(14 * 60 + 30, ClassTimeTableController.minutesFromTimeText("14：30"));
        // 无匹配时回退 08:00。
        assertEquals(8 * 60, ClassTimeTableController.minutesFromTimeText(""));
    }

    @Test
    public void hasClassTimeTable_detectsAnchors() {
        assertFalse(ClassTimeTableController.hasClassTimeTable(null));
        assertFalse(ClassTimeTableController.hasClassTimeTable(""));
        assertFalse(ClassTimeTableController.hasClassTimeTable("08:00 开始"));
        assertTrue(ClassTimeTableController.hasClassTimeTable("1 08:00-08:50"));
    }

    @Test
    public void nextClassTimeRow_followsPreviousRow() {
        List<int[]> rows = new ArrayList<>();
        rows.add(new int[]{8 * 60, 8 * 60 + 50});
        int[] next = ClassTimeTableController.nextClassTimeRow(rows);
        assertEquals(8 * 60 + 50 + 10, next[0]);
        assertEquals(next[0] + 50, next[1]);
    }

    @Test
    public void rowsFromSchoolModel_usesAnchoredConfig() {
        List<int[]> rows = ClassTimeTableController.rowsFromSchoolModel(SchoolParserModel.XUPT);
        assertEquals(11, rows.size());
        assertEquals(8 * 60, rows.get(0)[0]);
        assertEquals(8 * 60 + 50, rows.get(0)[1]);
    }

    @Test
    public void rowsFromSchoolModel_nullFallsBackToSingleRow() {
        List<int[]> rows = ClassTimeTableController.rowsFromSchoolModel(null);
        assertEquals(1, rows.size());
        assertEquals(8 * 60, rows.get(0)[0]);
        assertEquals(8 * 60 + 50, rows.get(0)[1]);
    }

    @Test
    public void loadRows_respectsSectionCountAndFallback() {
        List<int[]> rows = ClassTimeTableController.loadRows("", settings(), 2);
        assertEquals(2, rows.size());
        assertEquals(8 * 60, rows.get(0)[0]);
        assertEquals(8 * 60 + 50, rows.get(0)[1]);
    }

    @Test
    public void validate_rejectsEmptyAndEndBeforeStart() {
        ClassTimeTableController.Validation empty =
                ClassTimeTableController.validate(new ArrayList<>());
        assertFalse(empty.valid);
        assertEquals(ClassTimeTableController.ValidationError.EMPTY, empty.error);

        List<int[]> rows = Arrays.asList(new int[]{8 * 60 + 50, 8 * 60});
        ClassTimeTableController.Validation invalid =
                ClassTimeTableController.validate(rows);
        assertFalse(invalid.valid);
        assertEquals(ClassTimeTableController.ValidationError.END_BEFORE_START, invalid.error);
        assertEquals(1, invalid.errorRow);
    }

    @Test
    public void validate_flagsOverlapButStillValid() {
        List<int[]> rows = Arrays.asList(
                new int[]{8 * 60, 8 * 60 + 50},
                new int[]{8 * 60 + 40, 9 * 60 + 40});
        ClassTimeTableController.Validation result =
                ClassTimeTableController.validate(rows);
        assertTrue(result.valid);
        assertTrue(result.overlap);
    }

    @Test
    public void applyRows_writesConfigAndDerivedFields() {
        ScheduleViewState state = new ScheduleViewState();
        List<int[]> rows = Arrays.asList(
                new int[]{8 * 60, 8 * 60 + 45},
                new int[]{8 * 60 + 55, 9 * 60 + 40});

        ClassTimeTableController.applyRows(rows, state);

        assertEquals("1 08:00-08:45\n2 08:55-09:40", state.classTimeConfig);
        assertEquals("08:00", state.firstClassStartTime);
        assertEquals(45, state.classDurationMinutes);
        assertEquals(2, state.courseSectionCount);
    }
}
