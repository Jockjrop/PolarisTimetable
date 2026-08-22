package com.polaris.timetable.model;

/**
 * 学习计划（任务清单项）：标题 + 可选关联课程 + 周次/周几 + 完成状态 + 可选提醒。
 *
 * <p>不可变：勾选完成或编辑时通过 {@code withXxx} 派生新实例，与 Course 模型风格一致。
 * week 为 1-based 学期周次；dayOfWeek 0=周一 … 6=周日（与 Course.day 一致）。
 */
public final class StudyPlan {
    public final String id;
    public final String title;
    public final String courseName;
    public final int week;
    public final int dayOfWeek;
    public final boolean done;
    public final boolean remindEnabled;
    /** 提醒时刻（分钟数，0..1439，当天触发）。默认 {@link #REMIND_DEFAULT_MINUTE}。 */
    public final int remindMinute;
    public final long createdAt;

    /** 默认提醒时刻：08:00。 */
    public static final int REMIND_DEFAULT_MINUTE = 8 * 60;

    public StudyPlan(String id, String title, String courseName, int week, int dayOfWeek,
                     boolean done, boolean remindEnabled, int remindMinute, long createdAt) {
        this.id = id == null ? "" : id.trim();
        this.title = title == null ? "" : title.trim();
        this.courseName = courseName == null ? "" : courseName.trim();
        this.week = Math.max(1, week);
        this.dayOfWeek = Math.max(0, Math.min(6, dayOfWeek));
        this.done = done;
        this.remindEnabled = remindEnabled;
        this.remindMinute = Math.max(0, Math.min(23 * 60 + 59, remindMinute));
        this.createdAt = createdAt;
    }

    public boolean hasCourse() {
        return courseName.length() > 0;
    }

    public boolean hasReminder() {
        return remindEnabled && !done;
    }

    public StudyPlan withDone(boolean done) {
        return new StudyPlan(id, title, courseName, week, dayOfWeek,
                done, remindEnabled, remindMinute, createdAt);
    }

    public StudyPlan withReminder(boolean enabled, int minute) {
        return new StudyPlan(id, title, courseName, week, dayOfWeek,
                done, enabled, minute, createdAt);
    }
}
