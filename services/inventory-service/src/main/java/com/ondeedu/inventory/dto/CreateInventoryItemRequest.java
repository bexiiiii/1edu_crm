package com.ondeedu.inventory.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreateInventoryItemRequest {

    private UUID categoryId;

    @NotNull(message = "unitId is required")
    private UUID unitId;

    private String sku;

    private String barcode;

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    private String brand;

    private String model;

    @NotNull(message = "quantity is required")
    private BigDecimal quantity;

    private BigDecimal minQuantity;

    private BigDecimal maxQuantity;

    private BigDecimal pricePerUnit;

    private BigDecimal sellingPrice;

    private String currency;

    private String location;

    private String supplier;

    private String supplierContact;

    private Boolean isTracked;

    private String imageUrl;

    private String notes;
}
