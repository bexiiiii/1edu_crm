package com.ondeedu.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ensures the frontend Keycloak client has the required login settings and JWT mappers.
 */
@Slf4j
@Service
public class KeycloakSetupService {

    private final Keycloak keycloak;
    private final ObjectMapper objectMapper;
    private final String realm;
    private final String frontendClientId;

    @Value("${keycloak.server-url}")
    private String keycloakServerUrl;

    public KeycloakSetupService(Keycloak keycloak,
                                ObjectMapper objectMapper,
                                @Value("${keycloak.realm}") String realm,
                                @Value("${keycloak.frontend-client-id:1edu-web-app}") String frontendClientId) {
        this.keycloak = keycloak;
        this.objectMapper = objectMapper;
        this.realm = realm;
        this.frontendClientId = frontendClientId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureMappers() {
        ensureFrontendClientSettings();
        ensureUserProfileAttributes();
        ensureUserAttributeMapper("tenant_id-mapper", "tenant_id", "tenant_id", false);
        ensureUserAttributeMapper("staff-id-mapper", "staff_id", "staff_id", false);
        ensureUserAttributeMapper("branch-ids-mapper", "branch_ids", "branch_ids", true);
        ensureUserAttributeMapper("permissions-mapper", "permissions", "permissions", true);
        ensureRealmAccessRolesMapper();
        ensureUserPropertyMapper("preferred-username-mapper", "username", "preferred_username");
        ensureUserPropertyMapper("email-mapper", "email", "email");
        ensureSubjectMapper();
    }

    private void ensureFrontendClientSettings() {
        try {
            ClientRepresentation client = findClient(frontendClientId);
            if (client == null) {
                log.warn("Keycloak client '{}' not found - skipping client setup", frontendClientId);
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
                log.info("Keycloak client '{}' already has required login settings", frontendClientId);
                return;
            }

            keycloak.realm(realm).clients().get(client.getId()).update(client);
            log.info("Updated Keycloak client '{}' login settings", frontendClientId);
        } catch (Exception e) {
            log.warn("Could not set up frontend client '{}': {}", frontendClientId, e.getMessage());
        }
    }

    private void ensureUserAttributeMapper(String mapperName, String userAttribute,
                                           String claimName, boolean multivalued) {
        Map<String, String> config = new HashMap<>();
        config.put("user.attribute", userAttribute);
        config.put("claim.name", claimName);
        config.put("jsonType.label", "String");
        config.put("multivalued", String.valueOf(multivalued));
        config.put("aggregate.attrs", "false");
        config.put("id.token.claim", "true");
        config.put("access.token.claim", "true");
        config.put("userinfo.token.claim", "true");
        config.put("introspection.token.claim", "true");

        ensureProtocolMapper(mapperName, "oidc-usermodel-attribute-mapper", config);
    }

    private void ensureUserPropertyMapper(String mapperName, String userProperty, String claimName) {
        Map<String, String> config = new HashMap<>();
        config.put("user.attribute", userProperty);
        config.put("claim.name", claimName);
        config.put("jsonType.label", "String");
        config.put("id.token.claim", "true");
        config.put("access.token.claim", "true");
        config.put("userinfo.token.claim", "true");
        config.put("introspection.token.claim", "true");

        ensureProtocolMapper(mapperName, "oidc-usermodel-property-mapper", config);
    }

    private void ensureRealmAccessRolesMapper() {
        Map<String, String> config = new HashMap<>();
        config.put("claim.name", "realm_access.roles");
        config.put("jsonType.label", "String");
        config.put("multivalued", "true");
        config.put("id.token.claim", "true");
        config.put("access.token.claim", "true");
        config.put("userinfo.token.claim", "true");
        config.put("introspection.token.claim", "true");

        ensureProtocolMapper("realm-access-roles", "oidc-usermodel-realm-role-mapper", config);
    }

    private void ensureSubjectMapper() {
        Map<String, String> config = new HashMap<>();
        config.put("id.token.claim", "true");
        config.put("access.token.claim", "true");
        config.put("userinfo.token.claim", "true");
        config.put("introspection.token.claim", "true");

        ensureProtocolMapper("sub-mapper", "oidc-sub-mapper", config);
    }

    private void ensureProtocolMapper(String mapperName, String protocolMapper, Map<String, String> config) {
        try {
            ClientRepresentation client = findClient(frontendClientId);
            if (client == null) {
                log.warn("Keycloak client '{}' not found - skipping mapper '{}'", frontendClientId, mapperName);
                return;
            }

            String clientUuid = client.getId();
            List<ProtocolMapperRepresentation> existing = keycloak.realm(realm)
                    .clients().get(clientUuid)
                    .getProtocolMappers().getMappersPerProtocol("openid-connect");

            ProtocolMapperRepresentation mapper = new ProtocolMapperRepresentation();
            mapper.setName(mapperName);
            mapper.setProtocol("openid-connect");
            mapper.setProtocolMapper(protocolMapper);
            mapper.setConfig(config);

            ProtocolMapperRepresentation existingMapper = existing == null ? null : existing.stream()
                    .filter(m -> mapperName.equals(m.getName()))
                    .findFirst()
                    .orElse(null);

            if (existingMapper == null) {
                try (var response = keycloak.realm(realm).clients().get(clientUuid)
                        .getProtocolMappers().createMapper(mapper)) {
                    if (response.getStatus() == 201) {
                        log.info("Created Keycloak mapper '{}' for client '{}'", mapperName, frontendClientId);
                    } else {
                        log.warn("Failed to create mapper '{}' for client '{}', status: {}",
                                mapperName, frontendClientId, response.getStatus());
                    }
                }
                return;
            }

            boolean upToDate = Objects.equals(existingMapper.getProtocolMapper(), protocolMapper)
                    && Objects.equals(existingMapper.getConfig(), config);
            if (upToDate) {
                log.info("Keycloak mapper '{}' already configured for client '{}'", mapperName, frontendClientId);
                return;
            }

            mapper.setId(existingMapper.getId());
            keycloak.realm(realm).clients().get(clientUuid)
                    .getProtocolMappers().update(existingMapper.getId(), mapper);
            log.info("Updated Keycloak mapper '{}' for client '{}'", mapperName, frontendClientId);
        } catch (Exception e) {
            log.warn("Could not set up mapper '{}' for client '{}': {}", mapperName, frontendClientId, e.getMessage());
        }
    }

    private ClientRepresentation findClient(String clientId) {
        List<ClientRepresentation> clients = keycloak.realm(realm).clients().findByClientId(clientId);
        if (clients == null || clients.isEmpty()) {
            return null;
        }
        return clients.get(0);
    }

    @SuppressWarnings("unchecked")
    private void ensureUserProfileAttributes() {
        try {
            String endpoint = normalizeServerUrl(keycloakServerUrl)
                    + "/admin/realms/" + realm + "/users/profile";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + keycloak.tokenManager().getAccessTokenString())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .GET()
                    .build();

            HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
            if (getResponse.statusCode() < 200 || getResponse.statusCode() >= 300) {
                log.warn("Could not read Keycloak user profile config. status={}", getResponse.statusCode());
                return;
            }

            Map<String, Object> profile = objectMapper.readValue(
                    getResponse.body(),
                    new TypeReference<>() {
                    }
            );

            List<Map<String, Object>> attributes = (List<Map<String, Object>>) profile.get("attributes");
            if (attributes == null) {
                attributes = new ArrayList<>();
                profile.put("attributes", attributes);
            }

            boolean changed = false;
            changed |= ensureUserProfileAttribute(attributes, "tenant_id", false);
            changed |= ensureUserProfileAttribute(attributes, "permissions", true);
            changed |= ensureUserProfileAttribute(attributes, "permissions_source", false);
            changed |= ensureUserProfileAttribute(attributes, "staff_id", false);
            changed |= ensureUserProfileAttribute(attributes, "branch_ids", true);
            changed |= ensureUserProfileAttribute(attributes, "photoUrl", false);
            changed |= ensureUserProfileAttribute(attributes, "language", false);

            if (!changed) {
                log.info("Keycloak user profile attributes already configured");
                return;
            }

            String payload = objectMapper.writeValueAsString(profile);
            HttpRequest putRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + keycloak.tokenManager().getAccessTokenString())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());
            if (putResponse.statusCode() >= 200 && putResponse.statusCode() < 300) {
                log.info("Updated Keycloak user profile schema with custom attributes");
            } else {
                log.warn("Could not update Keycloak user profile schema. status={} body={}",
                        putResponse.statusCode(), putResponse.body());
            }
        } catch (Exception e) {
            log.warn("Could not ensure Keycloak user profile attributes: {}", e.getMessage());
        }
    }

    private boolean ensureUserProfileAttribute(List<Map<String, Object>> attributes,
                                               String attributeName,
                                               boolean multivalued) {
        Map<String, Object> existing = attributes.stream()
                .filter(attr -> attributeName.equals(attr.get("name")))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            Object currentMultivalued = existing.get("multivalued");
            if (!Objects.equals(currentMultivalued, multivalued)) {
                existing.put("multivalued", multivalued);
                return true;
            }
            return false;
        }

        Map<String, Object> attribute = new HashMap<>();
        attribute.put("name", attributeName);
        attribute.put("displayName", attributeName);
        attribute.put("validations", Map.of());
        attribute.put("annotations", Map.of());
        attribute.put("permissions", Map.of("view", List.of("admin"), "edit", List.of("admin")));
        attribute.put("multivalued", multivalued);
        attributes.add(attribute);
        return true;
    }

    private String normalizeServerUrl(String serverUrl) {
        if (serverUrl == null) {
            return "";
        }
        return serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    }
}
