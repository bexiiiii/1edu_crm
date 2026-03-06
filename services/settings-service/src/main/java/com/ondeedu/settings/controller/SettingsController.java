package com.ondeedu.settings.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.settings.dto.SettingsDto;
import com.ondeedu.settings.dto.UpdateSettingsRequest;
import com.ondeedu.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Tenant settings API")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','RECEPTIONIST','TEACHER')")
    @Operation(summary = "Get current tenant settings")
    public ApiResponse<SettingsDto> getSettings() {
        return ApiResponse.success(settingsService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update tenant settings")
    public ApiResponse<SettingsDto> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return ApiResponse.success(settingsService.upsertSettings(request), "Settings updated successfully");
    }
}
