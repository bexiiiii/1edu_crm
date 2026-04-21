package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.BranchAnalyticsResponse;
import com.ondeedu.analytics.service.BranchAnalyticsService;
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

/**
 * Контроллер аналитики по филиалам.
 */
@RestController
@RequestMapping("/api/v1/analytics/branches")
@RequiredArgsConstructor
@Tag(name = "Branch Analytics", description = "Аналитика по филиалам")
public class BranchAnalyticsController {

    private final BranchAnalyticsService branchAnalyticsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Получить аналитику по всем филиалам tenant'а")
    public ApiResponse<BranchAnalyticsResponse> getBranchAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.success(branchAnalyticsService.getBranchAnalytics(from, to));
    }
}
