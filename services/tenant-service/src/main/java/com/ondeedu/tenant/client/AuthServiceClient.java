package com.ondeedu.tenant.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class AuthServiceClient {

    private final RestClient restClient;

    public AuthServiceClient(@Value("${services.auth-service.url:http://auth-service}") String authServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    public void createTenantAdmin(String firstName, String lastName, String email,
                                   String password, String tenantId) {
        Map<String, Object> body = Map.of(
                "firstName", firstName,
                "lastName", lastName,
                "username", email,
                "email", email,
                "password", password,
                "role", "TENANT_ADMIN",
                "tenantId", tenantId
        );

        restClient.post()
                .uri("/internal/auth/users")
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Created TENANT_ADMIN user in Keycloak for tenant: {}", tenantId);
    }
}
