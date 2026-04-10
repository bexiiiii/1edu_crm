package com.ondeedu.tenant.service;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.event.EmailNotificationEvent;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.tenant.client.AuthServiceClient;
import com.ondeedu.tenant.dto.CreateTenantRequest;
import com.ondeedu.tenant.dto.RegisterRequest;
import com.ondeedu.tenant.dto.RegisterResponse;
import com.ondeedu.tenant.dto.TenantDto;
import com.ondeedu.tenant.entity.TenantPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final TenantService tenantService;
    private final AuthServiceClient authServiceClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${tenant.registration.welcome-email.base-domain:${BASE_DOMAIN:${DOMAIN:1edu.kz}}}")
    private String baseDomain;

    @Value("${tenant.registration.welcome-email.scheme:${TENANT_PUBLIC_SCHEME:https}}")
    private String tenantPublicScheme;

    public RegisterResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("PASSWORD_MISMATCH", "Passwords do not match");
        }

        CreateTenantRequest tenantRequest = CreateTenantRequest.builder()
                .name(request.getCenterName())
                .subdomain(request.getSubdomain())
                .email(request.getEmail())
                .phone(request.getPhone())
                .contactPerson(request.getFirstName() + " " + request.getLastName())
                .plan(TenantPlan.BASIC)
                .trialEndsAt(LocalDate.now().plusDays(7))
                .build();

        TenantDto tenant = tenantService.createTenant(tenantRequest);
        UUID tenantId = tenant.getId();

        try {
            authServiceClient.createTenantAdmin(
                    request.getFirstName(),
                    request.getLastName(),
                    request.getEmail(),
                    request.getPassword(),
                    tenantId.toString()
            );
        } catch (Exception e) {
            log.error("Failed to create Keycloak user for tenant {}: {}", tenantId, e.getMessage());
            tenantService.forceDeleteTenant(tenantId);
            throw new BusinessException("USER_CREATION_FAILED",
                    "Registration failed: could not create admin account");
        }

        publishWelcomeEmail(request, tenant);

        return RegisterResponse.builder()
                .tenantId(tenantId)
                .subdomain(request.getSubdomain())
                .adminUsername(request.getEmail())
                .build();
    }

    private void publishWelcomeEmail(RegisterRequest request, TenantDto tenant) {
        if (!StringUtils.hasText(request.getEmail())) {
            return;
        }

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_EMAIL_KEY,
                    new EmailNotificationEvent(
                            "tenant.registered.welcome",
                            tenant.getId().toString(),
                            tenant.getId().toString(),
                            request.getEmail().trim(),
                            "Добро пожаловать в 1edu CRM",
                            buildWelcomeBody(request, tenant)
                    )
            );
        } catch (Exception e) {
            log.warn("Tenant {} registered but welcome email event was not published: {}", tenant.getId(), e.getMessage());
        }
    }

    private String buildWelcomeBody(RegisterRequest request, TenantDto tenant) {
        String centerUrl = buildCenterUrl(tenant.getSubdomain());
        String trialEndsAt = tenant.getTrialEndsAt() != null
                ? DATE_FORMATTER.format(tenant.getTrialEndsAt())
                : "не указана";

        return """
                Здравствуйте, %s!

                Добро пожаловать в 1edu CRM. Ваш центр "%s" успешно зарегистрирован.

                Данные для входа:
                - Логин: %s
                - Адрес центра: %s
                - Пробный период активен до: %s

                Следующий шаг: войдите в систему и завершите первичную настройку центра.

                Если у вас появятся вопросы по запуску, просто ответьте на это письмо.
                """.formatted(
                request.getFirstName() + " " + request.getLastName(),
                tenant.getName(),
                request.getEmail(),
                centerUrl,
                trialEndsAt
        );
    }

    private String buildCenterUrl(String subdomain) {
        String normalizedScheme = StringUtils.hasText(tenantPublicScheme) ? tenantPublicScheme.trim() : "https";
        String normalizedDomain = StringUtils.hasText(baseDomain) ? baseDomain.trim() : "1edu.kz";
        return normalizedScheme + "://" + subdomain + "." + normalizedDomain;
    }
}
