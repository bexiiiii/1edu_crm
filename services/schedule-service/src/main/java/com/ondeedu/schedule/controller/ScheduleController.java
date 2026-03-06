package com.ondeedu.schedule.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.schedule.dto.CreateScheduleRequest;
import com.ondeedu.schedule.dto.ScheduleDto;
import com.ondeedu.schedule.dto.UpdateScheduleRequest;
import com.ondeedu.schedule.entity.ScheduleStatus;
import com.ondeedu.schedule.service.ScheduleService;
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
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Schedules", description = "Schedule (class group) management API")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_CREATE')")
    @Operation(summary = "Create a new schedule")
    public ApiResponse<ScheduleDto> createSchedule(@Valid @RequestBody CreateScheduleRequest request) {
        return ApiResponse.success(scheduleService.createSchedule(request), "Schedule created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_VIEW')")
    @Operation(summary = "Get schedule by ID")
    public ApiResponse<ScheduleDto> getSchedule(@PathVariable UUID id) {
        return ApiResponse.success(scheduleService.getSchedule(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_EDIT')")
    @Operation(summary = "Update schedule")
    public ApiResponse<ScheduleDto> updateSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduleRequest request) {
        return ApiResponse.success(scheduleService.updateSchedule(id, request), "Schedule updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_DELETE')")
    @Operation(summary = "Delete schedule")
    public ApiResponse<Void> deleteSchedule(@PathVariable UUID id) {
        scheduleService.deleteSchedule(id);
        return ApiResponse.success("Schedule deleted successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_VIEW')")
    @Operation(summary = "List schedules with pagination and optional filters")
    public ApiResponse<PageResponse<ScheduleDto>> listSchedules(
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID teacherId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(scheduleService.listSchedules(status, courseId, teacherId, pageable));
    }

    @GetMapping("/room/{roomId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_VIEW')")
    @Operation(summary = "Get schedules by room")
    public ApiResponse<PageResponse<ScheduleDto>> getSchedulesByRoom(
            @PathVariable UUID roomId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(scheduleService.getSchedulesByRoom(roomId, pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_VIEW')")
    @Operation(summary = "Search schedules by name")
    public ApiResponse<PageResponse<ScheduleDto>> searchSchedules(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(scheduleService.searchSchedules(query, pageable));
    }
}
