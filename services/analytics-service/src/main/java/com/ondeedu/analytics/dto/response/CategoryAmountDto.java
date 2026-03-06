package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CategoryAmountDto {
    private String category;
    private BigDecimal amount;
}
