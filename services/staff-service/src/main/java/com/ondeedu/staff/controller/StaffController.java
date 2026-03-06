package com.ondeedu.staff.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.staff.dto.*;
import com.ondeedu.staff.entity.StaffRole;
import com.ondeedu.staff.entity.StaffStatus;
import com.ondeedu.staff.service.StaffService;
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
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@Tag(name = "Staff", description = "Staff management API")
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STAFF_CREATE')")
    @Operation(summary = "Create a new staff member")
    public ApiResponse<StaffDto> createStaff(@Valid @RequestBody CreateStaffRequest request) {
        StaffDto staff = staffService.createStaff(request);
        return ApiResponse.success(staff, "Staff created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STAFF_VIEW')")
    @Operation(summary = "Get staff by ID")
    public ApiResponse<StaffDto> getStaff(@PathVariable UUID id) {
        return ApiResponse.success(staffService.getStaff(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STAFF_EDIT')")
    @Operation(summary = "Update staff")
    public ApiResponse<StaffDto> updateStaff(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStaffRequest request) {
        StaffDto staff = staffService.updateStaff(id, request);
        return ApiResponse.success(staff, "Staff updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STAFF_DELETE')")
    @Operation(summary = "Delete staff")
    public ApiResponse<Void> deleteStaff(@PathVariable UUID id) {
        staffService.deleteStaff(id);
        return ApiResponse.success("Staff deleted successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STAFF_VIEW')")
    @Operation(summary = "List staff with optional role/status filter")
    public ApiResponse<PageResponse<StaffDto>> listStaff(
            @RequestParam(required = false) StaffRole role,
            @RequestParam(required = false) StaffStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(staffService.listStaff(role, status, pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STAFF_VIEW')")
    @Operation(summary = "Search staff")
    public ApiResponse<PageResponse<StaffDto>> searchStaff(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(staffService.searchStaff(query, pageable));
    }
}
