package com.polaris.timetable.reminder;

import com.polaris.timetable.Course;
import com.polaris.timetable.time.AcademicCalendar;
import com.polaris.timetable.time.CourseTimeResolver;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Builds a bounded list of future course reminder times without Android dependencies. */
public final class CourseReminderPlanner {
    public static final int DEFAULT_MAX_REMINDERS = 64;

    private CourseReminderPlanner() {
    }

    public static final class Entry {
        public final Course course;
        public final int week;
        public final long classStartMillis;
        public final long triggerAtMillis;
        public final String classTimeText;

        Entry(Course course, int week, long classStartMillis, long triggerAtMillis,
              String classTimeText) {
            this.course = course;
            this.week = week;
            this.classStartMillis = classStartMillis;
            this.triggerAtMillis = triggerAtMillis;
            this.classTimeText = classTimeText;
        }
    }

    public static List<Entry> plan(List<Course> courses,
                                   CourseTimeResolver.Settings timeSettings,
                                   long firstWeekStartMillis,
                                   int semesterWeeks,
                                   int minutesBefore,
                                   Calendar now,
                                   int maxReminders) {
        return plan(courses, timeSettings, firstWeekStartMillis, semesterWeeks,
                minutesBefore, now, maxReminders, null);
    }

    /**
     * 带学业日历的提醒规划：逐日判断「这天实际按星期几上课」，
     * 放假当天不生成提醒，补班当天为目标星期的课程生成提醒。
     */
    public static List<Entry> plan(List<Course> courses,
                                   CourseTimeResolver.Settings timeSettings,
                                   long firstWeekStartMillis,
                                   int semesterWeeks,
                                   int minutesBefore,
                                   Calendar now,
                                   int maxReminders,
                                   AcademicCalendar calendar) {
        if (courses == null || courses.isEmpty() || timeSettings == null
                || firstWeekStartMillis <= 0L || maxReminders <= 0) {
            return Collections.emptyList();
        }
        Calendar current = now == null ? Calendar.getInstance() : (Calendar) now.clone();
        int weekCount = Math.max(1, semesterWeeks);
        int leadMinutes = Math.max(0, Math.min(180, minutesBefore));
        List<Entry> result = new ArrayList<>();
        for (Course course : courses) {
            if (course == null || !course.hasScheduledTime()) {
                continue;
            }
            CourseTimeResolver.TimeRange range = CourseTimeResolver.timeRange(
                    course, timeSettings);
            if (range == null) {
                continue;
            }
            for (int week = 1; week <= weekCount; week++) {
                if (!CourseTimeResolver.isActiveInWeek(course, week)) {
                    continue;
                }
                // 一周七天逐日比对：正常日、补班日都能命中，放假日自然被排除。
                for (int offset = 0; offset < 7; offset++) {
                    Calendar classStart = Calendar.getInstance(current.getTimeZone());
                    classStart.setTimeInMillis(firstWeekStartMillis);
                    classStart.add(Calendar.DATE, (week - 1) * 7 + offset);
                    int effectiveDay = calendar == null
                            ? offset
                            : calendar.effectiveWeekday(
                                    AcademicCalendar.dateKey(classStart), offset);
                    if (effectiveDay != course.day) {
                        continue;
                    }
                    classStart.set(Calendar.HOUR_OF_DAY, range.startMinutes / 60);
                    classStart.set(Calendar.MINUTE, range.startMinutes % 60);
                    classStart.set(Calendar.SECOND, 0);
                    classStart.set(Calendar.MILLISECOND, 0);
                    long triggerAt = classStart.getTimeInMillis() - leadMinutes * 60_000L;
                    if (triggerAt <= current.getTimeInMillis()) {
                        continue;
                    }
                    result.add(new Entry(course, week, classStart.getTimeInMillis(), triggerAt,
                            range.displayText()));
                }
            }
        }
        Collections.sort(result, new Comparator<Entry>() {
            @Override
            public int compare(Entry first, Entry second) {
                int byTrigger = Long.compare(first.triggerAtMillis, second.triggerAtMillis);
                if (byTrigger != 0) {
                    return byTrigger;
                }
                int byName = safe(first.course.name).compareTo(safe(second.course.name));
                if (byName != 0) {
                    return byName;
                }
                CourseTimeResolver.TimeRange firstRange = CourseTimeResolver.timeRange(
                        first.course, timeSettings);
                CourseTimeResolver.TimeRange secondRange = CourseTimeResolver.timeRange(
                        second.course, timeSettings);
                return Integer.compare(
                        firstRange == null ? Integer.MAX_VALUE : firstRange.startMinutes,
                        secondRange == null ? Integer.MAX_VALUE : secondRange.startMinutes);
            }
        });
        if (result.size() > maxReminders) {
            return new ArrayList<>(result.subList(0, maxReminders));
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
