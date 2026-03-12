package com.ondeedu.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Студент с задолженностью.
 *
 * <p>{@code balance} = (сумма оплат) − (сумма активных абонементов).
 * Отрицательное значение означает долг перед учебным центром.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtorDto {

    private UUID       studentId;
    private String     fullName;
    /** Баланс: отрицательный = долг студента (сумма < 0). */
    private BigDecimal balance;
}
