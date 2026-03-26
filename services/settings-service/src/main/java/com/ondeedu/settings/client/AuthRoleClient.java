package com.ondeedu.settings.client;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class AuthRoleClient {

    private final RestClient restClient;

    public AuthRoleClient(@Value("${services.auth-service.url:http://localhost:8101}") String authServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    public void syncRole(String tenantId, String roleName, String description, List<String> permissions) {
        SyncRoleRequest request = new SyncRoleRequest();
        request.setTenantId(tenantId);
        request.setDescription(description);
        request.setPermissions(permissions);

        restClient.put()
                .uri("/internal/auth/roles/{name}", roleName)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteRole(String tenantId, String roleName) {
        restClient.method(HttpMethod.DELETE)
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/auth/roles/{name}")
                        .queryParam("tenantId", tenantId)
                        .build(roleName))
                .retrieve()
                .toBodilessEntity();
    }

    @Data
    private static class SyncRoleRequest {
        private String tenantId;
        private String description;
        private List<String> permissions;
    }
}
