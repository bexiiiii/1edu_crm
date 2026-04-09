package com.ondeedu.tenant.dto.subscription;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SubscriptionPlanDto {
    private String code;
    private String displayName;
    private BigDecimal monthlyPrice;
    private BigDecimal sixMonthMonthlyPrice;
    private BigDecimal annualMonthlyPrice;
    /** Total prices for the billing period */
    private BigDecimal sixMonthTotalPrice;
    private BigDecimal annualTotalPrice;
    /** Discount percentages */
    private int sixMonthDiscountPct;
    private int annualDiscountPct;
    private List<String> features;
    private int sortOrder;
}
