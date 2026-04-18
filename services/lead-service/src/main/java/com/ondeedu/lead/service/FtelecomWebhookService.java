package com.ondeedu.lead.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.common.tenant.TenantSchemaResolver;
import com.ondeedu.lead.client.SettingsGrpcClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Service
@RequiredArgsConstructor
public class FtelecomWebhookService {

    private final SettingsGrpcClient settingsGrpcClient;
    private final ObjectMapper objectMapper;

    public void handleWebhook(String tenantId, String payload) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String schemaName = TenantSchemaResolver.schemaNameForTenantId(normalizedTenantId);
        if (!StringUtils.hasText(schemaName)) {
            throw new BusinessException("FTELECOM_INVALID_TENANT", "Invalid tenant identifier", HttpStatus.BAD_REQUEST);
        }

        TenantContext.setTenantId(normalizedTenantId);
        TenantContext.setSchemaName(schemaName);

        try {
            SettingsGrpcClient.FtelecomConfigData config = settingsGrpcClient.getFtelecomConfig()
                    .orElseThrow(() -> new BusinessException("FTELECOM_CONFIG_NOT_FOUND",
                            "Freedom Telecom settings are not configured for current tenant"));

            if (!config.enabled()) {
                throw new BusinessException("FTELECOM_DISABLED", "Freedom Telecom integration is disabled", HttpStatus.CONFLICT);
            }

            JsonNode root = parsePayload(payload);
            verifyCrmToken(root, config.crmToken());

            String cmd = firstNonBlank(
                    textAt(root, "cmd"),
                    textAt(root, "command")
            );
            String type = firstNonBlank(
                    textAt(root, "type"),
                    textAt(root, "event"),
                    textAt(root, "data", "type")
            );
            String eventId = firstNonBlank(
                    textAt(root, "id"),
                    textAt(root, "event_id"),
                    textAt(root, "data", "id")
            );

            log.info("Accepted Freedom Telecom webhook [tenant={}, cmd={}, type={}, id={}]",
                    normalizedTenantId, cmd, type, eventId);
        } finally {
            TenantContext.clear();
        }
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new BusinessException("FTELECOM_INVALID_WEBHOOK_PAYLOAD", "Invalid Freedom Telecom webhook payload");
        }
    }

    private void verifyCrmToken(JsonNode root, String configuredToken) {
        if (!StringUtils.hasText(configuredToken)) {
            throw new BusinessException("FTELECOM_CRM_TOKEN_REQUIRED",
                    "Freedom Telecom CRM token is not configured");
        }

        String token = firstNonBlank(
                textAt(root, "crm_token"),
                textAt(root, "crmToken"),
                textAt(root, "data", "crm_token"),
                textAt(root, "data", "crmToken")
        );

        if (!StringUtils.hasText(token)) {
            throw new BusinessException("FTELECOM_CRM_TOKEN_MISSING",
                    "crm_token is required in webhook payload", HttpStatus.FORBIDDEN);
        }

        boolean valid = MessageDigest.isEqual(
                token.trim().getBytes(StandardCharsets.UTF_8),
                configuredToken.trim().getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            throw new BusinessException("FTELECOM_CRM_TOKEN_INVALID",
                    "Invalid Freedom Telecom crm_token", HttpStatus.FORBIDDEN);
        }
    }

    private String textAt(JsonNode root, String... path) {
        JsonNode current = root;
        for (String segment : path) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(segment);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        String value = current.asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("FTELECOM_TENANT_REQUIRED", "Tenant identifier is required");
        }
        return tenantId.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
