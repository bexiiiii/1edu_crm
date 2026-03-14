package com.ondeedu.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ensures Keycloak has the required client settings and protocol mappers for JWT claims.
 * - permissions mapper for ondeedu-app client
 * - tenant_id mapper for 1edu-web-app client (frontend)
 */
@Slf4j
@Service
public class KeycloakSetupService {

    private final Keycloak keycloak;
    private final String realm;

    public KeycloakSetupService(Keycloak keycloak,
                                @Value("${keycloak.realm}") String realm) {
        this.keycloak = keycloak;
        this.realm = realm;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureMappers() {
        ensureFrontendClientSettings();
        ensureMapper("ondeedu-app", "permissions-mapper", "permissions", "permissions", true);
        ensureMapper("1edu-web-app", "tenant_id-mapper", "tenant_id", "tenant_id", false);
        ensureMapper("ondeedu-app", "tenant_id-mapper", "tenant_id", "tenant_id", false);
    }

    private void ensureFrontendClientSettings() {
        try {
            ClientRepresentation client = findClient("1edu-web-app");
            if (client == null) {
                log.warn("Keycloak client '1edu-web-app' not found - skipping client setup");
                return;
            }

            boolean changed = false;

            if (!Boolean.TRUE.equals(client.isPublicClient())) {
                client.setPublicClient(true);
                changed = true;
            }

            if (!Boolean.TRUE.equals(client.isStandardFlowEnabled())) {
                client.setStandardFlowEnabled(true);
                changed = true;
            }

            if (!Boolean.TRUE.equals(client.isDirectAccessGrantsEnabled())) {
                client.setDirectAccessGrantsEnabled(true);
                changed = true;
            }

            if (!changed) {
                log.info("Keycloak client '1edu-web-app' already has required login settings");
                return;
            }

            keycloak.realm(realm).clients().get(client.getId()).update(client);
            log.info("Updated Keycloak client '1edu-web-app' login settings");
        } catch (Exception e) {
            log.warn("Could not set up frontend client '1edu-web-app': {}", e.getMessage());
        }
    }

    private void ensureMapper(String clientId, String mapperName, String userAttribute,
                               String claimName, boolean multivalued) {
        try {
            ClientRepresentation client = findClient(clientId);
            if (client == null) {
                log.warn("Keycloak client '{}' not found — skipping mapper '{}'", clientId, mapperName);
                return;
            }

            String clientUuid = client.getId();

            List<ProtocolMapperRepresentation> existing = keycloak.realm(realm)
                    .clients().get(clientUuid)
                    .getProtocolMappers().getMappersPerProtocol("openid-connect");

            boolean alreadyExists = existing != null && existing.stream()
                    .anyMatch(m -> mapperName.equals(m.getName()));

            if (alreadyExists) {
                log.info("Keycloak mapper '{}' already exists for client '{}'", mapperName, clientId);
                return;
            }

            ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
            mapper.setName(mapperName);
            mapper.setProtocol("openid-connect");
            mapper.setProtocolMapper("oidc-usermodel-attribute-mapper");

            Map<String, String> config = new HashMap<>();
            config.put("user.attribute", userAttribute);
            config.put("claim.name", claimName);
            config.put("jsonType.label", "String");
            config.put("multivalued", String.valueOf(multivalued));
            config.put("aggregate.attrs", "false");
            config.put("id.token.claim", "true");
            config.put("access.token.claim", "true");
            config.put("userinfo.token.claim", "true");
            mapper.setConfig(config);

            try (var response = keycloak.realm(realm).clients().get(clientUuid)
                    .getProtocolMappers().createMapper(mapper)) {
                if (response.getStatus() == 201) {
                    log.info("Created Keycloak mapper '{}' for client '{}'", mapperName, clientId);
                } else {
                    log.warn("Failed to create mapper '{}' for client '{}', status: {}",
                            mapperName, clientId, response.getStatus());
                }
            }
        } catch (Exception e) {
            log.warn("Could not set up mapper '{}' for client '{}': {}", mapperName, clientId, e.getMessage());
        }
    }

    private ClientRepresentation findClient(String clientId) {
        List<ClientRepresentation> clients = keycloak.realm(realm).clients().findByClientId(clientId);
        if (clients == null || clients.isEmpty()) {
            return null;
        }
        return clients.get(0);
    }
}
