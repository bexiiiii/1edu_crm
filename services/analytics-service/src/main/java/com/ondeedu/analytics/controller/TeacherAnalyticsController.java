package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.TeacherAnalyticsResponse;
import com.ondeedu.analytics.service.TeacherAnalyticsService;
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
@RequestMapping("/api/v1/analytics/teachers")
@RequiredArgsConstructor
@Tag(name = "Teachers", description = "Аналитика преподавателей")
public class TeacherAnalyticsController {

    private final TeacherAnalyticsService teacherAnalyticsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Аналитика преподавателей — выручка, загрузка, удержание, лучший сотрудник")
    public ApiResponse<TeacherAnalyticsResponse> getAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        return ApiResponse.success(teacherAnalyticsService.getAnalytics(from, to));
    }
}
