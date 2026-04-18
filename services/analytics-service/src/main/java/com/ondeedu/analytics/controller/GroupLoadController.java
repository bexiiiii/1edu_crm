package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.GroupLoadResponse;
import com.ondeedu.analytics.export.AnalyticsExcelExportService;
import com.ondeedu.analytics.service.GroupLoadService;
import com.ondeedu.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics/group-load")
@RequiredArgsConstructor
@Tag(name = "Group Load", description = "Загрузка групп")
public class GroupLoadController {

    private final GroupLoadService groupLoadService;
    private final AnalyticsExcelExportService excelExportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Загрузка групп — текущий снимок")
    public ApiResponse<GroupLoadResponse> getLoad() {
        return ApiResponse.success(groupLoadService.getLoad());
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(summary = "Скачать загрузку групп в Excel")
    public ResponseEntity<byte[]> exportLoad() {
        GroupLoadResponse report = groupLoadService.getLoad();
        byte[] file = excelExportService.exportGroupLoad(report);
        return ExcelResponseFactory.attachment("group-load-report.xlsx", file);
    }
}
