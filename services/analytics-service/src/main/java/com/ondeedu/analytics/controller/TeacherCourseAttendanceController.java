package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.TeacherCourseAttendanceResponse;
import com.ondeedu.analytics.export.AnalyticsExcelExportService;
import com.ondeedu.analytics.service.TeacherCourseAttendanceService;
import com.ondeedu.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics/teacher-course-attendance")
@RequiredArgsConstructor
@Tag(name = "Teacher Course Attendance", description = "Посещаемость курса преподавателя по месяцам")
public class TeacherCourseAttendanceController {

    private final TeacherCourseAttendanceService teacherCourseAttendanceService;
    private final AnalyticsExcelExportService excelExportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'TEACHER') or hasAuthority('ANALYTICS_VIEW') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Посещаемость курса преподавателя")
    public ApiResponse<TeacherCourseAttendanceResponse> getAttendance(
            @RequestParam UUID teacherId,
            @RequestParam UUID courseId,
            @RequestParam(required = false) String month) {
        return ApiResponse.success(teacherCourseAttendanceService.getTeacherCourseAttendance(teacherId, courseId, month));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'TEACHER') or hasAuthority('ANALYTICS_VIEW') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Скачать посещаемость курса преподавателя в Excel")
    public ResponseEntity<byte[]> exportAttendance(
            @RequestParam UUID teacherId,
            @RequestParam UUID courseId,
            @RequestParam(required = false) String month) {
        var report = teacherCourseAttendanceService.getTeacherCourseAttendance(teacherId, courseId, month);
        byte[] file = excelExportService.exportTeacherCourseAttendance(report);
        return ExcelResponseFactory.attachment("teacher-course-attendance.xlsx", file);
    }
}
