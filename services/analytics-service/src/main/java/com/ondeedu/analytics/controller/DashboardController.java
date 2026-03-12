package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.DashboardResponse;
import com.ondeedu.analytics.service.DashboardService;
import com.ondeedu.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Дашборд руководителя")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Получить все метрики дашборда руководителя")
    public ApiResponse<DashboardResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "ALL") String lessonType) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        return ApiResponse.success(dashboardService.getDashboard(from, to, lessonType));
    }
}
