package com.ondeedu.tenant.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.tenant.client.AuthServiceClient;
import com.ondeedu.tenant.dto.CreateTenantRequest;
import com.ondeedu.tenant.dto.RegisterRequest;
import com.ondeedu.tenant.dto.RegisterResponse;
import com.ondeedu.tenant.dto.TenantDto;
import com.ondeedu.tenant.entity.TenantPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final TenantService tenantService;
    private final AuthServiceClient authServiceClient;

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

        return RegisterResponse.builder()
                .tenantId(tenantId)
                .subdomain(request.getSubdomain())
                .adminUsername(request.getEmail())
                .build();
    }
}
