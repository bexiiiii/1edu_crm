package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.RoomLoadResponse;
import com.ondeedu.analytics.service.RoomLoadService;
import com.ondeedu.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analytics/room-load")
@RequiredArgsConstructor
@Tag(name = "Room Load", description = "Загрузка аудиторий")
public class RoomLoadController {

    private final RoomLoadService roomLoadService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Загрузка аудиторий за период + таймлайн на дату")
    public ApiResponse<RoomLoadResponse> getLoad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate timelineDate) {
        return ApiResponse.success(roomLoadService.getLoad(from, to, timelineDate));
    }
}
