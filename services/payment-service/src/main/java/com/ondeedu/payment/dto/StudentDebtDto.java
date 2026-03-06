package com.ondeedu.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class StudentDebtDto {

    private UUID studentId;
    private UUID subscriptionId;

    /** Суммарный долг по всем просроченным месяцам */
    private BigDecimal totalDebt;

    /** Количество месяцев с долгом */
    private int debtMonths;

    /** Ежемесячный взнос (для справки) */
    private BigDecimal monthlyExpected;
}
