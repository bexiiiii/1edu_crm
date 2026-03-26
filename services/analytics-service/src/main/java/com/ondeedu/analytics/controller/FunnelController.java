package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.LeadConversionResponse;
import com.ondeedu.analytics.dto.response.SalesFunnelResponse;
import com.ondeedu.analytics.service.FunnelService;
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
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Funnel", description = "Воронка продаж и конверсии лидов")
public class FunnelController {

    private final FunnelService funnelService;

    @GetMapping("/funnel")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Воронка продаж")
    public ApiResponse<SalesFunnelResponse> getFunnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        return ApiResponse.success(funnelService.getFunnel(from, to));
    }

    @GetMapping("/lead-conversions")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Конверсии лидов — детализация")
    public ApiResponse<LeadConversionResponse> getConversions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        return ApiResponse.success(funnelService.getConversions(from, to));
    }
}
