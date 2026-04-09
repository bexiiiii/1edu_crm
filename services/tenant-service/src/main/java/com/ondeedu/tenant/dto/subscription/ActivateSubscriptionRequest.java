package com.ondeedu.tenant.dto.subscription;

import com.ondeedu.tenant.entity.BillingPeriod;
import com.ondeedu.tenant.entity.TenantPlan;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivateSubscriptionRequest {
    @NotNull
    private TenantPlan plan;
    @NotNull
    private BillingPeriod billingPeriod;
}
