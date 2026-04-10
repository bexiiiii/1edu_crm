package com.ondeedu.tenant.service;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.event.EmailNotificationEvent;
import com.ondeedu.tenant.entity.BillingPeriod;
import com.ondeedu.tenant.entity.Tenant;
import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSubscriptionNotificationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String NOTIFICATION_QUERY = """
            SELECT EXISTS (
                SELECT 1
                FROM system.notification_logs
                WHERE tenant_id = :tenantId
                  AND event_type = :eventType
                  AND status = 'SENT'
            )
            """;

    private final TenantRepository tenantRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    public void sendTrialExpiredNotifications() {
        List<Tenant> tenants = tenantRepository.findAllByStatusAndTrialEndsAtIsNotNull(TenantStatus.TRIAL);
        tenants.forEach(this::sendTrialExpiredNotificationIfNeeded);
    }

    public void sendUpcomingPaymentNotifications() {
        List<Tenant> tenants = tenantRepository.findAllByStatusAndSubscriptionEndAtIsNotNull(TenantStatus.ACTIVE);
        tenants.forEach(this::sendUpcomingPaymentNotificationIfNeeded);
    }

    public void sendOverduePaymentNotifications() {
        List<Tenant> tenants = tenantRepository.findAllByStatusAndSubscriptionEndAtIsNotNull(TenantStatus.ACTIVE);
        tenants.forEach(this::sendOverduePaymentNotificationIfNeeded);
    }

    private void sendTrialExpiredNotificationIfNeeded(Tenant tenant) {
        LocalDate trialEndDate = tenant.getTrialEndsAt();
        if (trialEndDate == null) {
            return;
        }

        LocalDate today = currentDate(tenant);
        if (!today.isAfter(trialEndDate)) {
            return;
        }

        String eventType = "tenant.trial.expired." + trialEndDate;
        if (wasNotificationSent(tenant.getId(), eventType)) {
            return;
        }

        publishEmail(tenant, eventType,
                "Пробный период 1edu CRM завершён",
                """
                %s

                Пробный период для центра "%s" завершился %s.
                Чтобы продолжить пользоваться 1edu CRM, активируйте платный тариф и оплатите SaaS-подписку.

                Если у вас уже есть оплата или нужна помощь с продлением, просто ответьте на это письмо.
                """.formatted(
                        greeting(tenant),
                        tenant.getName(),
                        formatDate(trialEndDate)
                ));
    }

    private void sendUpcomingPaymentNotificationIfNeeded(Tenant tenant) {
        LocalDate dueDate = subscriptionDueDate(tenant);
        if (dueDate == null) {
            return;
        }

        LocalDate today = currentDate(tenant);
        if (!today.equals(dueDate.minusDays(1))) {
            return;
        }

        String eventType = "tenant.subscription.payment_due." + dueDate;
        if (wasNotificationSent(tenant.getId(), eventType)) {
            return;
        }

        publishEmail(tenant, eventType,
                "Напоминание об оплате 1edu CRM",
                """
                %s

                %s заканчивается оплаченный период по тарифу %s для центра "%s".
                Чтобы доступ к 1edu CRM не прерывался, оплатите продление сегодня.
                %s
                """ .formatted(
                        greeting(tenant),
                        formatDate(dueDate),
                        formatPlan(tenant.getPlan()),
                        tenant.getName(),
                        renewalAmountLine(tenant)
                ).trim());
    }

    private void sendOverduePaymentNotificationIfNeeded(Tenant tenant) {
        LocalDate dueDate = subscriptionDueDate(tenant);
        if (dueDate == null) {
            return;
        }

        LocalDate today = currentDate(tenant);
        if (!today.isAfter(dueDate)) {
            return;
        }

        String eventType = "tenant.subscription.payment_overdue." + dueDate;
        if (wasNotificationSent(tenant.getId(), eventType)) {
            return;
        }

        publishEmail(tenant, eventType,
                "Подписка 1edu CRM не продлена",
                """
                %s

                Оплаченный период по тарифу %s для центра "%s" завершился %s, и мы пока не видим продления SaaS-подписки.
                Чтобы восстановить непрерывный доступ к 1edu CRM, оплатите продление.
                %s
                Если оплата уже выполнена, просто проигнорируйте это письмо.
                """.formatted(
                        greeting(tenant),
                        formatPlan(tenant.getPlan()),
                        tenant.getName(),
                        formatDate(dueDate),
                        renewalAmountLine(tenant)
                ));
    }

    private void publishEmail(Tenant tenant, String eventType, String subject, String body) {
        if (!StringUtils.hasText(tenant.getEmail())) {
            log.info("Skipping SaaS email {} for tenant {} because recipient email is blank", eventType, tenant.getId());
            return;
        }

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.NOTIFICATION_EMAIL_KEY,
                new EmailNotificationEvent(
                        eventType,
                        tenant.getId().toString(),
                        null,
                        tenant.getEmail().trim(),
                        subject,
                        body
                )
        );

        log.info("Published SaaS notification {} for tenant {}", eventType, tenant.getId());
    }

    private boolean wasNotificationSent(UUID tenantId, String eventType) {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    NOTIFICATION_QUERY,
                    Map.of(
                            "tenantId", tenantId.toString(),
                            "eventType", eventType
                    ),
                    Boolean.class
            ));
        } catch (Exception ex) {
            log.warn("Could not check notification log for tenant {} and event {}: {}", tenantId, eventType, ex.getMessage());
            return false;
        }
    }

    private LocalDate currentDate(Tenant tenant) {
        return LocalDate.now(resolveZone(tenant.getTimezone()));
    }

    private LocalDate subscriptionDueDate(Tenant tenant) {
        Instant subscriptionEndAt = tenant.getSubscriptionEndAt();
        if (subscriptionEndAt == null) {
            return null;
        }
        return subscriptionEndAt.atZone(resolveZone(tenant.getTimezone())).toLocalDate();
    }

    private ZoneId resolveZone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return ZoneId.of("UTC");
        }

        try {
            return ZoneId.of(timezone);
        } catch (Exception ex) {
            log.warn("Unknown tenant timezone '{}', falling back to UTC", timezone);
            return ZoneId.of("UTC");
        }
    }

    private String greeting(Tenant tenant) {
        if (StringUtils.hasText(tenant.getContactPerson())) {
            return "Здравствуйте, " + tenant.getContactPerson().trim() + "!";
        }
        return "Здравствуйте!";
    }

    private String renewalAmountLine(Tenant tenant) {
        BigDecimal amount = renewalAmount(tenant);
        if (amount == null) {
            return "";
        }
        return "Сумма к оплате: " + amount.stripTrailingZeros().toPlainString() + " KZT.";
    }

    private BigDecimal renewalAmount(Tenant tenant) {
        if (tenant.getSubscriptionPrice() == null) {
            return null;
        }

        BillingPeriod billingPeriod = tenant.getBillingPeriod();
        if (billingPeriod == null) {
            return tenant.getSubscriptionPrice();
        }

        return switch (billingPeriod) {
            case MONTHLY -> tenant.getSubscriptionPrice();
            case SIX_MONTHS -> tenant.getSubscriptionPrice().multiply(BigDecimal.valueOf(6));
            case ANNUAL -> tenant.getSubscriptionPrice().multiply(BigDecimal.valueOf(12));
        };
    }

    private String formatDate(LocalDate date) {
        return DATE_FORMATTER.format(date);
    }

    private String formatPlan(TenantPlan plan) {
        if (plan == null) {
            return "выбранному тарифу";
        }

        return switch (plan) {
            case BASIC -> "Базовый";
            case EXTENDED -> "Расширенный";
            case EXTENDED_PLUS -> "Расширенный+";
        };
    }
}
