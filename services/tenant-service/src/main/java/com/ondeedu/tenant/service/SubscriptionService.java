package com.ondeedu.tenant.service;

import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.tenant.dto.subscription.ActivateSubscriptionRequest;
import com.ondeedu.tenant.dto.subscription.SubscriptionPlanDto;
import com.ondeedu.tenant.dto.subscription.SubscriptionStatusDto;
import com.ondeedu.tenant.entity.BillingPeriod;
import com.ondeedu.tenant.entity.Tenant;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String GATEWAY_CACHE_PREFIX = "sub-status:";

    private final TenantRepository tenantRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    // ── Public plans listing ────────────────────────────────────────────────

    public List<SubscriptionPlanDto> listPlans() {
        return jdbcTemplate.query(
            """
            SELECT code, display_name, monthly_price,
                   six_month_monthly_price, annual_monthly_price,
                   features, sort_order
            FROM system.subscription_plans
            WHERE is_active = TRUE
            ORDER BY sort_order
            """,
            Map.of(),
            (rs, i) -> {
                BigDecimal monthly    = rs.getBigDecimal("monthly_price");
                BigDecimal sixMonthly = rs.getBigDecimal("six_month_monthly_price");
                BigDecimal annually   = rs.getBigDecimal("annual_monthly_price");

                // Parse features JSON array
                String featuresJson = rs.getString("features");
                List<String> features = parseFeatures(featuresJson);

                return SubscriptionPlanDto.builder()
                    .code(rs.getString("code"))
                    .displayName(rs.getString("display_name"))
                    .monthlyPrice(monthly)
                    .sixMonthMonthlyPrice(sixMonthly)
                    .annualMonthlyPrice(annually)
                    .sixMonthTotalPrice(sixMonthly.multiply(BigDecimal.valueOf(6)))
                    .annualTotalPrice(annually.multiply(BigDecimal.valueOf(12)))
                    .sixMonthDiscountPct(10)
                    .annualDiscountPct(17)
                    .features(features)
                    .sortOrder(rs.getInt("sort_order"))
                    .build();
            }
        );
    }

    // ── Subscription status ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SubscriptionStatusDto getStatus(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));
        return buildStatus(tenant);
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusDto getStatusForGateway(UUID tenantId) {
        return tenantRepository.findById(tenantId)
            .map(this::buildStatus)
            .orElse(null);
    }

    // ── Activate subscription (SUPER_ADMIN) ─────────────────────────────────

    @Transactional
    public SubscriptionStatusDto activate(UUID tenantId, ActivateSubscriptionRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", tenantId));

        Instant now = Instant.now();
        Instant endAt = calculateEndDate(now, request.getBillingPeriod());
        BigDecimal price = resolvePrice(request.getPlan().name(), request.getBillingPeriod());

        tenant.setPlan(request.getPlan());
        tenant.setBillingPeriod(request.getBillingPeriod());
        tenant.setSubscriptionStartAt(now);
        tenant.setSubscriptionEndAt(endAt);
        tenant.setSubscriptionPrice(price);
        tenant.setStatus(TenantStatus.ACTIVE);

        tenantRepository.save(tenant);

        // Evict gateway subscription cache so new status takes effect immediately
        redisTemplate.delete(GATEWAY_CACHE_PREFIX + tenantId);

        log.info("Activated subscription for tenant {}: plan={} billing={}",
            tenantId, request.getPlan(), request.getBillingPeriod());

        return buildStatus(tenant);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private SubscriptionStatusDto buildStatus(Tenant tenant) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        SubscriptionStatusDto.AccessState accessState = resolveAccessState(tenant, today);

        Long trialDaysRemaining = null;
        if (tenant.getTrialEndsAt() != null) {
            trialDaysRemaining = ChronoUnit.DAYS.between(today, tenant.getTrialEndsAt());
        }

        Long subDaysRemaining = null;
        if (tenant.getSubscriptionEndAt() != null) {
            subDaysRemaining = ChronoUnit.DAYS.between(Instant.now(), tenant.getSubscriptionEndAt()) / 86400;
        }

        return SubscriptionStatusDto.builder()
            .tenantStatus(tenant.getStatus())
            .plan(tenant.getPlan())
            .billingPeriod(tenant.getBillingPeriod())
            .trialEndsAt(tenant.getTrialEndsAt())
            .trialDaysRemaining(trialDaysRemaining)
            .subscriptionEndAt(tenant.getSubscriptionEndAt())
            .subscriptionDaysRemaining(subDaysRemaining)
            .subscriptionPrice(tenant.getSubscriptionPrice())
            .accessState(accessState)
            .build();
    }

    private SubscriptionStatusDto.AccessState resolveAccessState(Tenant tenant, LocalDate today) {
        return switch (tenant.getStatus()) {
            case TRIAL -> {
                if (tenant.getTrialEndsAt() == null || !today.isAfter(tenant.getTrialEndsAt())) {
                    yield SubscriptionStatusDto.AccessState.TRIAL_ACTIVE;
                }
                yield SubscriptionStatusDto.AccessState.TRIAL_EXPIRED;
            }
            case ACTIVE -> {
                if (tenant.getSubscriptionEndAt() == null || Instant.now().isBefore(tenant.getSubscriptionEndAt())) {
                    yield SubscriptionStatusDto.AccessState.SUBSCRIPTION_ACTIVE;
                }
                yield SubscriptionStatusDto.AccessState.SUBSCRIPTION_EXPIRED;
            }
            case SUSPENDED -> SubscriptionStatusDto.AccessState.SUSPENDED;
            case BANNED    -> SubscriptionStatusDto.AccessState.BANNED;
            case INACTIVE  -> SubscriptionStatusDto.AccessState.INACTIVE;
        };
    }

    private Instant calculateEndDate(Instant start, BillingPeriod period) {
        return switch (period) {
            case MONTHLY    -> start.plus(30, ChronoUnit.DAYS);
            case SIX_MONTHS -> start.plus(183, ChronoUnit.DAYS);
            case ANNUAL     -> start.plus(365, ChronoUnit.DAYS);
        };
    }

    private BigDecimal resolvePrice(String planCode, BillingPeriod period) {
        String col = switch (period) {
            case MONTHLY    -> "monthly_price";
            case SIX_MONTHS -> "six_month_monthly_price";
            case ANNUAL     -> "annual_monthly_price";
        };
        return jdbcTemplate.queryForObject(
            "SELECT " + col + " FROM system.subscription_plans WHERE code = :code",
            Map.of("code", planCode),
            BigDecimal.class
        );
    }

    private List<String> parseFeatures(String json) {
        if (json == null) return List.of();
        // simple JSON array string → strip brackets, split by comma
        try {
            json = json.trim().replaceAll("^\\[|\\]$", "");
            return List.of(json.split("\",\"")).stream()
                .map(s -> s.replaceAll("^\"|\"$", "").trim())
                .filter(s -> !s.isEmpty())
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
