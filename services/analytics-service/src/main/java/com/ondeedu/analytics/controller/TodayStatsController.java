package com.ondeedu.analytics.controller;

import com.ondeedu.analytics.dto.response.TodayStatsResponse;
import com.ondeedu.analytics.service.TodayStatsService;
import com.ondeedu.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/analytics/today")
@RequiredArgsConstructor
@Tag(name = "Today Stats", description = "Главный дашборд — статистика за сегодня")
public class TodayStatsController {

    private final TodayStatsService todayStatsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER') or hasAuthority('ANALYTICS_VIEW')")
    @Operation(
            summary = "Статистика за выбранную дату",
            description = """
                    Возвращает сводку за дату (по умолчанию сегодня):
                    - Доходы / расходы / новые абонементы / занятия / посещения / записи
                    - Непродлённые абонементы (по дате, по остатку, просроченные)
                    - Задолженности студентов
                    - Неоплаченные посещения (за последние 30 дней)
                    - Ближайшие дни рождения (7 дней)
                    """
    )
    public ApiResponse<TodayStatsResponse> getStats(
            @Parameter(description = "Дата (ISO, по умолчанию сегодня)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        return ApiResponse.success(todayStatsService.getStats(date != null ? date : LocalDate.now()));
    }
}
