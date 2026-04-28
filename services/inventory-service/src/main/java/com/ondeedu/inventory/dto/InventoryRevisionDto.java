package com.ondeedu.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class InventoryRevisionDto implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private UUID id;
    private UUID branchId;
    private LocalDate revisionDate;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private String status;
    private String notes;
    private UUID performedBy;
    private int totalItems;
    private int surplusItems;
    private int shortageItems;
    private int okItems;
    private Instant createdAt;
}
