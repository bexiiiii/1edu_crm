package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.GroupAttendanceResponse;
import com.ondeedu.analytics.service.GroupAttendanceService;
import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'TEACHER') or hasAuthority('ANALYTICS_VIEW') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Посещаемость группы (query API)")
    public ApiResponse<GroupAttendanceResponse> getAttendanceByQuery(
            @RequestParam UUID groupId,
            @RequestParam(required = false) Integer months,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate[] range = resolveRange(months, from, to);
        return ApiResponse.success(groupAttendanceService.getAttendance(groupId, range[0], range[1]));
    }

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'TEACHER') or hasAuthority('ANALYTICS_VIEW') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Посещаемость группы по месяцам")
    public ApiResponse<GroupAttendanceResponse> getAttendance(
            @PathVariable UUID groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(groupAttendanceService.getAttendance(groupId, from, to));
    }

    private LocalDate[] resolveRange(Integer months, LocalDate from, LocalDate to) {
        if (months != null) {
            if (months != 6 && months != 12) {
                throw new BusinessException(
                        "INVALID_MONTHS_FILTER",
                        "months must be either 6 or 12",
                        HttpStatus.BAD_REQUEST
                );
            }
            LocalDate rangeTo = LocalDate.now();
            LocalDate rangeFrom = rangeTo.withDayOfMonth(1).minusMonths(months - 1L);
            return new LocalDate[]{rangeFrom, rangeTo};
        }

        if (from == null || to == null) {
            throw new BusinessException(
                    "DATE_RANGE_REQUIRED",
                    "Either months=6|12 or both from and to must be provided",
                    HttpStatus.BAD_REQUEST
            );
        }

        return new LocalDate[]{from, to};
    }
}
