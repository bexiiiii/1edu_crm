package com.ondeedu.settings.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.settings.dto.AttendanceStatusConfigDto;
import com.ondeedu.settings.dto.SaveAttendanceStatusRequest;
import com.ondeedu.settings.service.AttendanceStatusConfigService;
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
@RequestMapping("/api/v1/settings/attendance-statuses")
@RequiredArgsConstructor
@Tag(name = "Attendance Status Config", description = "Custom attendance status management")
public class AttendanceStatusConfigController {

    private final AttendanceStatusConfigService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','RECEPTIONIST','TEACHER')")
    @Operation(summary = "Get all attendance status configs ordered by sort order")
    public ApiResponse<List<AttendanceStatusConfigDto>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Create a new attendance status config")
    public ApiResponse<AttendanceStatusConfigDto> create(@Valid @RequestBody SaveAttendanceStatusRequest request) {
        return ApiResponse.success(service.create(request), "Attendance status created successfully");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update an attendance status config")
    public ApiResponse<AttendanceStatusConfigDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody SaveAttendanceStatusRequest request) {
        return ApiResponse.success(service.update(id, request), "Attendance status updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Delete an attendance status config (system statuses cannot be deleted)")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Attendance status deleted successfully");
    }
}
