package com.ondeedu.tenant.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.tenant.dto.CreateTenantRequest;
import com.ondeedu.tenant.dto.TenantDto;
import com.ondeedu.tenant.dto.UpdateTenantRequest;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
@Tag(name = "Tenants", description = "Tenant management API")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Create a new tenant")
    public ApiResponse<TenantDto> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantDto tenant = tenantService.createTenant(request);
        return ApiResponse.success(tenant, "Tenant created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Get tenant by ID")
    public ApiResponse<TenantDto> getTenant(@PathVariable UUID id) {
        return ApiResponse.success(tenantService.getTenant(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update tenant")
    public ApiResponse<TenantDto> updateTenant(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTenantRequest request) {
        TenantDto tenant = tenantService.updateTenant(id, request);
        return ApiResponse.success(tenant, "Tenant updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Delete tenant (only if INACTIVE)")
    public ApiResponse<Void> deleteTenant(@PathVariable UUID id) {
        tenantService.deleteTenant(id);
        return ApiResponse.success("Tenant deleted successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "List tenants with optional status filter")
    public ApiResponse<PageResponse<TenantDto>> listTenants(
            @RequestParam(required = false) TenantStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(tenantService.listTenants(status, pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Search tenants by name or email")
    public ApiResponse<PageResponse<TenantDto>> searchTenants(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(tenantService.searchTenants(query, pageable));
    }
}
