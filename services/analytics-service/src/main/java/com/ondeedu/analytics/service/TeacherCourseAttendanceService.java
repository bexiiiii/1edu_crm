package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.TeacherCourseAttendanceResponse;
import com.ondeedu.analytics.repository.AnalyticsRepository;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.analytics.config.AnalyticsCacheNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeacherCourseAttendanceService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.GROUP_ATTENDANCE, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public TeacherCourseAttendanceResponse getTeacherCourseAttendance(UUID teacherId, UUID courseId, String month) {
        String schema = TenantContext.getSchemaName();
        YearMonth yearMonth = parseMonth(month);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        // Get teacher info
        String teacherName = "Unknown";
        try {
            Map<String, Object> teacherInfo = repo.getTeacherInfo(schema, teacherId);
            teacherName = (String) teacherInfo.get("full_name");
        } catch (Exception e) {
            log.warn("Teacher not found: {}", teacherId);
        }

        // Get course info
        String courseName = "Unknown";
        try {
            Map<String, Object> courseInfo = repo.getCourseInfo(schema, courseId);
            courseName = (String) courseInfo.get("name");
        } catch (Exception e) {
            log.warn("Course not found: {}", courseId);
        }

        // Get lesson-level attendance data
        List<Map<String, Object>> lessons = repo.getTeacherCourseAttendanceMonthly(
                schema, teacherId, courseId, monthStart, monthEnd);

        List<TeacherCourseAttendanceResponse.CourseLessonDetail> lessonDetails = new ArrayList<>();
        int totalLessons = 0;
        int attendedLessons = 0;
        int absentLessons = 0;
        int plannedLessons = 0;
        double totalRate = 0;
        int ratedLessons = 0;

        for (Map<String, Object> lesson : lessons) {
            int totalStudents = toInt(lesson.get("total_students"));
            int attended = toInt(lesson.get("attended_count"));
            int absent = toInt(lesson.get("absent_count"));
            int planned = toInt(lesson.get("planned_count"));
            double rate = toDouble(lesson.get("attendance_rate"));

            if (totalStudents > 0) {
                totalLessons++;
                attendedLessons += attended;
                absentLessons += absent;
                plannedLessons += planned;
                totalRate += rate;
                ratedLessons++;
            }

            lessonDetails.add(TeacherCourseAttendanceResponse.CourseLessonDetail.builder()
                    .lessonId((UUID) lesson.get("lesson_id"))
                    .lessonDate((String) lesson.get("lesson_date"))
                    .lessonType((String) lesson.get("lesson_type"))
                    .totalStudents(totalStudents)
                    .attendedCount(attended)
                    .absentCount(absent)
                    .plannedLessons(planned)
                    .attendanceRate(rate)
                    .build());
        }

        double avgAttendanceRate = ratedLessons > 0 ? Math.round((totalRate / ratedLessons) * 10) / 10.0 : 0.0;

        return TeacherCourseAttendanceResponse.builder()
                .teacherId(teacherId)
                .teacherName(teacherName)
                .courseId(courseId)
                .courseName(courseName)
                .month(yearMonth.format(MONTH_FORMATTER))
                .avgAttendanceRate(avgAttendanceRate)
                .totalLessons(totalLessons)
                .attendedLessons(attendedLessons)
                .absentLessons(absentLessons)
                .plannedLessons(plannedLessons)
                .lessons(lessonDetails)
                .build();
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FORMATTER);
        } catch (Exception e) {
            return YearMonth.now();
        }
    }

    private static int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
