package com.ondeedu.inventory.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem extends BaseEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private InventoryCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private InventoryUnit unit;

    // Identification
    @Column(name = "sku", length = 50)
    private String sku;

    @Column(name = "barcode", length = 100)
    private String barcode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "model", length = 100)
    private String model;

    // Quantity & Pricing
    @Column(name = "quantity", precision = 15, scale = 3, nullable = false)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "min_quantity", precision = 15, scale = 3)
    private BigDecimal minQuantity;

    @Column(name = "max_quantity", precision = 15, scale = 3)
    private BigDecimal maxQuantity;

    @Column(name = "price_per_unit", precision = 15, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(name = "selling_price", precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "UZS";

    // Location & Storage
    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "supplier", length = 200)
    private String supplier;

    @Column(name = "supplier_contact", length = 100)
    private String supplierContact;

    // Status & Tracking
    @Column(name = "status", length = 30)
    @Builder.Default
    private String status = "IN_STOCK";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_tracked")
    @Builder.Default
    private Boolean isTracked = true;

    @Column(name = "requires_reorder")
    @Builder.Default
    private Boolean requiresReorder = false;

    // Media
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Вычисляемое поле — общая стоимость
    @Transient
    private BigDecimal totalValue;

    public void updateStatus() {
        if (minQuantity != null && quantity.compareTo(minQuantity) <= 0) {
            this.status = "LOW_STOCK";
            this.requiresReorder = true;
        } else if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = "OUT_OF_STOCK";
            this.requiresReorder = true;
        } else {
            this.status = "IN_STOCK";
            this.requiresReorder = false;
        }
    }

    public BigDecimal calculateTotalValue() {
        if (pricePerUnit != null && quantity != null) {
            return pricePerUnit.multiply(quantity);
        }
        return BigDecimal.ZERO;
    }
}
