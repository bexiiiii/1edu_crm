package com.ondeedu.finance.dto;

import com.ondeedu.finance.entity.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryPaymentDto {
    private UUID transactionId;
    private UUID staffId;
    private String salaryMonth;
    private BigDecimal amount;
    private String currency;
    private LocalDate paymentDate;
    private String notes;
    private TransactionStatus status;
    private Instant createdAt;
}
