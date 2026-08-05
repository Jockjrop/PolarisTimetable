package com.polaris.timetable.parser;

public enum SchoolParserModel {
    XUPT("西安邮电大学", "08:00", 50, 5, 30, "14:30", "16:35",
            "1 08:00-08:50\n"
                    + "2 08:55-09:45\n"
                    + "3 10:15-11:05\n"
                    + "4 11:10-12:00\n"
                    + "5 14:30-15:20\n"
                    + "6 15:25-16:15\n"
                    + "7 16:35-17:25\n"
                    + "8 17:30-18:20\n"
                    + "9 19:00-19:50\n"
                    + "10 20:00-20:50\n"
                    + "11 21:00-21:50"),
    XAUT("西安理工大学", "08:00", 50, 10, 30, "14:30", "16:35",
            "1 08:00-08:50\n"
                    + "2 09:00-09:50\n"
                    + "3 10:10-11:00\n"
                    + "4 11:10-12:00\n"
                    + "5 12:10-13:00\n"
                    + "6 13:10-14:00\n"
                    + "7 14:10-15:00\n"
                    + "8 15:10-16:00\n"
                    + "9 16:10-17:00\n"
                    + "10 17:10-18:00\n"
                    + "11 19:00-19:50\n"
                    + "12 20:00-20:50\n"
                    + "13 21:00-21:50"),
    HDU("杭州电子科技大学", "08:05", 45, 5, 20, "13:30", "14:20",
            "1 08:05-08:50\n"
                    + "2 08:55-09:40\n"
                    + "3 10:00-10:45\n"
                    + "4 10:50-11:35\n"
                    + "5 11:40-12:25\n"
                    + "6 13:30-14:15\n"
                    + "7 14:20-15:05\n"
                    + "8 15:15-16:00\n"
                    + "9 16:05-16:50\n"
                    + "10 18:30-19:15\n"
                    + "11 19:20-20:05\n"
                    + "12 20:10-20:55");

    public final String label;
    public final String defaultFirstClassStartTime;
    public final int defaultClassDurationMinutes;
    public final int defaultClassBreakMinutes;
    public final int defaultClassBigBreakMinutes;
    public final String defaultAfternoonStartTime;
    public final String defaultLateAfternoonStartTime;
    public final String defaultClassTimeConfig;

    SchoolParserModel(String label,
                      String defaultFirstClassStartTime,
                      int defaultClassDurationMinutes,
                      int defaultClassBreakMinutes,
                      int defaultClassBigBreakMinutes,
                      String defaultAfternoonStartTime,
                      String defaultLateAfternoonStartTime) {
        this(label, defaultFirstClassStartTime, defaultClassDurationMinutes, defaultClassBreakMinutes,
                defaultClassBigBreakMinutes, defaultAfternoonStartTime, defaultLateAfternoonStartTime, "");
    }

    SchoolParserModel(String label,
                      String defaultFirstClassStartTime,
                      int defaultClassDurationMinutes,
                      int defaultClassBreakMinutes,
                      int defaultClassBigBreakMinutes,
                      String defaultAfternoonStartTime,
                      String defaultLateAfternoonStartTime,
                      String defaultClassTimeConfig) {
        this.label = label;
        this.defaultFirstClassStartTime = defaultFirstClassStartTime;
        this.defaultClassDurationMinutes = defaultClassDurationMinutes;
        this.defaultClassBreakMinutes = defaultClassBreakMinutes;
        this.defaultClassBigBreakMinutes = defaultClassBigBreakMinutes;
        this.defaultAfternoonStartTime = defaultAfternoonStartTime;
        this.defaultLateAfternoonStartTime = defaultLateAfternoonStartTime;
        this.defaultClassTimeConfig = defaultClassTimeConfig == null ? "" : defaultClassTimeConfig;
    }

    public int defaultSectionCount() {
        if (defaultClassTimeConfig.length() == 0) {
            return 0;
        }
        int count = 0;
        String[] lines = defaultClassTimeConfig.split("\\n");
        for (String line : lines) {
            if (line.trim().matches("\\d{1,2}\\s+\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}")) {
                count++;
            }
        }
        return count;
    }
}
