package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SalesFunnelResponse {

    // Воронка по этапам
    private List<FunnelStageDto> stages;

    // Итоги
    private long totalLeads;
    private double totalLeadsDeltaPct;
    private long successfulDeals;
    private double successfulDealsDeltaPct;
    private long failedDeals;
    private double failedDealsDeltaPct;

    // Средняя длительность сделки
    private double avgDealDurationDays;
    private double avgDealDurationDeltaDays;

    // Открытые сделки
    private long openDeals;
    private double openDealsDeltaPct;

    @Data
    @Builder
    public static class FunnelStageDto {
        private String stage;
        private long count;
        private double pct;
        private BigDecimal budget;
        // для графика воронки
        private long active;
        private long closed;
    }
}
