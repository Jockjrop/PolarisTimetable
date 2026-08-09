package com.polaris.timetable.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructuredCourse {
    public final String id;
    public final String name;
    public final String teacher;
    public final String defaultLocation;
    public final List<CourseMeeting> meetings;
    public final String rawText;
    public final String credit;
    public final String color;
    public final CourseType courseType;

    public StructuredCourse(String id, String name, String teacher, String defaultLocation,
                            List<CourseMeeting> meetings, String rawText) {
        this(id, name, teacher, defaultLocation, meetings, rawText, "", "");
    }

    public StructuredCourse(String id, String name, String teacher, String defaultLocation,
                            List<CourseMeeting> meetings, String rawText, String credit, String color) {
        this(id, name, teacher, defaultLocation, meetings, rawText, credit, color, CourseType.LECTURE);
    }

    public StructuredCourse(String id, String name, String teacher, String defaultLocation,
                            List<CourseMeeting> meetings, String rawText, String credit, String color,
                            CourseType courseType) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.teacher = teacher == null ? "" : teacher;
        this.defaultLocation = defaultLocation == null ? "" : defaultLocation;
        this.meetings = meetings == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(meetings));
        this.rawText = rawText == null ? "" : rawText;
        this.credit = credit == null ? "" : credit;
        this.color = color == null ? "" : color;
        this.courseType = courseType == null ? CourseType.LECTURE : courseType;
    }

    public boolean hasMultipleMeetings() {
        return meetings.size() > 1;
    }
}
