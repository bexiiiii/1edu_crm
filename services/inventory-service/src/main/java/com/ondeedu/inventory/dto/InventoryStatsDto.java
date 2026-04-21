package com.ondeedu.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStatsDto {
    private long totalItems;
    private long lowStockCount;
    private long outOfStockCount;
    private long totalTransactions;
    private long totalCategories;
}
