package com.ondeedu.settings.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.settings.dto.SettingsDto;
import com.ondeedu.settings.dto.UpdateSettingsRequest;
import com.ondeedu.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Tenant settings API")
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','RECEPTIONIST','TEACHER') or hasAuthority('SETTINGS_VIEW')")
    @Operation(summary = "Get current tenant settings")
    public ApiResponse<SettingsDto> getSettings() {
        return ApiResponse.success(settingsService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Update tenant settings")
    public ApiResponse<SettingsDto> updateSettings(@Valid @RequestBody UpdateSettingsRequest request) {
        return ApiResponse.success(settingsService.upsertSettings(request), "Settings updated successfully");
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Upload tenant logo and update logoUrl")
    public ApiResponse<SettingsDto> uploadLogo(@RequestPart("file") MultipartFile file,
                                               JwtAuthenticationToken authentication) {
        String bearerToken = "Bearer " + authentication.getToken().getTokenValue();
        return ApiResponse.success(settingsService.uploadLogo(file, bearerToken), "Logo uploaded successfully");
    }
}
