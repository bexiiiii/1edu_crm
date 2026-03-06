package com.ondeedu.report.client;

import com.ondeedu.common.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@FeignClient(name = "analytics-service")
public interface AnalyticsClient {

    @GetMapping("/api/v1/analytics/dashboard")
    ApiResponse<Object> getDashboard(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    );

    @GetMapping("/api/v1/analytics/finance-report")
    ApiResponse<Object> getFinanceReport(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    );
}
