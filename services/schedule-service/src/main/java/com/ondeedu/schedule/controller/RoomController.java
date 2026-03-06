package com.ondeedu.schedule.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.schedule.dto.CreateRoomRequest;
import com.ondeedu.schedule.dto.RoomDto;
import com.ondeedu.schedule.dto.UpdateRoomRequest;
import com.ondeedu.schedule.entity.RoomStatus;
import com.ondeedu.schedule.service.RoomService;
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
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "Rooms", description = "Room management API")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Create a new room")
    public ApiResponse<RoomDto> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return ApiResponse.success(roomService.createRoom(request), "Room created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'RECEPTIONIST', 'TEACHER')")
    @Operation(summary = "Get room by ID")
    public ApiResponse<RoomDto> getRoom(@PathVariable UUID id) {
        return ApiResponse.success(roomService.getRoom(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Update room")
    public ApiResponse<RoomDto> updateRoom(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomRequest request) {
        return ApiResponse.success(roomService.updateRoom(id, request), "Room updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Delete room")
    public ApiResponse<Void> deleteRoom(@PathVariable UUID id) {
        roomService.deleteRoom(id);
        return ApiResponse.success("Room deleted successfully");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'RECEPTIONIST', 'TEACHER')")
    @Operation(summary = "List rooms with pagination and optional status filter")
    public ApiResponse<PageResponse<RoomDto>> listRooms(
            @RequestParam(required = false) RoomStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(roomService.listRooms(status, pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'RECEPTIONIST', 'TEACHER')")
    @Operation(summary = "Search rooms by name")
    public ApiResponse<PageResponse<RoomDto>> searchRooms(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(roomService.searchRooms(query, pageable));
    }
}
