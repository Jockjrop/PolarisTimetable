package com.polaris.timetable.time;

import com.polaris.timetable.Course;

import java.util.ArrayList;
import java.util.List;

/**
 * 空闲节段计算：给定某天课程，输出连续空闲节区间（1-based 闭区间）。
 * 节次占用按分钟区间重叠判定（timeRange），节次模式与具体时间模式统一处理。
 * 纯逻辑无 Android 依赖，供空闲时段速查与单元测试共用。
 */
public final class FreeSlotCalculator {
    private FreeSlotCalculator() {
    }

    /**
     * @param courses 当周课程列表（调用方已按周过滤），day 为 0-based（0=周一，与 Course.day 同基准）
     * @return 升序不重叠的空闲节区间；全天无课返回单个 [1, sectionCount]
     */
    public static List<int[]> freeSectionsForDay(List<Course> courses, int day,
                                                 CourseTimeResolver.Settings settings,
                                                 int sectionCount) {
        int safeSectionCount = Math.max(1, sectionCount);
        boolean[] occupied = new boolean[safeSectionCount + 1];
        if (courses != null) {
            for (Course course : courses) {
                if (course == null || course.day != day) {
                    continue;
                }
                CourseTimeResolver.TimeRange courseRange =
                        CourseTimeResolver.timeRange(course, settings);
                if (courseRange == null) {
                    continue;
                }
                for (int section = 1; section <= safeSectionCount; section++) {
                    CourseTimeResolver.TimeRange slot =
                            CourseTimeResolver.sectionTimeRange(settings, section);
                    if (slot == null) {
                        continue;
                    }
                    if (courseRange.startMinutes < slot.endMinutes
                            && slot.startMinutes < courseRange.endMinutes) {
                        occupied[section] = true;
                    }
                }
            }
        }
        List<int[]> ranges = new ArrayList<>();
        int start = -1;
        for (int section = 1; section <= safeSectionCount; section++) {
            if (!occupied[section]) {
                if (start < 0) {
                    start = section;
                }
            } else if (start > 0) {
                ranges.add(new int[]{start, section - 1});
                start = -1;
            }
        }
        if (start > 0) {
            ranges.add(new int[]{start, safeSectionCount});
        }
        return ranges;
    }
}
