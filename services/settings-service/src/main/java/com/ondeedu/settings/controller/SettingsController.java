package com.ondeedu.settings.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.settings.dto.ApiPaySettingsDto;
import com.ondeedu.settings.dto.AisarSettingsDto;
import com.ondeedu.settings.dto.CloudBackupRunResultDto;
import com.ondeedu.settings.dto.FtelecomSettingsDto;
import com.ondeedu.settings.dto.GoogleDriveBackupSettingsDto;
import com.ondeedu.settings.dto.KpaySettingsDto;
import com.ondeedu.settings.dto.SettingsDto;
import com.ondeedu.settings.dto.UpdateGoogleDriveBackupSettingsRequest;
import com.ondeedu.settings.dto.UpdateAisarSettingsRequest;
import com.ondeedu.settings.dto.UpdateApiPaySettingsRequest;
import com.ondeedu.settings.dto.UpdateFtelecomSettingsRequest;
import com.ondeedu.settings.dto.UpdateKpaySettingsRequest;
import com.ondeedu.settings.dto.UpdateSettingsRequest;
import com.ondeedu.settings.dto.UpdateYandexDiskBackupSettingsRequest;
import com.ondeedu.settings.dto.UpdateZadarmaSettingsRequest;
import com.ondeedu.settings.dto.YandexDiskBackupSettingsDto;
import com.ondeedu.settings.dto.ZadarmaSettingsDto;
import com.ondeedu.settings.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Tenant settings API")
public class SettingsController {

    private final SettingsService settingsService;

    @Value("${ondeedu.frontend.settings-url:https://app.1edu.kz/settings}")
    private String frontendSettingsUrl;

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

    @GetMapping("/kpay")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Get KPAY integration settings")
    public ApiResponse<KpaySettingsDto> getKpaySettings() {
        return ApiResponse.success(settingsService.getKpaySettings());
    }

    @PutMapping("/kpay")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Update KPAY integration settings")
    public ApiResponse<KpaySettingsDto> updateKpaySettings(@RequestBody UpdateKpaySettingsRequest request) {
        return ApiResponse.success(settingsService.upsertKpaySettings(request),
                "KPAY settings updated successfully");
    }

    @GetMapping("/apipay")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Get ApiPay integration settings")
    public ApiResponse<ApiPaySettingsDto> getApiPaySettings() {
        return ApiResponse.success(settingsService.getApiPaySettings());
    }

    @PutMapping("/apipay")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Update ApiPay integration settings")
    public ApiResponse<ApiPaySettingsDto> updateApiPaySettings(@RequestBody UpdateApiPaySettingsRequest request) {
        return ApiResponse.success(settingsService.upsertApiPaySettings(request),
                "ApiPay settings updated successfully");
    }

    @GetMapping("/aisar")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Get AISAR integration settings")
    public ApiResponse<AisarSettingsDto> getAisarSettings() {
        return ApiResponse.success(settingsService.getAisarSettings());
    }

    @PutMapping("/aisar")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Update AISAR integration settings")
    public ApiResponse<AisarSettingsDto> updateAisarSettings(@RequestBody UpdateAisarSettingsRequest request) {
        return ApiResponse.success(settingsService.upsertAisarSettings(request),
                "AISAR settings updated successfully");
    }

    @GetMapping("/ftelecom")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Get Freedom Telecom integration settings")
    public ApiResponse<FtelecomSettingsDto> getFtelecomSettings() {
        return ApiResponse.success(settingsService.getFtelecomSettings());
    }

    @PutMapping("/ftelecom")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Update Freedom Telecom integration settings")
    public ApiResponse<FtelecomSettingsDto> updateFtelecomSettings(@RequestBody UpdateFtelecomSettingsRequest request) {
        return ApiResponse.success(settingsService.upsertFtelecomSettings(request),
                "Freedom Telecom settings updated successfully");
    }

    @GetMapping("/zadarma")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Get Zadarma integration settings")
    public ApiResponse<ZadarmaSettingsDto> getZadarmaSettings() {
        return ApiResponse.success(settingsService.getZadarmaSettings());
    }

    @PutMapping("/zadarma")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Update Zadarma integration settings")
    public ApiResponse<ZadarmaSettingsDto> updateZadarmaSettings(@RequestBody UpdateZadarmaSettingsRequest request) {
        return ApiResponse.success(settingsService.upsertZadarmaSettings(request),
                "Zadarma settings updated successfully");
    }

    @GetMapping("/google-drive-backup")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Get Google Drive backup settings")
    public ApiResponse<GoogleDriveBackupSettingsDto> getGoogleDriveBackupSettings() {
        return ApiResponse.success(settingsService.getGoogleDriveBackupSettings());
    }

    @PutMapping("/google-drive-backup")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Update Google Drive backup settings")
    public ApiResponse<GoogleDriveBackupSettingsDto> updateGoogleDriveBackupSettings(
            @RequestBody UpdateGoogleDriveBackupSettingsRequest request
    ) {
        return ApiResponse.success(settingsService.upsertGoogleDriveBackupSettings(request),
                "Google Drive backup settings updated successfully");
    }

    @PostMapping("/google-drive-backup/run")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Run tenant backup upload to Google Drive")
    public ApiResponse<CloudBackupRunResultDto> runGoogleDriveBackup() {
        return ApiResponse.success(settingsService.runGoogleDriveBackup(),
                "Google Drive backup completed successfully");
    }

    @GetMapping("/google-drive-backup/oauth/connect-url")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Get Google Drive OAuth connect URL")
    public ApiResponse<String> getGoogleDriveOAuthConnectUrl() {
        return ApiResponse.success(settingsService.getGoogleDriveBackupOAuthConnectUrl());
    }

    @GetMapping(value = "/google-drive-backup/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Google Drive OAuth callback")
    public ResponseEntity<String> handleGoogleDriveOAuthCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription
    ) {
        if (error != null && !error.isBlank()) {
            return html(HttpStatus.BAD_REQUEST,
                    "Google Drive authorization failed: " + (errorDescription != null ? errorDescription : error));
        }

        try {
            settingsService.completeGoogleDriveBackupOAuth(code, state);
            return redirectToSettings();
        } catch (Exception ex) {
            return html(HttpStatus.BAD_REQUEST, "Google Drive authorization failed: " + ex.getMessage());
        }
    }

    @GetMapping("/yandex-disk-backup")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Get Yandex Disk backup settings")
    public ApiResponse<YandexDiskBackupSettingsDto> getYandexDiskBackupSettings() {
        return ApiResponse.success(settingsService.getYandexDiskBackupSettings());
    }

    @PutMapping("/yandex-disk-backup")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Update Yandex Disk backup settings")
    public ApiResponse<YandexDiskBackupSettingsDto> updateYandexDiskBackupSettings(
            @RequestBody UpdateYandexDiskBackupSettingsRequest request
    ) {
        return ApiResponse.success(settingsService.upsertYandexDiskBackupSettings(request),
                "Yandex Disk backup settings updated successfully");
    }

    @PostMapping("/yandex-disk-backup/run")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Run tenant backup upload to Yandex Disk")
    public ApiResponse<CloudBackupRunResultDto> runYandexDiskBackup() {
        return ApiResponse.success(settingsService.runYandexDiskBackup(),
                "Yandex Disk backup completed successfully");
    }

    @GetMapping("/yandex-disk-backup/oauth/connect-url")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Get Yandex Disk OAuth connect URL")
    public ApiResponse<String> getYandexDiskOAuthConnectUrl() {
        return ApiResponse.success(settingsService.getYandexDiskBackupOAuthConnectUrl());
    }

    @GetMapping(value = "/yandex-disk-backup/oauth/callback", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "Yandex Disk OAuth callback")
    public ResponseEntity<String> handleYandexDiskOAuthCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription
    ) {
        if (error != null && !error.isBlank()) {
            return html(HttpStatus.BAD_REQUEST,
                    "Yandex Disk authorization failed: " + (errorDescription != null ? errorDescription : error));
        }

        try {
            settingsService.completeYandexDiskBackupOAuth(code, state);
            return redirectToSettings();
        } catch (Exception ex) {
            return html(HttpStatus.BAD_REQUEST, "Yandex Disk authorization failed: " + ex.getMessage());
        }
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Upload tenant logo and update logoUrl")
    public ApiResponse<SettingsDto> uploadLogo(@RequestPart("file") MultipartFile file,
                                               JwtAuthenticationToken authentication) {
        String bearerToken = "Bearer " + authentication.getToken().getTokenValue();
        return ApiResponse.success(settingsService.uploadLogo(file, bearerToken), "Logo uploaded successfully");
    }

    private ResponseEntity<String> html(HttpStatus status, String message) {
        String body = "<html><body style='font-family:sans-serif;padding:24px;'>"
                + "<h3>1edu CRM</h3><p>" + escapeHtml(message) + "</p></body></html>";
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(body);
    }

        private ResponseEntity<String> redirectToSettings() {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, frontendSettingsUrl)
            .build();
        }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
