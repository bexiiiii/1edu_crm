package com.ondeedu.payment.dto;

import com.ondeedu.payment.entity.SubscriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SubscriptionPaymentSummaryDto {

    private UUID subscriptionId;
    private UUID courseId;
    private UUID priceListId;

    /** Общая сумма подписки */
    private BigDecimal totalAmount;

    /** Ежемесячный взнос (totalAmount / months_duration) */
    private BigDecimal monthlyExpected;

    /** Количество месяцев в подписке */
    private int totalMonths;

    private LocalDate startDate;
    private LocalDate endDate;
    private SubscriptionStatus subscriptionStatus;

    /** Итого оплачено по этой подписке */
    private BigDecimal totalPaid;

    /** Суммарный долг по этой подписке */
    private BigDecimal totalDebt;

    /** Помесячная разбивка от startDate до endDate */
    private List<MonthlyBreakdownDto> months;
}
