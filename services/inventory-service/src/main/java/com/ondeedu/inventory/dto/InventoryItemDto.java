package com.ondeedu.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemDto {
    private UUID id;
    private UUID branchId;
    private UUID categoryId;
    private String categoryName;
    private UUID unitId;
    private String unitName;
    private String unitAbbreviation;
    private String name;
    private String description;
    private String sku;
    private String barcode;
    private String brand;
    private String model;
    private BigDecimal quantity;
    private BigDecimal minQuantity;
    private BigDecimal maxQuantity;
    private BigDecimal pricePerUnit;
    private BigDecimal sellingPrice;
    private String currency;
    private BigDecimal totalValue;
    private String location;
    private String supplier;
    private String supplierContact;
    private String status;
    private Boolean isActive;
    private Boolean isTracked;
    private Boolean requiresReorder;
    private String imageUrl;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
