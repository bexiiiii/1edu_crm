package com.ondeedu.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class InventoryRevisionRequest {

    /** Дата проведения ревизии (обязательно) */
    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate revisionDate;

    /** Период ревизии — с какой даты проверяем движение */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate periodFrom;

    /** Период ревизии — по какую дату */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate periodTo;

    private String notes;

    @NotEmpty
    @Valid
    private List<RevisionItem> items;

    @Data
    public static class RevisionItem {
        @NotNull
        private UUID itemId;

        /** Фактическое количество по результатам пересчёта */
        @NotNull
        @PositiveOrZero
        private BigDecimal actualQuantity;

        private String notes;
    }
}
