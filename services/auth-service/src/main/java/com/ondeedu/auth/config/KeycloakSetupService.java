package com.ondeedu.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ensures Keycloak has a User Attribute protocol mapper that includes
 * the "permissions" user attribute as a claim in the JWT token.
 * This allows custom role permissions to flow through to resource servers.
 */
@Slf4j
@Service
public class KeycloakSetupService {

    private static final String MAPPER_NAME = "permissions-mapper";
    private static final String CLIENT_ID = "ondeedu-app";

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakSetupService(Keycloak keycloak,
                                @Value("${keycloak.realm}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensurePermissionsMapper() {
        try {
            var clients = keycloak.realm(realm).clients().findByClientId(CLIENT_ID);
            if (clients == null || clients.isEmpty()) {
                log.warn("Keycloak client '{}' not found in realm '{}' — skipping mapper setup", CLIENT_ID, realm);
                return;
            }

            var client = clients.get(0);
            String clientUuid = client.getId();

            List<ProtocolMapperRepresentation> existing = keycloak.realm(realm)
                    .clients().get(clientUuid)
                    .getProtocolMappers().getMappersPerProtocol("openid-connect");

            boolean alreadyExists = existing != null && existing.stream()
                    .anyMatch(m -> MAPPER_NAME.equals(m.getName()));

            if (alreadyExists) {
                log.info("Keycloak permissions mapper already exists");
                return;
            }

            ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
            mapper.setName(MAPPER_NAME);
            mapper.setProtocol("openid-connect");
            mapper.setProtocolMapper("oidc-usermodel-attribute-mapper");

            Map<String, String> config = new HashMap<>();
            config.put("user.attribute", "permissions");
            config.put("claim.name", "permissions");
            config.put("jsonType.label", "String");
            config.put("multivalued", "true");
            config.put("aggregate.attrs", "false");
            config.put("id.token.claim", "true");
            config.put("access.token.claim", "true");
            config.put("userinfo.token.claim", "true");
            mapper.setConfig(config);

            try (var response = keycloak.realm(realm).clients().get(clientUuid)
                    .getProtocolMappers().createMapper(mapper)) {
                if (response.getStatus() == 201) {
                    log.info("Created Keycloak permissions protocol mapper for client '{}'", CLIENT_ID);
                } else {
                    log.warn("Failed to create permissions mapper, status: {}", response.getStatus());
                }
            }
        } catch (Exception e) {
            log.warn("Could not set up permissions mapper (Keycloak may be unavailable): {}", e.getMessage());
        }
    }
}
