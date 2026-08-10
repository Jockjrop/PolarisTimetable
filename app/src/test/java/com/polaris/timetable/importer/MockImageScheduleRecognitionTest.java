package com.polaris.timetable.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.polaris.timetable.Course;
import com.polaris.timetable.model.CourseMeeting;
import com.polaris.timetable.model.CourseStructureMapper;
import com.polaris.timetable.model.StableCourseId;
import com.polaris.timetable.model.StableMeetingId;
import com.polaris.timetable.model.StructuredCourse;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MockImageScheduleRecognitionTest {
    @Test
    public void mockRecognizerImplementsSharedInterface() {
        assertTrue(new MockImageScheduleRecognition() instanceof ImageScheduleRecognizer);
    }

    @Test
    public void recognize_returnsImportableCoursesWithUniqueStableIds() {
        ImageScheduleRecognitionResult result =
                MockImageScheduleRecognition.recognize();
        Set<String> courseIds = new HashSet<>();
        Set<String> meetingIds = new HashSet<>();

        assertTrue(result.isImportable());
        assertEquals(3, result.structuredCourses.size());
        assertEquals(4, result.meetingCount());
        for (StructuredCourse course : result.structuredCourses) {
            assertTrue(StableCourseId.isValid(course.id));
            assertTrue(courseIds.add(course.id));
            for (CourseMeeting meeting : course.meetings) {
                assertTrue(StableMeetingId.isValid(meeting.id));
                assertTrue(meetingIds.add(meeting.id));
            }
        }
    }

    @Test
    public void recognize_keepsSameNameDifferentTeacherCoursesSeparate() {
        List<StructuredCourse> courses =
                MockImageScheduleRecognition.recognize().structuredCourses;
        StructuredCourse firstEnglish = courses.get(1);
        StructuredCourse secondEnglish = courses.get(2);

        assertEquals("大学英语", firstEnglish.name);
        assertEquals("大学英语", secondEnglish.name);
        assertEquals("李老师", firstEnglish.teacher);
        assertEquals("王老师", secondEnglish.teacher);
        assertNotEquals(firstEnglish.id, secondEnglish.id);
    }

    @Test
    public void expandedCourseView_carriesCourseAndMeetingIdentity() {
        ImageScheduleRecognitionResult result =
                MockImageScheduleRecognition.recognize();
        List<Course> expanded = new CourseStructureMapper()
                .toLegacyCourses(result.structuredCourses);
        Set<String> expectedMeetingIds = new HashSet<>();
        Set<String> expectedCourseIds = new HashSet<>();
        for (StructuredCourse course : result.structuredCourses) {
            expectedCourseIds.add(course.id);
            for (CourseMeeting meeting : course.meetings) {
                expectedMeetingIds.add(meeting.id);
            }
        }

        assertEquals(4, expanded.size());
        for (Course course : expanded) {
            assertTrue(expectedCourseIds.contains(course.structuredCourseId));
            assertTrue(expectedMeetingIds.remove(course.meetingId));
        }
        assertTrue(expectedMeetingIds.isEmpty());
    }

    @Test
    public void separateRecognitionRuns_doNotReuseGeneratedIdentity() {
        ImageScheduleRecognitionResult first =
                MockImageScheduleRecognition.recognize();
        ImageScheduleRecognitionResult second =
                MockImageScheduleRecognition.recognize();

        assertNotEquals(first.structuredCourses.get(0).id,
                second.structuredCourses.get(0).id);
        assertNotEquals(first.structuredCourses.get(0).meetings.get(0).id,
                second.structuredCourses.get(0).meetings.get(0).id);
    }
}
