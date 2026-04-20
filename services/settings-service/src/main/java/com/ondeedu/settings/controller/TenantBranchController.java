package com.ondeedu.settings.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.settings.dto.BranchDto;
import com.ondeedu.settings.dto.SaveBranchRequest;
import com.ondeedu.settings.service.TenantBranchService;
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
@RequestMapping("/api/v1/settings/branches")
@RequiredArgsConstructor
@Tag(name = "Branches", description = "Tenant branch management")
public class TenantBranchController {

    private final TenantBranchService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','RECEPTIONIST','TEACHER') or hasAuthority('SETTINGS_VIEW')")
    @Operation(summary = "Get all tenant branches")
    public ApiResponse<List<BranchDto>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Create tenant branch")
    public ApiResponse<BranchDto> create(@Valid @RequestBody SaveBranchRequest request) {
        return ApiResponse.success(service.create(request), "Branch created successfully");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Update tenant branch")
    public ApiResponse<BranchDto> update(@PathVariable UUID id,
                                         @Valid @RequestBody SaveBranchRequest request) {
        return ApiResponse.success(service.update(id, request), "Branch updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SETTINGS_EDIT')")
    @Operation(summary = "Delete tenant branch")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Branch deleted successfully");
    }
}
