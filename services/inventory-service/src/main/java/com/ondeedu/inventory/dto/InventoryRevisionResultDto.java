package com.ondeedu.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InventoryRevisionResultDto implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private int totalItems;
    private int adjustedItems;
    private int unchangedItems;
    private List<RevisionLineDto> lines;

    @Data
    @Builder
    public static class RevisionLineDto implements Serializable {
        @Serial private static final long serialVersionUID = 1L;

        private UUID itemId;
        private String itemName;
        private BigDecimal systemQuantity;
        private BigDecimal actualQuantity;
        private BigDecimal difference;        // actual - system
        private boolean adjusted;
        private UUID transactionId;           // null если unchanged
    }
}
