package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.LeadConversionResponse;
import com.ondeedu.analytics.dto.response.SalesFunnelResponse;
import com.ondeedu.analytics.repository.AnalyticsRepository;
import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.ondeedu.analytics.config.AnalyticsCacheNames;
import org.springframework.cache.annotation.Cacheable;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FunnelService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.FUNNEL, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public SalesFunnelResponse getFunnel(LocalDate from, LocalDate to) {
        String schema = TenantContext.getSchemaName();
        PeriodHelper.Period prev = PeriodHelper.previous(from, to);

        Map<String, Object> summary = repo.getLeadsSummary(schema, from, to);
        Map<String, Object> prevSummary = repo.getLeadsSummary(schema, prev.from(), prev.to());

        long totalLeads = toLong(summary.get("total_leads"));
        long totalPrev = toLong(prevSummary.get("total_leads"));
        long successful = toLong(summary.get("successful_deals"));
        long successfulPrev = toLong(prevSummary.get("successful_deals"));
        long failed = toLong(summary.get("failed_deals"));
        long failedPrev = toLong(prevSummary.get("failed_deals"));
        long open = toLong(summary.get("open_deals"));
        long openPrev = toLong(prevSummary.get("open_deals"));

        List<Map<String, Object>> stagesRaw = repo.getLeadsByStage(schema, from, to);
        List<SalesFunnelResponse.FunnelStageDto> stages = stagesRaw.stream()
                .map(r -> SalesFunnelResponse.FunnelStageDto.builder()
                        .stage((String) r.get("stage"))
                        .count(toLong(r.get("cnt")))
                        .pct(toDouble(r.get("pct")))
                        .build())
                .collect(Collectors.toList());

        return SalesFunnelResponse.builder()
                .stages(stages)
                .totalLeads(totalLeads)
                .totalLeadsDeltaPct(PeriodHelper.deltaPct(totalLeads, totalPrev))
                .successfulDeals(successful)
                .successfulDealsDeltaPct(PeriodHelper.deltaPct(successful, successfulPrev))
                .failedDeals(failed)
                .failedDealsDeltaPct(PeriodHelper.deltaPct(failed, failedPrev))
                .openDeals(open)
                .openDealsDeltaPct(PeriodHelper.deltaPct(open, openPrev))
                .avgDealDurationDays(0) // TODO: посчитать из lead_activities
                .build();
    }

    @Cacheable(value = AnalyticsCacheNames.LEAD_CONVERSIONS, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public LeadConversionResponse getConversions(LocalDate from, LocalDate to) {
        String schema = TenantContext.getSchemaName();

        List<Map<String, Object>> bySource = repo.getLeadsBySource(schema, from, to);
        List<Map<String, Object>> byManager = repo.getLeadsByManager(schema, from, to);
        List<Map<String, Object>> stages = repo.getLeadsByStage(schema, from, to);

        // Конверсии между этапами (упрощённо: LINEAR)
        List<String> stageOrder = List.of("NEW", "CONTACTED", "QUALIFIED", "TRIAL", "NEGOTIATION", "WON");
        Map<String, Long> stageCounts = stages.stream()
                .collect(Collectors.toMap(r -> (String) r.get("stage"), r -> toLong(r.get("cnt"))));
        long totalLeads = toLong(repo.getLeadsSummary(schema, from, to).get("total_leads"));

        List<LeadConversionResponse.StageConversionDto> stageConversions = stageOrder.stream()
                .map(s -> LeadConversionResponse.StageConversionDto.builder()
                        .stageFrom(s)
                        .stageTo(s)
                        .entries(stageCounts.getOrDefault(s, 0L))
                        .strictConversionPct(totalLeads == 0 ? 0
                                : Math.round(100.0 * stageCounts.getOrDefault(s, 0L) / totalLeads * 100) / 100.0)
                        .build())
                .collect(Collectors.toList());

        List<LeadConversionResponse.SourceConversionDto> sourceDtos = bySource.stream()
                .map(r -> LeadConversionResponse.SourceConversionDto.builder()
                        .source((String) r.get("source"))
                        .leads(toLong(r.get("leads")))
                        .contracts(toLong(r.get("contracts")))
                        .conversionPct(toDouble(r.get("conversion_pct")))
                        .build())
                .collect(Collectors.toList());

        List<LeadConversionResponse.ManagerConversionDto> managerDtos = byManager.stream()
                .map(r -> LeadConversionResponse.ManagerConversionDto.builder()
                        .manager((String) r.get("manager"))
                        .leads(toLong(r.get("leads")))
                        .contracts(toLong(r.get("contracts")))
                        .conversionPct(toDouble(r.get("conversion_pct")))
                        .frtP50Days(toDouble(r.get("frt_p50")))
                        .frtP75Days(toDouble(r.get("frt_p75")))
                        .frtP90Days(toDouble(r.get("frt_p90")))
                        .build())
                .collect(Collectors.toList());

        List<LeadConversionResponse.StageSummaryDto> stageSummary = stages.stream()
                .map(r -> LeadConversionResponse.StageSummaryDto.builder()
                        .stage((String) r.get("stage"))
                        .count(toLong(r.get("cnt")))
                        .pct(toDouble(r.get("pct")))
                        .build())
                .collect(Collectors.toList());

        // Пробные
        Map<String, Object> trial = repo.getTrialConversion(schema, from, to);
        long trialScheduled = toLong(trial.get("trial_scheduled"));
        long trialAttended = toLong(trial.get("trial_attended"));

        // ARPU / средний чек / ARPPU (ARPPU = доход / платившие)
        BigDecimal arpu = repo.getArpu(schema, from, to);
        BigDecimal avgCheck = repo.getAverageCheck(schema, from, to);

        return LeadConversionResponse.builder()
                .stageConversions(stageConversions)
                .bySource(sourceDtos)
                .byManager(managerDtos)
                .stageSummary(stageSummary)
                .avgDaysToContract(0)
                .medianDaysP50(0)
                .medianDaysP75(0)
                .medianDaysP90(0)
                .trialScheduled(trialScheduled)
                .trialAttended(trialAttended)
                .trialConverted30d(0)
                .trialScheduledPct(100.0)
                .trialAttendedPct(trialScheduled == 0 ? 0 : Math.round(100.0 * trialAttended / trialScheduled * 100) / 100.0)
                .trialConverted30dPct(0)
                .arpu(arpu)
                .arppu(avgCheck)
                .avgCheck(avgCheck)
                .rpr(0)
                .build();
    }

    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }

    private static double toDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}
