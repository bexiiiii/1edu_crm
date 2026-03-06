package com.ondeedu.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StudentPaymentHistoryResponse {

    private UUID studentId;

    /** Суммарный долг студента по всем подпискам */
    private BigDecimal totalDebt;

    /** Суммарно оплачено по всем подпискам */
    private BigDecimal totalPaid;

    /** Список подписок с помесячной разбивкой платежей */
    private List<SubscriptionPaymentSummaryDto> subscriptions;
}
