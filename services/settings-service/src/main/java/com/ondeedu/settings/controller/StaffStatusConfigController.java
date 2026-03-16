package com.ondeedu.settings.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.settings.dto.SaveStaffStatusRequest;
import com.ondeedu.settings.dto.StaffStatusConfigDto;
import com.ondeedu.settings.service.StaffStatusConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/staff-statuses")
@RequiredArgsConstructor
@Tag(name = "Staff Status Config", description = "Custom staff status management")
public class StaffStatusConfigController {

    private final StaffStatusConfigService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER')")
    @Operation(summary = "Get all custom staff statuses ordered by sort order")
    public ApiResponse<List<StaffStatusConfigDto>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Create a custom staff status")
    public ApiResponse<StaffStatusConfigDto> create(@Valid @RequestBody SaveStaffStatusRequest request) {
        return ApiResponse.success(service.create(request), "Staff status created successfully");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update a custom staff status")
    public ApiResponse<StaffStatusConfigDto> update(@PathVariable UUID id,
                                                    @Valid @RequestBody SaveStaffStatusRequest request) {
        return ApiResponse.success(service.update(id, request), "Staff status updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Delete a custom staff status")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Staff status deleted successfully");
    }
}
