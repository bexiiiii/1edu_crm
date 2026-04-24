package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TeacherCourseAttendanceResponse {
    private UUID teacherId;
    private String teacherName;
    private UUID courseId;
    private String courseName;
    private String month;
    private Double avgAttendanceRate;
    private int totalLessons;
    private int attendedLessons;
    private int absentLessons;
    private int plannedLessons;
    private List<CourseLessonDetail> lessons;

    @Data
    @Builder
    public static class CourseLessonDetail {
        private UUID lessonId;
        private String lessonDate;
        private String lessonType;
        private int totalStudents;
        private int attendedCount;
        private int absentCount;
        private int plannedLessons;
        private Double attendanceRate;
    }
}
