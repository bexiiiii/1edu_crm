package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.SubscriptionReportResponse;
import com.ondeedu.analytics.service.SubscriptionReportService;
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
@RequestMapping("/api/v1/analytics/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription Report", description = "Отчёт по абонементам")
public class SubscriptionReportController {

    private final SubscriptionReportService subscriptionReportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Отчёт по абонементам за период")
    public ApiResponse<SubscriptionReportResponse> getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean onlySuspicious) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        return ApiResponse.success(subscriptionReportService.getReport(from, to, onlySuspicious));
    }
}
