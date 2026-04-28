package com.ondeedu.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class InventoryRevisionRequest {

    @NotEmpty
    @Valid
    private List<RevisionItem> items;

    private String notes;

    @Data
    public static class RevisionItem {
        @NotNull
        private UUID itemId;

        @NotNull
        @PositiveOrZero
        private BigDecimal actualQuantity;

        private String notes;
    }
}