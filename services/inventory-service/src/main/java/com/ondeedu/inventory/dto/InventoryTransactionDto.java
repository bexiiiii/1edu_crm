package com.ondeedu.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransactionDto {
    private UUID id;
    private UUID branchId;
    private UUID itemId;
    private String itemName;
    private String transactionType;
    private BigDecimal quantity;
    private BigDecimal quantityBefore;
    private BigDecimal quantityAfter;
    private String referenceType;
    private UUID referenceId;
    private String referenceNumber;
    private UUID performedBy;
    private UUID approvedBy;
    private UUID recipientId;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private LocalDateTime transactionDate;
    private String notes;
    private String reason;
    private Instant createdAt;
}
