package com.ondeedu.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_revision_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryRevisionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revision_id", nullable = false)
    private InventoryRevision revision;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "item_name", nullable = false, length = 200)
    private String itemName;

    @Column(name = "system_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "actual_quantity", nullable = false, precision = 15, scale = 3)
    private BigDecimal actualQuantity;

    /** actual - system: >0 излишек, <0 недостача, =0 норма */
    @Column(name = "difference", nullable = false, precision = 15, scale = 3)
    private BigDecimal difference;

    /** OK | SURPLUS | SHORTAGE */
    @Column(name = "discrepancy_type", nullable = false, length = 20)
    private String discrepancyType;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
