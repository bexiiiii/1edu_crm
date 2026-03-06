package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.GroupAttendanceResponse;
import com.ondeedu.analytics.service.GroupAttendanceService;
import com.ondeedu.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics/group-attendance")
@RequiredArgsConstructor
@Tag(name = "Group Attendance", description = "Посещаемость группы по месяцам")
public class GroupAttendanceController {

    private final GroupAttendanceService groupAttendanceService;

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'TEACHER')")
    @Operation(summary = "Посещаемость группы по месяцам")
    public ApiResponse<GroupAttendanceResponse> getAttendance(
            @PathVariable UUID groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(groupAttendanceService.getAttendance(groupId, from, to));
    }
}
