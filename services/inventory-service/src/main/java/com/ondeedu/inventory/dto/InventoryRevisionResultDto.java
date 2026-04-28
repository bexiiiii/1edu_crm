package com.ondeedu.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InventoryRevisionResultDto implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private UUID revisionId;
    private LocalDate revisionDate;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private String notes;

    private int totalItems;
    private int surplusItems;    // излишки (actual > system)
    private int shortageItems;   // недостача (actual < system)
    private int okItems;         // совпадение

    private List<RevisionLineDto> lines;

    @Data
    @Builder
    public static class RevisionLineDto implements Serializable {
        @Serial private static final long serialVersionUID = 1L;

        private UUID itemId;
        private String itemName;
        private BigDecimal systemQuantity;   // учётный остаток
        private BigDecimal actualQuantity;   // фактический остаток
        private BigDecimal difference;       // actual - system (+ излишек, - недостача)

        /** OK | SURPLUS | SHORTAGE */
        private String discrepancyType;

        private UUID transactionId;          // ADJUSTMENT транзакция (null если OK)
        private String notes;
    }
}
