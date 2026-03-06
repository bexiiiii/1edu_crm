package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SubscriptionReconciliationDto {
    private BigDecimal totalSubscriptionAmount;
    private BigDecimal revenueFromSubscriptions;
    private BigDecimal paidBeforePeriod;
    private BigDecimal debtFromSubscriptions;
    private double coverageRate;
    private BigDecimal paidAfterPeriod;
    private BigDecimal paidBeforePeriodPayments;
    private BigDecimal revenueNotFromSubscriptions;
    private long studentsWithoutPayments;
    private long subscriptionsWithoutPayments;
}
