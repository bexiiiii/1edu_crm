package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MonthlyFinanceDto {
    private String month;       // "2026-02"
    private String label;       // "Февраль 2026"
    private BigDecimal revenue;
    private BigDecimal expenses;
    private BigDecimal profit;
}
