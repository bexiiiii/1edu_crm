package com.ondeedu.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtorDto {

    private UUID studentId;

    private BigDecimal totalPaid;

    private BigDecimal totalSubscriptionCost;

    private BigDecimal debt;
}
