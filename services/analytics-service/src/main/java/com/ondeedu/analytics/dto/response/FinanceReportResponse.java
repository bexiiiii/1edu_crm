package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class FinanceReportResponse {

    // Итоги
    private BigDecimal revenue;
    private double revenueDeltaPct;
    private BigDecimal expenses;
    private double expensesDeltaPct;
    private BigDecimal profit;
    private double profitDeltaPct;

    // Динамика по месяцам
    private List<MonthlyFinanceDto> monthly;

    // Доходы по статьям (category)
    private List<CategoryAmountDto> revenueByCategory;

    // Доходы по источникам (source)
    private List<CategoryAmountDto> revenueBySource;

    // Доходы по группам
    private List<GroupRevenueDto> revenueByGroup;

    // Расходы по статьям
    private List<CategoryAmountDto> expensesByCategory;

    // Сверка с абонементами
    private SubscriptionReconciliationDto reconciliation;
}
