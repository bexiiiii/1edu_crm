package com.ondeedu.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
public class UpdateInventoryItemRequest {
    private UUID categoryId;

    @Size(max = 200)
    private String name;

    @Size(max = 1000)
    private String description;

    @Size(max = 50)
    private String sku;

    @Size(max = 100)
    private String brand;

    @Size(max = 100)
    private String model;

    @Min(value = 0, message = "Quantity must be >= 0")
    private BigDecimal quantity;

    @Min(value = 0, message = "Min quantity must be >= 0")
    private BigDecimal minQuantity;

    private BigDecimal maxQuantity;

    private BigDecimal pricePerUnit;

    private BigDecimal sellingPrice;

    @Size(max = 100)
    private String location;

    private String supplier;

    private String supplierContact;

    private Boolean isTracked;

    private String imageUrl;

    private String notes;
}
