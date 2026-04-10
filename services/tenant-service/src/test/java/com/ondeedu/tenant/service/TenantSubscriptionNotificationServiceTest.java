package com.ondeedu.tenant.service;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.event.EmailNotificationEvent;
import com.ondeedu.tenant.entity.BillingPeriod;
import com.ondeedu.tenant.entity.Tenant;
import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSubscriptionNotificationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private TenantSubscriptionNotificationService notificationService;

    @Test
    void shouldPublishTrialExpiredEmail() {
        Tenant tenant = baseTenant()
                .status(TenantStatus.TRIAL)
                .trialEndsAt(LocalDate.now().minusDays(1))
                .build();
        tenant.setId(UUID.randomUUID());

        when(tenantRepository.findAllByStatusAndTrialEndsAtIsNotNull(TenantStatus.TRIAL))
                .thenReturn(List.of(tenant));
        when(jdbcTemplate.queryForObject(anyString(), anyMap(), eq(Boolean.class)))
                .thenReturn(false);

        notificationService.sendTrialExpiredNotifications();

        ArgumentCaptor<EmailNotificationEvent> eventCaptor = ArgumentCaptor.forClass(EmailNotificationEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.NOTIFICATION_EXCHANGE),
                eq(RabbitMQConfig.NOTIFICATION_EMAIL_KEY),
                eventCaptor.capture()
        );

        EmailNotificationEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("tenant.trial.expired." + tenant.getTrialEndsAt());
        assertThat(event.getRecipientEmail()).isEqualTo("owner@example.com");
        assertThat(event.getSubject()).contains("Пробный период");
    }

    @Test
    void shouldPublishPaymentReminderOneDayBeforeDueDate() {
        Instant subscriptionEndAt = Instant.now().plus(1, ChronoUnit.DAYS);
        Tenant tenant = baseTenant()
                .status(TenantStatus.ACTIVE)
                .billingPeriod(BillingPeriod.MONTHLY)
                .subscriptionPrice(BigDecimal.valueOf(20000))
                .subscriptionEndAt(subscriptionEndAt)
                .build();
        tenant.setId(UUID.randomUUID());

        when(tenantRepository.findAllByStatusAndSubscriptionEndAtIsNotNull(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant));
        when(jdbcTemplate.queryForObject(anyString(), anyMap(), eq(Boolean.class)))
                .thenReturn(false);

        notificationService.sendUpcomingPaymentNotifications();

        ArgumentCaptor<EmailNotificationEvent> eventCaptor = ArgumentCaptor.forClass(EmailNotificationEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.NOTIFICATION_EXCHANGE),
                eq(RabbitMQConfig.NOTIFICATION_EMAIL_KEY),
                eventCaptor.capture()
        );

        LocalDate dueDate = subscriptionEndAt.atZone(java.time.ZoneId.of("UTC")).toLocalDate();
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("tenant.subscription.payment_due." + dueDate);
        assertThat(eventCaptor.getValue().getBody()).contains("Сумма к оплате: 20000 KZT.");
    }

    @Test
    void shouldPublishOverdueEmailWhenSubscriptionExpired() {
        Instant subscriptionEndAt = Instant.now().minus(2, ChronoUnit.DAYS);
        Tenant tenant = baseTenant()
                .status(TenantStatus.ACTIVE)
                .billingPeriod(BillingPeriod.SIX_MONTHS)
                .subscriptionPrice(BigDecimal.valueOf(18000))
                .subscriptionEndAt(subscriptionEndAt)
                .build();
        tenant.setId(UUID.randomUUID());

        when(tenantRepository.findAllByStatusAndSubscriptionEndAtIsNotNull(TenantStatus.ACTIVE))
                .thenReturn(List.of(tenant));
        when(jdbcTemplate.queryForObject(anyString(), anyMap(), eq(Boolean.class)))
                .thenReturn(false);

        notificationService.sendOverduePaymentNotifications();

        ArgumentCaptor<EmailNotificationEvent> eventCaptor = ArgumentCaptor.forClass(EmailNotificationEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.NOTIFICATION_EXCHANGE),
                eq(RabbitMQConfig.NOTIFICATION_EMAIL_KEY),
                eventCaptor.capture()
        );

        assertThat(eventCaptor.getValue().getSubject()).contains("не продлена");
        assertThat(eventCaptor.getValue().getBody()).contains("Сумма к оплате: 108000 KZT.");
    }

    @Test
    void shouldSkipWhenSameNotificationWasAlreadySent() {
        Tenant tenant = baseTenant()
                .status(TenantStatus.TRIAL)
                .trialEndsAt(LocalDate.now().minusDays(2))
                .build();
        tenant.setId(UUID.randomUUID());

        when(tenantRepository.findAllByStatusAndTrialEndsAtIsNotNull(TenantStatus.TRIAL))
                .thenReturn(List.of(tenant));
        when(jdbcTemplate.queryForObject(anyString(), anyMap(), eq(Boolean.class)))
                .thenReturn(true);

        notificationService.sendTrialExpiredNotifications();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(EmailNotificationEvent.class));
    }

    private Tenant.TenantBuilder baseTenant() {
        return Tenant.builder()
                .name("Test Center")
                .email("owner@example.com")
                .contactPerson("Owner Name")
                .subdomain("test-center")
                .schemaName("tenant_test")
                .plan(TenantPlan.BASIC)
                .timezone("UTC");
    }
}
