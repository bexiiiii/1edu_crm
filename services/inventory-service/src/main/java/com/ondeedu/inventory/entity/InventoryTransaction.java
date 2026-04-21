package com.ondeedu.inventory.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction extends BaseEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(name = "quantity", precision = 15, scale = 3, nullable = false)
    private BigDecimal quantity;

    @Column(name = "quantity_before", precision = 15, scale = 3)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", precision = 15, scale = 3)
    private BigDecimal quantityAfter;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_number", length = 50)
    private String referenceNumber;

    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "recipient_id")
    private UUID recipientId;

    @Column(name = "unit_cost", precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "transaction_date", nullable = false)
    @Builder.Default
    private LocalDateTime transactionDate = LocalDateTime.now();

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    public enum TransactionType {
        RECEIVED,    // Поступление
        ISSUED,      // Выдача
        RETURNED,    // Возврат
        ADJUSTMENT,  // Корректировка
        WRITE_OFF,   // Списание
        TRANSFER     // Перемещение
    }
}
