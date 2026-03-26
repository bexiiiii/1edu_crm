package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.RetentionResponse;
import com.ondeedu.analytics.service.RetentionService;
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
@RequestMapping("/api/v1/analytics/retention")
@RequiredArgsConstructor
@Tag(name = "Retention", description = "Когортный анализ удержания")
public class RetentionController {

    private final RetentionService retentionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Когортный анализ удержания студентов")
    public ApiResponse<RetentionResponse> getCohorts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "FIRST_PAYMENT") String cohortType) {
        if (from == null) from = LocalDate.now().minusMonths(6).withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        return ApiResponse.success(retentionService.getCohorts(from, to, cohortType));
    }
}
