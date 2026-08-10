package com.polaris.timetable;

import com.polaris.timetable.model.CourseType;

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
    }

    public boolean hasFixedTime() {
        return day >= 0 && day <= 6 && startSection >= 1 && endSection >= startSection;
    }

    public boolean isPracticeBannerOnly() {
        return courseType == CourseType.PRACTICE && !hasFixedTime();
    }

    public boolean isBannerOnlyCourse() {
        return courseType.supportsBannerOnly() && !hasFixedTime();
    }
}
