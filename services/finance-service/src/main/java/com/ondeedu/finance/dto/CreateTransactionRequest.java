package com.ondeedu.finance.dto;

import com.ondeedu.finance.entity.TransactionType;
import com.ondeedu.finance.entity.AmountChangeReasonCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {
    @NotNull
    private TransactionType type;
    @NotNull
    @Positive
    private BigDecimal amount;
    private String currency;
    private String category;
    private String description;
    @NotNull
    private LocalDate transactionDate;
    private UUID studentId;
    private AmountChangeReasonCode amountChangeReasonCode;
    private String amountChangeReasonOther;
    private String notes;
}
