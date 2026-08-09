package com.polaris.timetable;

import java.util.Calendar;

final class SemesterStartDateDefaults {
    private SemesterStartDateDefaults() {
    }

    static String resolve(Calendar currentDate) {
        int year = currentDate.get(Calendar.YEAR);
        int month = currentDate.get(Calendar.MONTH);
        int day = currentDate.get(Calendar.DAY_OF_MONTH);

        if (month == Calendar.JANUARY) {
            return (year - 1) + "/9/1";
        }
        if (month < Calendar.JULY || (month == Calendar.JULY && day < 10)) {
            return year + "/3/1";
        }
        return year + "/9/1";
    }

    static String resolveSemesterName(Calendar currentDate) {
        int year = currentDate.get(Calendar.YEAR);
        int month = currentDate.get(Calendar.MONTH);
        int day = currentDate.get(Calendar.DAY_OF_MONTH);
        if (month == Calendar.JANUARY) {
            return (year - 1) + "-" + year + "学年第1学期";
        }
        if (month < Calendar.JULY || (month == Calendar.JULY && day < 10)) {
            return (year - 1) + "-" + year + "学年第2学期";
        }
        return year + "-" + (year + 1) + "学年第1学期";
    }
}
