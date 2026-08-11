package com.polaris.timetable;

import com.polaris.timetable.model.CourseType;
import com.polaris.timetable.model.CourseTimeMode;

public class Course {
    public final int day;
    public final int startSection;
    public final int endSection;
    public final String name;
    public final String weeks;
    public final String location;
    public final String teacher;
    public final String raw;
    public final String credit;
    public final String color;
    public final CourseType courseType;
    public final String structuredCourseId;
    public final String meetingId;
    public final CourseTimeMode timeMode;
    public final int startMinuteOfDay;
    public final int endMinuteOfDay;

    public Course(int day, int startSection, int endSection, String name, String weeks,
                  String location, String teacher, String raw) {
        this(day, startSection, endSection, name, weeks, location, teacher, raw, "");
    }

    public Course(int day, int startSection, int endSection, String name, String weeks,
                  String location, String teacher, String raw, String credit) {
        this(day, startSection, endSection, name, weeks, location, teacher, raw, credit, "");
    }

    public Course(int day, int startSection, int endSection, String name, String weeks,
                  String location, String teacher, String raw, String credit, String color) {
        this(day, startSection, endSection, name, weeks, location, teacher, raw,
                credit, color, CourseType.LECTURE);
    }

    public Course(int day, int startSection, int endSection, String name, String weeks,
                  String location, String teacher, String raw, String credit, String color,
                  CourseType courseType) {
        this(day, startSection, endSection, name, weeks, location, teacher, raw,
                credit, color, courseType, "");
    }

    public Course(int day, int startSection, int endSection, String name, String weeks,
                  String location, String teacher, String raw, String credit, String color,
                  CourseType courseType, String structuredCourseId) {
        this(day, startSection, endSection, name, weeks, location, teacher, raw,
                credit, color, courseType, structuredCourseId, "");
    }

    public Course(int day, int startSection, int endSection, String name, String weeks,
                  String location, String teacher, String raw, String credit, String color,
                  CourseType courseType, String structuredCourseId, String meetingId) {
        this(day, startSection, endSection, name, weeks, location, teacher, raw,
                credit, color, courseType, structuredCourseId, meetingId,
                CourseTimeMode.normalize(null, day, startSection, endSection, -1, -1),
                -1, -1);
    }

    public Course(int day, int startSection, int endSection, String name, String weeks,
                  String location, String teacher, String raw, String credit, String color,
                  CourseType courseType, String structuredCourseId, String meetingId,
                  CourseTimeMode timeMode, int startMinuteOfDay, int endMinuteOfDay) {
        this.day = day;
        this.startSection = startSection;
        this.endSection = endSection;
        this.name = name;
        this.weeks = weeks;
        this.location = location;
        this.teacher = teacher;
        this.raw = raw;
        this.credit = credit == null ? "" : credit;
        this.color = color == null ? "" : color;
        this.courseType = courseType == null ? CourseType.LECTURE : courseType;
        this.structuredCourseId = structuredCourseId == null ? "" : structuredCourseId;
        this.meetingId = meetingId == null ? "" : meetingId;
        this.timeMode = CourseTimeMode.normalize(
                timeMode, day, startSection, endSection, startMinuteOfDay, endMinuteOfDay);
        this.startMinuteOfDay = this.timeMode == CourseTimeMode.CLOCK
                ? startMinuteOfDay : -1;
        this.endMinuteOfDay = this.timeMode == CourseTimeMode.CLOCK
                ? endMinuteOfDay : -1;
    }

    public boolean hasFixedTime() {
        return hasSectionTime();
    }

    public boolean hasSectionTime() {
        return timeMode == CourseTimeMode.SECTION
                && day >= 0 && day <= 6
                && startSection >= 1 && endSection >= startSection;
    }

    public boolean hasExactTime() {
        return timeMode == CourseTimeMode.CLOCK
                && day >= 0 && day <= 6
                && startMinuteOfDay >= 0
                && startMinuteOfDay < endMinuteOfDay
                && endMinuteOfDay <= 24 * 60;
    }

    public boolean hasScheduledTime() {
        return hasSectionTime() || hasExactTime();
    }

    public boolean isPracticeBannerOnly() {
        return courseType == CourseType.PRACTICE && !hasScheduledTime();
    }

    public boolean isBannerOnlyCourse() {
        return courseType.supportsBannerOnly() && !hasScheduledTime();
    }
}
