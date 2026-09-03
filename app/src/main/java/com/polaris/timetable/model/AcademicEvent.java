package com.polaris.timetable.model;

import java.util.Calendar;

/**
 * 学业事件（考试 / 截止 / 实践）：挂在绝对日期上的不可变记录。
 *
 * <p>与 {@link StudyPlan} 的区别：StudyPlan 用「学期周次 + 周几」表达相对课表周的待办，
 * AcademicEvent 用「绝对日期 + 时刻」表达与学期周次无关的日程（期中/期末考试、
 * 作业截止、实验/实践提交等），适合学期外或跨周的事件。
 *
 * <p>不可变：勾选完成或编辑时通过 {@code withXxx} 派生新实例。
 * dateMillis 为本地时区当天零点毫秒（由本地 Calendar 归一化），显示时按同一规则格式化。
 */
public final class AcademicEvent {

    /** 事件类型：考试 / 截止 / 实践。 */
    public enum Type {
        EXAM,
        DEADLINE,
        PRACTICE
    }

    public final String id;
    public final String title;
    public final String courseName;
    public final Type type;
    /** 本地时区当天零点毫秒（由本地 Calendar 归一化）。 */
    public final long dateMillis;
    /** 当天时刻（分钟，0..1439）；-1 表示未指定时刻（仅日期）。 */
    public final int minuteOfDay;
    public final String location;
    public final String seat;
    public final String note;
    public final boolean done;
    public final long createdAt;

    /** 默认时刻：09:00。 */
    public static final int DEFAULT_MINUTE_OF_DAY = 9 * 60;

    public AcademicEvent(String id, String title, String courseName, Type type,
                         long dateMillis, int minuteOfDay, String location, String seat,
                         String note, boolean done, long createdAt) {
        this.id = id == null ? "" : id.trim();
        this.title = title == null ? "" : title.trim();
        this.courseName = courseName == null ? "" : courseName.trim();
        this.type = type == null ? Type.EXAM : type;
        this.dateMillis = dateMillis;
        this.minuteOfDay = Math.max(-1, Math.min(23 * 60 + 59, minuteOfDay));
        this.location = location == null ? "" : location.trim();
        this.seat = seat == null ? "" : seat.trim();
        this.note = note == null ? "" : note.trim();
        this.done = done;
        this.createdAt = createdAt;
    }

    /** 归一化到本地当天零点（时/分/秒/毫秒清零）。 */
    public static long normalizedDateMillis(Calendar localDate) {
        Calendar normalized = (Calendar) localDate.clone();
        normalized.set(Calendar.HOUR_OF_DAY, 0);
        normalized.set(Calendar.MINUTE, 0);
        normalized.set(Calendar.SECOND, 0);
        normalized.set(Calendar.MILLISECOND, 0);
        return normalized.getTimeInMillis();
    }

    public boolean hasTime() {
        return minuteOfDay >= 0;
    }

    public boolean hasCourse() {
        return courseName.length() > 0;
    }

    public boolean hasLocation() {
        return location.length() > 0;
    }

    public boolean hasSeat() {
        return seat.length() > 0;
    }

    public boolean hasNote() {
        return note.length() > 0;
    }

    public AcademicEvent withDone(boolean done) {
        return new AcademicEvent(id, title, courseName, type, dateMillis, minuteOfDay,
                location, seat, note, done, createdAt);
    }
}
