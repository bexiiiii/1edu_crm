package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.ManagerEfficiencyResponse;
import com.ondeedu.analytics.service.ManagerEfficiencyService;
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
@RequestMapping("/api/v1/analytics/managers")
@RequiredArgsConstructor
@Tag(name = "Managers", description = "Эффективность менеджеров")
public class ManagerEfficiencyController {

    private final ManagerEfficiencyService managerEfficiencyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Эффективность менеджеров — лиды, конверсия, FRT")
    public ApiResponse<ManagerEfficiencyResponse> getEfficiency(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(managerEfficiencyService.getEfficiency(from, to));
    }
}
