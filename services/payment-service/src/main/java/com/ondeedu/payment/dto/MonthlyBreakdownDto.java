package com.ondeedu.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MonthlyBreakdownDto {

    /** Месяц в формате YYYY-MM */
    private String month;

    /** Ожидаемая сумма за этот месяц */
    private BigDecimal expected;

    /** Фактически оплачено */
    private BigDecimal paid;

    /** Долг = max(0, expected - paid) */
    private BigDecimal debt;

    /** PAID / PARTIAL / UNPAID */
    private String status;

    /** Список фактических платежей за этот месяц */
    private List<StudentPaymentDto> payments;
}
