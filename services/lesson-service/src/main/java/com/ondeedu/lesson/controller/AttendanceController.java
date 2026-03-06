package com.ondeedu.lesson.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.lesson.dto.AttendanceDto;
import com.ondeedu.lesson.dto.BulkMarkAttendanceRequest;
import com.ondeedu.lesson.dto.MarkAttendanceRequest;
import com.ondeedu.lesson.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons/{lessonId}/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Lesson attendance API")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_MARK_ATTENDANCE')")
    @Operation(summary = "Mark attendance for a single student")
    public ApiResponse<AttendanceDto> markAttendance(
            @PathVariable UUID lessonId,
            @Valid @RequestBody MarkAttendanceRequest request) {
        AttendanceDto dto = attendanceService.markAttendance(lessonId, request);
        return ApiResponse.success(dto, "Attendance marked successfully");
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_MARK_ATTENDANCE')")
    @Operation(summary = "Bulk mark attendance for multiple students")
    public ApiResponse<List<AttendanceDto>> bulkMarkAttendance(
            @PathVariable UUID lessonId,
            @Valid @RequestBody BulkMarkAttendanceRequest request) {
        List<AttendanceDto> dtos = attendanceService.bulkMarkAttendance(lessonId, request);
        return ApiResponse.success(dtos, "Bulk attendance marked successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Get all attendance records for a lesson")
    public ApiResponse<List<AttendanceDto>> getLessonAttendance(@PathVariable UUID lessonId) {
        List<AttendanceDto> dtos = attendanceService.getLessonAttendance(lessonId);
        return ApiResponse.success(dtos);
    }

    @PostMapping("/mark-all")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_MARK_ATTENDANCE')")
    @Operation(summary = "Mark all given students as attended (Отметить всех)")
    public ApiResponse<List<AttendanceDto>> markAllPresent(
            @PathVariable UUID lessonId,
            @RequestBody List<UUID> studentIds) {
        List<AttendanceDto> dtos = attendanceService.markAllPresent(lessonId, studentIds);
        return ApiResponse.success(dtos, "All students marked as present");
    }
}
