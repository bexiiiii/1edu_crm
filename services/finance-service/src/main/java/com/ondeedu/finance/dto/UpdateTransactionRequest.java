package com.ondeedu.finance.dto;

import com.ondeedu.finance.entity.TransactionStatus;
import com.ondeedu.finance.entity.TransactionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTransactionRequest {
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal amount;
    private String currency;
    private String category;
    private String description;
    private LocalDate transactionDate;
    private UUID studentId;
    private String notes;
}
