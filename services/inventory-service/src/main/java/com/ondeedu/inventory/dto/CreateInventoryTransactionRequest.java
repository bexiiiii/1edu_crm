package com.ondeedu.inventory.dto;

import com.ondeedu.inventory.entity.InventoryTransaction.TransactionType;
import jakarta.validation.constraints.NotNull;
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
public class CreateInventoryTransactionRequest {

    @NotNull(message = "transactionType is required")
    private TransactionType transactionType;

    @NotNull(message = "quantity is required")
    private BigDecimal quantity;

    private String referenceType;

    private UUID referenceId;

    private String notes;

    private String reason;
}
