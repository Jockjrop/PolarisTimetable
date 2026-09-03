package com.polaris.timetable.widget;

import com.polaris.timetable.Course;
import com.polaris.timetable.storage.ScheduleRepository;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScheduleWidgetData {
    private ScheduleWidgetData() {
    }

    static List<ScheduleWidgetEntry> forDate(
            List<Course> source, ScheduleRepository.Config config, Calendar date) {
        return forDate(source, config, date, Calendar.getInstance());
    }

    static List<ScheduleWidgetEntry> forDate(
            List<Course> source, ScheduleRepository.Config config, Calendar date, Calendar now) {
        if (source == null || config == null || date == null) {
            return Collections.emptyList();
        }
        int week = weekForDate(config.firstWeekDay, date);
        if (week < 1 || week > Math.max(1, config.semesterWeeks)) {
            return Collections.emptyList();
        }
        int day = mondayBasedDay(date);
        Map<String, Integer> courseColors = buildCourseColors(source);
        List<Course> matching = new ArrayList<>();
        for (Course course : source) {
            if (course == null || !isActiveInWeek(course, week)) {
                continue;
            }
            if (course.isBannerOnlyCourse()) {
                if (config.showPracticeBanner) {
                    matching.add(course);
                }
            } else if (course.hasScheduledTime() && course.day == day
                    && !hasEndedToday(course, config, date, now)) {
                matching.add(course);
            }
        }
        Collections.sort(matching, new Comparator<Course>() {
            @Override
            public int compare(Course first, Course second) {
                if (first.isBannerOnlyCourse() != second.isBannerOnlyCourse()) {
                    return first.isBannerOnlyCourse() ? -1 : 1;
                }
                int time = Integer.compare(
                        ScheduleWidgetTimeFormatter.startMinutes(first, config),
                        ScheduleWidgetTimeFormatter.startMinutes(second, config));
                if (time != 0) {
                    return time;
                }
                return safe(first.name).compareTo(safe(second.name));
            }
        });

        List<ScheduleWidgetEntry> entries = new ArrayList<>();
        for (int index = 0; index < matching.size(); index++) {
            Course course = matching.get(index);
            String name = safe(course.name).length() == 0 ? "未命名课程" : safe(course.name);
            String location = safe(course.location);
            if (location.length() == 0) {
                location = course.isBannerOnlyCourse() ? "无固定地点" : "地点待定";
            }
            entries.add(new ScheduleWidgetEntry(
                    name,
                    ScheduleWidgetTimeFormatter.format(course, config),
                    location,
                    stableId(course, week, day, index),
                    courseColor(course, courseColors),
                    isOngoingNow(course, config, date, now)));
        }
        return entries;
    }

    /** 目标日期=今天且当前时刻落在课程时间内（含开始、不含结束）时为进行中。 */
    private static boolean isOngoingNow(
            Course course, ScheduleRepository.Config config, Calendar target, Calendar now) {
        if (now == null || !isSameDay(target, now)
                || course.isBannerOnlyCourse() || !course.hasScheduledTime()) {
            return false;
        }
        int startMinutes = ScheduleWidgetTimeFormatter.startMinutes(course, config);
        int endMinutes = ScheduleWidgetTimeFormatter.endMinutes(course, config);
        int nowMinutes = minutesOfDay(now);
        return startMinutes >= 0 && endMinutes > startMinutes
                && nowMinutes >= startMinutes && nowMinutes < endMinutes;
    }

    static long nextCourseEndAfter(
            List<Course> source, ScheduleRepository.Config config, Calendar now) {
        return nextCourseBoundaryAfter(source, config, now, false);
    }

    static long nextCourseStartAfter(
            List<Course> source, ScheduleRepository.Config config, Calendar now) {
        return nextCourseBoundaryAfter(source, config, now, true);
    }

    /**
     * 下一个课程时间边界（开始或结束）的时间戳；无未来边界返回 -1。
     * 供 provider 安排下一次刷新，使"进行中"高亮在课程开始/结束时刻准时切换。
     */
    private static long nextCourseBoundaryAfter(
            List<Course> source, ScheduleRepository.Config config, Calendar now,
            boolean startBoundary) {
        if (source == null || config == null || now == null) {
            return -1L;
        }
        int week = weekForDate(config.firstWeekDay, now);
        if (week < 1 || week > Math.max(1, config.semesterWeeks)) {
            return -1L;
        }
        int day = mondayBasedDay(now);
        int currentMinutes = minutesOfDay(now);
        long nextBoundary = Long.MAX_VALUE;
        for (Course course : source) {
            if (course == null || !course.hasScheduledTime() || course.day != day
                    || !isActiveInWeek(course, week)) {
                continue;
            }
            int boundaryMinutes = startBoundary
                    ? ScheduleWidgetTimeFormatter.startMinutes(course, config)
                    : ScheduleWidgetTimeFormatter.endMinutes(course, config);
            if (boundaryMinutes <= currentMinutes) {
                continue;
            }
            Calendar boundary = (Calendar) now.clone();
            boundary.set(Calendar.HOUR_OF_DAY, boundaryMinutes / 60);
            boundary.set(Calendar.MINUTE, boundaryMinutes % 60);
            boundary.set(Calendar.SECOND, startBoundary ? 0 : 1);
            boundary.set(Calendar.MILLISECOND, 0);
            nextBoundary = Math.min(nextBoundary, boundary.getTimeInMillis());
        }
        return nextBoundary == Long.MAX_VALUE ? -1L : nextBoundary;
    }

    private static Map<String, Integer> buildCourseColors(List<Course> courses) {
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (Course course : courses) {
            if (course == null) {
                continue;
            }
            String name = safe(course.name);
            if (!colors.containsKey(name)) {
                float hue = (colors.size() * 137.508f) % 360f;
                colors.put(name, hsvColor(hue, 0.62f, 0.94f));
            }
        }
        return colors;
    }

    private static int courseColor(Course course, Map<String, Integer> colors) {
        Integer saved = parseColor(course.color);
        if (saved != null) {
            return saved;
        }
        Integer generated = colors.get(safe(course.name));
        return generated == null ? 0xFF4FA4F3 : generated;
    }

    private static Integer parseColor(String value) {
        String color = safe(value);
        if (!color.startsWith("#")) {
            return null;
        }
        try {
            long parsed = Long.parseLong(color.substring(1), 16);
            if (color.length() == 7) {
                parsed |= 0xFF000000L;
            } else if (color.length() != 9) {
                return null;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int hsvColor(float hue, float saturation, float value) {
        float chroma = value * saturation;
        float segment = hue / 60f;
        float secondary = chroma * (1f - Math.abs(segment % 2f - 1f));
        float red;
        float green;
        float blue;
        if (segment < 1f) {
            red = chroma; green = secondary; blue = 0f;
        } else if (segment < 2f) {
            red = secondary; green = chroma; blue = 0f;
        } else if (segment < 3f) {
            red = 0f; green = chroma; blue = secondary;
        } else if (segment < 4f) {
            red = 0f; green = secondary; blue = chroma;
        } else if (segment < 5f) {
            red = secondary; green = 0f; blue = chroma;
        } else {
            red = chroma; green = 0f; blue = secondary;
        }
        float match = value - chroma;
        int r = Math.round((red + match) * 255f);
        int g = Math.round((green + match) * 255f);
        int b = Math.round((blue + match) * 255f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int weekForDate(String firstWeekDay, Calendar target) {
        return CourseTimeResolver.weekForDate(firstWeekDay, target);
    }

    private static int mondayBasedDay(Calendar date) {
        return CourseTimeResolver.mondayBasedDay(date);
    }

    private static boolean hasEndedToday(
            Course course, ScheduleRepository.Config config, Calendar target, Calendar now) {
        if (now == null || !isSameDay(target, now)) {
            return false;
        }
        int endMinutes = ScheduleWidgetTimeFormatter.endMinutes(course, config);
        return endMinutes >= 0 && minutesOfDay(now) >= endMinutes;
    }

    private static boolean isSameDay(Calendar first, Calendar second) {
        return first.get(Calendar.ERA) == second.get(Calendar.ERA)
                && first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private static int minutesOfDay(Calendar date) {
        return CourseTimeResolver.minutesOfDay(date);
    }

    private static boolean isActiveInWeek(Course course, int week) {
        return CourseTimeResolver.isActiveInWeek(course, week);
    }

    private static int stableId(Course course, int week, int day, int index) {
        int result = 17;
        result = 31 * result + safe(course.name).hashCode();
        result = 31 * result + week;
        result = 31 * result + day;
        result = 31 * result + course.startSection;
        result = 31 * result + course.endSection;
        result = 31 * result + course.startMinuteOfDay;
        result = 31 * result + course.endMinuteOfDay;
        result = 31 * result + index;
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
