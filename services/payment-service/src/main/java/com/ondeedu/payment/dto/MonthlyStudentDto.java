package com.ondeedu.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class MonthlyStudentDto {

    private UUID studentId;
    private UUID subscriptionId;

    /** Ожидаемый взнос за месяц */
    private BigDecimal expected;

    /** Фактически оплачено за месяц */
    private BigDecimal paid;

    /** Долг за этот месяц */
    private BigDecimal debt;

    /** PAID / PARTIAL / UNPAID */
    private String status;
}
