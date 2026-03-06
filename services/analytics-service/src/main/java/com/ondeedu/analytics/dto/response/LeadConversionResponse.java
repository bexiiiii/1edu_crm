package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class LeadConversionResponse {

    // Конверсия между этапами
    private List<StageConversionDto> stageConversions;

    // По источникам
    private List<SourceConversionDto> bySource;

    // По менеджерам
    private List<ManagerConversionDto> byManager;

    // Сводка по этапам
    private List<StageSummaryDto> stageSummary;

    // Среднее время
    private double avgDaysToContract;
    private double medianDaysP50;
    private double medianDaysP75;
    private double medianDaysP90;

    // Пробные → конверсия
    private long trialScheduled;
    private long trialAttended;
    private long trialConverted30d;
    private double trialScheduledPct;
    private double trialAttendedPct;
    private double trialConverted30dPct;

    // ARPU / ARPPU / средний чек / RPR
    private BigDecimal arpu;
    private BigDecimal arppu;
    private BigDecimal avgCheck;
    private double rpr;

    @Data
    @Builder
    public static class StageConversionDto {
        private String stageFrom;
        private String stageTo;
        private long entries;
        private double strictConversionPct;
    }

    @Data
    @Builder
    public static class SourceConversionDto {
        private String source;
        private long leads;
        private long contracts;
        private double conversionPct;
    }

    @Data
    @Builder
    public static class ManagerConversionDto {
        private String manager;
        private long leads;
        private long contracts;
        private double conversionPct;
        private double frtP50Days;
        private double frtP75Days;
        private double frtP90Days;
    }

    @Data
    @Builder
    public static class StageSummaryDto {
        private String stage;
        private long count;
        private double pct;
    }
}
