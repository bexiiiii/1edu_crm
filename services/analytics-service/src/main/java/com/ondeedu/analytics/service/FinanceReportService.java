package com.ondeedu.analytics.service;

import com.ondeedu.analytics.dto.response.*;
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
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceReportService {

    private final AnalyticsRepository repo;

    @Cacheable(value = AnalyticsCacheNames.FINANCE_REPORT, keyGenerator = "tenantCacheKeyGenerator")
    @Transactional(readOnly = true)
    public FinanceReportResponse getReport(LocalDate from, LocalDate to) {
        String schema = TenantContext.getSchemaName();
        PeriodHelper.Period prev = PeriodHelper.previous(from, to);

        Map<String, Object> summary = repo.getFinanceSummary(schema, from, to);
        Map<String, Object> prevSummary = repo.getFinanceSummary(schema, prev.from(), prev.to());

        BigDecimal revenue = toBigDecimal(summary.get("revenue"));
        BigDecimal expenses = toBigDecimal(summary.get("expenses"));
        BigDecimal profit = revenue.subtract(expenses);

        BigDecimal revenuePrev = toBigDecimal(prevSummary.get("revenue"));
        BigDecimal expensesPrev = toBigDecimal(prevSummary.get("expenses"));
        BigDecimal profitPrev = revenuePrev.subtract(expensesPrev);

        double revDelta = PeriodHelper.deltaPct(revenue, revenuePrev);
        double expDelta = PeriodHelper.deltaPct(expenses, expensesPrev);
        double profitDelta = PeriodHelper.deltaPct(profit, profitPrev);

        // Динамика по месяцам
        List<Map<String, Object>> monthlyRaw = repo.getMonthlyFinance(schema, from, to);
        List<MonthlyFinanceDto> monthly = monthlyRaw.stream()
                .map(r -> {
                    String monthKey = (String) r.get("month"); // "2026-02"
                    BigDecimal rev = toBigDecimal(r.get("revenue"));
                    BigDecimal exp = toBigDecimal(r.get("expenses"));
                    return MonthlyFinanceDto.builder()
                            .month(monthKey)
                            .label(formatMonthLabel(monthKey))
                            .revenue(rev)
                            .expenses(exp)
                            .profit(rev.subtract(exp))
                            .build();
                })
                .collect(Collectors.toList());

        // Доходы по статьям
        List<CategoryAmountDto> revenueByCategory = repo.getRevenueByCategory(schema, from, to).stream()
                .map(r -> CategoryAmountDto.builder()
                        .category((String) r.get("category"))
                        .amount(toBigDecimal(r.get("amount")))
                        .build())
                .collect(Collectors.toList());

        // Расходы по статьям
        List<CategoryAmountDto> expensesByCategory = repo.getExpensesByCategory(schema, from, to).stream()
                .map(r -> CategoryAmountDto.builder()
                        .category((String) r.get("category"))
                        .amount(toBigDecimal(r.get("amount")))
                        .build())
                .collect(Collectors.toList());

        // Доходы по источникам
        List<CategoryAmountDto> revenueBySource = repo.getRevenueBySource(schema, from, to).stream()
                .map(r -> CategoryAmountDto.builder()
                        .category((String) r.get("category"))
                        .amount(toBigDecimal(r.get("amount")))
                        .build())
                .collect(Collectors.toList());

        // Доходы по группам
        List<GroupRevenueDto> revenueByGroup = repo.getRevenueByGroup(schema, from, to).stream()
                .map(r -> GroupRevenueDto.builder()
                        .groupId(toUuid(r.get("group_id")))
                        .groupName((String) r.get("group_name"))
                        .revenue(toBigDecimal(r.get("revenue")))
                        .build())
                .collect(Collectors.toList());

        // Сверка с абонементами
        Map<String, Object> rec = repo.getSubscriptionReconciliation(schema, from, to);
        BigDecimal totalSubs = toBigDecimal(rec.get("total_subscription_amount"));
        BigDecimal revenueFromSubs = toBigDecimal(rec.get("revenue_from_subscriptions"));
        BigDecimal paidBefore = toBigDecimal(rec.get("paid_before_period"));
        BigDecimal debt = toBigDecimal(rec.get("debt_from_subscriptions"));
        double coverage = totalSubs.compareTo(BigDecimal.ZERO) == 0 ? 0
                : Math.round(revenueFromSubs.doubleValue() / totalSubs.doubleValue() * 10000.0) / 100.0;
        BigDecimal nonSubRevenue = revenue.subtract(revenueFromSubs);

        SubscriptionReconciliationDto reconciliation = SubscriptionReconciliationDto.builder()
                .totalSubscriptionAmount(totalSubs)
                .revenueFromSubscriptions(revenueFromSubs)
                .paidBeforePeriod(paidBefore)
                .debtFromSubscriptions(debt)
                .coverageRate(coverage)
                .paidAfterPeriod(BigDecimal.ZERO)
                .paidBeforePeriodPayments(paidBefore)
                .revenueNotFromSubscriptions(nonSubRevenue)
                .studentsWithoutPayments(toLong(rec.get("students_without_payments")))
                .subscriptionsWithoutPayments(toLong(rec.get("subscriptions_without_payments")))
                .build();

        return FinanceReportResponse.builder()
                .revenue(revenue)
                .revenueDeltaPct(revDelta)
                .expenses(expenses)
                .expensesDeltaPct(expDelta)
                .profit(profit)
                .profitDeltaPct(profitDelta)
                .monthly(monthly)
                .revenueByCategory(revenueByCategory)
                .expensesByCategory(expensesByCategory)
                .revenueBySource(revenueBySource)
                .revenueByGroup(revenueByGroup)
                .reconciliation(reconciliation)
                .build();
    }

    private String formatMonthLabel(String monthKey) {
        // "2026-02" -> "Февраль 2026"
        String[] parts = monthKey.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        String monthName = Month.of(month).getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
        return capitalize(monthName) + " " + year;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static BigDecimal toBigDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }

    private static UUID toUuid(Object val) {
        if (val == null) return null;
        if (val instanceof UUID u) return u;
        return UUID.fromString(val.toString());
    }
}
