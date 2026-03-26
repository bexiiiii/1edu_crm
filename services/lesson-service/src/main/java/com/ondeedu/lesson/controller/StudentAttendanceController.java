package com.ondeedu.lesson.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.lesson.dto.AttendanceDto;
import com.ondeedu.lesson.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Tag(name = "Attendance", description = "Student attendance history API")
public class StudentAttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'RECEPTIONIST', 'TEACHER') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Get attendance history for a student")
    public ApiResponse<PageResponse<AttendanceDto>> getStudentAttendance(
            @PathVariable UUID studentId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(attendanceService.getStudentAttendance(studentId, pageable));
    }
}
