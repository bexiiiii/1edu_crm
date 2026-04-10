package com.ondeedu.tenant.service;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.event.EmailNotificationEvent;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.tenant.client.AuthServiceClient;
import com.ondeedu.tenant.dto.RegisterRequest;
import com.ondeedu.tenant.dto.RegisterResponse;
import com.ondeedu.tenant.dto.TenantDto;
import com.ondeedu.tenant.entity.TenantPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private TenantService tenantService;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(registrationService, "baseDomain", "1edu.kz");
        ReflectionTestUtils.setField(registrationService, "tenantPublicScheme", "https");
    }

    @Test
    void shouldSendWelcomeEmailAfterSuccessfulRegistration() {
        RegisterRequest request = validRequest();
        UUID tenantId = UUID.randomUUID();

        when(tenantService.createTenant(any())).thenReturn(TenantDto.builder()
                .id(tenantId)
                .name("Alpha Center")
                .subdomain("alpha")
                .email("owner@alpha.kz")
                .plan(TenantPlan.BASIC)
                .trialEndsAt(LocalDate.of(2026, 4, 17))
                .build());

        RegisterResponse response = registrationService.register(request);

        assertThat(response.getTenantId()).isEqualTo(tenantId);
        verify(authServiceClient).createTenantAdmin(
                "Ali",
                "Bek",
                "owner@alpha.kz",
                "secret123",
                tenantId.toString()
        );

        ArgumentCaptor<EmailNotificationEvent> eventCaptor = ArgumentCaptor.forClass(EmailNotificationEvent.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMQConfig.NOTIFICATION_EXCHANGE),
                org.mockito.ArgumentMatchers.eq(RabbitMQConfig.NOTIFICATION_EMAIL_KEY),
                eventCaptor.capture()
        );

        EmailNotificationEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("tenant.registered.welcome");
        assertThat(event.getRecipientEmail()).isEqualTo("owner@alpha.kz");
        assertThat(event.getSubject()).contains("Добро пожаловать");
        assertThat(event.getBody()).contains("https://alpha.1edu.kz");
        assertThat(event.getBody()).contains("17.04.2026");
    }

    @Test
    void shouldNotFailRegistrationWhenWelcomeEmailPublishFails() {
        RegisterRequest request = validRequest();
        UUID tenantId = UUID.randomUUID();

        when(tenantService.createTenant(any())).thenReturn(TenantDto.builder()
                .id(tenantId)
                .name("Alpha Center")
                .subdomain("alpha")
                .email("owner@alpha.kz")
                .plan(TenantPlan.BASIC)
                .trialEndsAt(LocalDate.of(2026, 4, 17))
                .build());
        doThrow(new RuntimeException("rabbit down"))
                .when(rabbitTemplate)
                .convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), any(EmailNotificationEvent.class));

        RegisterResponse response = registrationService.register(request);

        assertThat(response.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void shouldRejectMismatchedPasswordsBeforeRegistration() {
        RegisterRequest request = validRequest();
        request.setConfirmPassword("different123");

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Passwords do not match");

        verify(tenantService, never()).createTenant(any());
        verify(authServiceClient, never()).createTenantAdmin(any(), any(), any(), any(), any());
        verify(rabbitTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), any(EmailNotificationEvent.class));
    }

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Ali");
        request.setLastName("Bek");
        request.setCenterName("Alpha Center");
        request.setSubdomain("alpha");
        request.setEmail("owner@alpha.kz");
        request.setPhone("+77001234567");
        request.setPassword("secret123");
        request.setConfirmPassword("secret123");
        return request;
    }
}
