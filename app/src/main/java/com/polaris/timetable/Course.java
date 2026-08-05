package com.polaris.timetable;

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
    }
}
