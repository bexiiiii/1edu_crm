package com.ondeedu.settings.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.settings.dto.RoleConfigDto;
import com.ondeedu.settings.dto.SaveRoleConfigRequest;
import com.ondeedu.settings.service.RoleConfigService;
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
@RequestMapping("/api/v1/settings/roles")
@RequiredArgsConstructor
@Tag(name = "Roles Config", description = "Custom role definitions with granular permissions")
public class RoleConfigController {

    private final RoleConfigService service;

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get all available permission codes for role building")
    public ApiResponse<List<String>> getAllPermissions() {
        return ApiResponse.success(service.getAllPermissions());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Get all role configs")
    public ApiResponse<List<RoleConfigDto>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get role config by ID")
    public ApiResponse<RoleConfigDto> getById(@PathVariable UUID id) {
        return ApiResponse.success(service.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Create a new role config")
    public ApiResponse<RoleConfigDto> create(@Valid @RequestBody SaveRoleConfigRequest request) {
        return ApiResponse.success(service.create(request), "Role created successfully");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update a role config")
    public ApiResponse<RoleConfigDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody SaveRoleConfigRequest request) {
        return ApiResponse.success(service.update(id, request), "Role updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Delete a role config")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Role deleted successfully");
    }
}
