package com.ondeedu.tenant.dto.subscription;

import com.ondeedu.tenant.entity.BillingPeriod;
import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class SubscriptionStatusDto {
    private TenantStatus tenantStatus;
    private TenantPlan plan;
    private BillingPeriod billingPeriod;

    /** For TRIAL: days until trial expires (negative = already expired) */
    private Long trialDaysRemaining;
    private LocalDate trialEndsAt;

    /** For ACTIVE: when subscription expires */
    private Instant subscriptionEndAt;
    private Long subscriptionDaysRemaining;

    private BigDecimal subscriptionPrice;

    /** Computed access state for the frontend */
    private AccessState accessState;

    public enum AccessState {
        TRIAL_ACTIVE,
        TRIAL_EXPIRED,
        SUBSCRIPTION_ACTIVE,
        SUBSCRIPTION_EXPIRED,
        SUSPENDED,
        BANNED,
        INACTIVE
    }
}
