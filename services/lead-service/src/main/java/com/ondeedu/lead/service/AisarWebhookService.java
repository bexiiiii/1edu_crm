package com.ondeedu.lead.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.common.tenant.TenantSchemaResolver;
import com.ondeedu.lead.client.SettingsGrpcClient;
import com.ondeedu.lead.dto.CreateLeadRequest;
import com.ondeedu.lead.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AisarWebhookService {

    private final SettingsGrpcClient settingsGrpcClient;
    private final LeadRepository leadRepository;
    private final LeadService leadService;
    private final ObjectMapper objectMapper;

    public void handleWebhook(String tenantId, String payload, String signatureHeader) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String schemaName = TenantSchemaResolver.schemaNameForTenantId(normalizedTenantId);
        if (!StringUtils.hasText(schemaName)) {
            throw new BusinessException("AISAR_INVALID_TENANT", "Invalid tenant identifier", HttpStatus.BAD_REQUEST);
        }

        TenantContext.setTenantId(normalizedTenantId);
        TenantContext.setSchemaName(schemaName);

        try {
            SettingsGrpcClient.AisarConfigData config = settingsGrpcClient.getAisarConfig()
                    .orElseThrow(() -> new BusinessException("AISAR_CONFIG_NOT_FOUND",
                            "AISAR settings are not configured for current tenant"));

            if (!config.enabled()) {
                throw new BusinessException("AISAR_DISABLED", "AISAR integration is disabled", HttpStatus.CONFLICT);
            }

            verifySignature(payload, signatureHeader, config.webhookSecret());

            JsonNode root = parsePayload(payload);
            LeadCandidate candidate = extractLeadCandidate(root);

            if (!candidate.hasAnyIdentity()) {
                log.info("AISAR webhook skipped: no phone/email found [tenant={}, eventType={}]", normalizedTenantId,
                        candidate.eventType());
                return;
            }

            if (leadExists(candidate)) {
                log.info("AISAR webhook skipped duplicate lead [tenant={}, phone={}, email={}]",
                        normalizedTenantId, candidate.phone(), candidate.email());
                return;
            }

            PersonName name = splitName(candidate.name(), candidate.phone(), candidate.email());

            leadService.createLead(CreateLeadRequest.builder()
                    .firstName(name.firstName())
                    .lastName(name.lastName())
                    .phone(candidate.phone())
                    .email(candidate.email())
                    .source("AISAR")
                    .notes(buildNotes(candidate))
                    .build());

            log.info("Created lead from AISAR webhook [tenant={}, phone={}, email={}, eventType={}]",
                    normalizedTenantId, candidate.phone(), candidate.email(), candidate.eventType());
        } finally {
            TenantContext.clear();
        }
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new BusinessException("AISAR_INVALID_WEBHOOK_PAYLOAD", "Invalid AISAR webhook payload");
        }
    }

    private void verifySignature(String payload, String signatureHeader, String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("AISAR_WEBHOOK_SECRET_REQUIRED",
                    "AISAR webhook secret is not configured");
        }
        if (!StringUtils.hasText(signatureHeader)) {
            throw new BusinessException("AISAR_SIGNATURE_MISSING",
                    "X-AISAR-Signature header is required", HttpStatus.FORBIDDEN);
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            String provided = signatureHeader.trim();
            if (provided.regionMatches(true, 0, "sha256=", 0, 7)) {
                provided = provided.substring(7).trim();
            }

            boolean valid;
            if (provided.matches("^[A-Fa-f0-9]+$")) {
                valid = MessageDigest.isEqual(
                        toLowerHex(digest).getBytes(StandardCharsets.UTF_8),
                        provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
                );
            } else {
                valid = MessageDigest.isEqual(
                        Base64.getEncoder().encodeToString(digest).getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8)
                );
            }

            if (!valid) {
                throw new BusinessException("AISAR_SIGNATURE_INVALID",
                        "Invalid AISAR webhook signature", HttpStatus.FORBIDDEN);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AISAR_SIGNATURE_VERIFY_FAILED",
                    "Unable to verify AISAR webhook signature");
        }
    }

    private LeadCandidate extractLeadCandidate(JsonNode root) {
        return new LeadCandidate(
                firstNonBlank(
                        textAt(root, "event"),
                        textAt(root, "type"),
                        textAt(root, "eventType"),
                        textAt(root, "data", "event"),
                        textAt(root, "meta", "event")
                ),
                firstNonBlank(
                        textAt(root, "contact", "id"),
                        textAt(root, "contactId"),
                        textAt(root, "customer", "id"),
                        textAt(root, "customerId"),
                        textAt(root, "lead", "id"),
                        textAt(root, "conversation", "contact", "id"),
                        textAt(root, "chat", "contact", "id"),
                        textAt(root, "data", "contact", "id")
                ),
                firstNonBlank(
                        textAt(root, "conversation", "id"),
                        textAt(root, "conversationId"),
                        textAt(root, "chat", "id"),
                        textAt(root, "chatId"),
                        textAt(root, "dialog", "id"),
                        textAt(root, "thread", "id"),
                        textAt(root, "data", "conversation", "id")
                ),
                firstNonBlank(
                        textAt(root, "contact", "name"),
                        textAt(root, "contact", "fullName"),
                        textAt(root, "contact", "displayName"),
                        textAt(root, "customer", "name"),
                        textAt(root, "customer", "fullName"),
                        textAt(root, "lead", "name"),
                        textAt(root, "conversation", "contact", "name"),
                        textAt(root, "chat", "contact", "name"),
                        textAt(root, "data", "contact", "name")
                ),
                normalizePhone(firstNonBlank(
                        textAt(root, "contact", "phone"),
                        textAt(root, "contact", "phoneNumber"),
                        textAt(root, "contact", "waId"),
                        textAt(root, "contact", "displayPhone"),
                        firstArrayText(root, "contact", "phones"),
                        textAt(root, "customer", "phone"),
                        firstArrayText(root, "customer", "phones"),
                        textAt(root, "lead", "phone"),
                        textAt(root, "conversation", "contact", "phone"),
                        textAt(root, "chat", "contact", "phone"),
                        textAt(root, "data", "contact", "phone")
                )),
                normalizeEmail(firstNonBlank(
                        textAt(root, "contact", "email"),
                        textAt(root, "customer", "email"),
                        textAt(root, "lead", "email"),
                        textAt(root, "conversation", "contact", "email"),
                        textAt(root, "chat", "contact", "email"),
                        textAt(root, "data", "contact", "email")
                )),
                firstNonBlank(
                        textAt(root, "channel", "type"),
                        textAt(root, "channel"),
                        textAt(root, "source"),
                        textAt(root, "platform"),
                        textAt(root, "origin"),
                        textAt(root, "conversation", "channel"),
                        textAt(root, "chat", "platform"),
                        textAt(root, "data", "channel", "type")
                ),
                firstNonBlank(
                        textAt(root, "message", "text"),
                        textAt(root, "message", "body"),
                        textAt(root, "message", "content"),
                        textAt(root, "lastMessage", "text"),
                        textAt(root, "text"),
                        textAt(root, "body"),
                        textAt(root, "content"),
                        textAt(root, "data", "message", "text")
                )
        );
    }

    private boolean leadExists(LeadCandidate candidate) {
        if (StringUtils.hasText(candidate.phone()) && leadRepository.findLatestByNormalizedPhone(candidate.phone()).isPresent()) {
            return true;
        }
        return StringUtils.hasText(candidate.email())
                && leadRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(candidate.email()).isPresent();
    }

    private PersonName splitName(String fullName, String phone, String email) {
        String normalized = normalizeWhitespace(fullName);
        if (!StringUtils.hasText(normalized)) {
            return new PersonName("Новый", "лид");
        }

        String[] parts = normalized.split(" ");
        if (parts.length == 1) {
            return new PersonName(parts[0], StringUtils.hasText(phone) ? "AISAR" : "контакт");
        }
        return new PersonName(parts[0], normalized.substring(parts[0].length()).trim());
    }

    private String buildNotes(LeadCandidate candidate) {
        StringBuilder notes = new StringBuilder("Создано автоматически из AISAR webhook.");

        appendLine(notes, "Event", candidate.eventType());
        appendLine(notes, "Channel", candidate.channel());
        appendLine(notes, "Contact ID", candidate.contactId());
        appendLine(notes, "Conversation ID", candidate.conversationId());
        appendLine(notes, "Message", truncate(candidate.messageText(), 500));

        return notes.toString();
    }

    private void appendLine(StringBuilder notes, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        notes.append(System.lineSeparator())
                .append(label)
                .append(": ")
                .append(value);
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

    private String firstArrayText(JsonNode root, String... pathToArray) {
        JsonNode current = root;
        for (String segment : pathToArray) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(segment);
        }
        if (current == null || !current.isArray() || current.isEmpty()) {
            return null;
        }

        JsonNode first = current.get(0);
        if (first == null || first.isNull()) {
            return null;
        }
        String value = first.isValueNode() ? first.asText(null) : first.path("value").asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String trimmed = phone.trim();
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isDigit(ch) || (ch == '+' && normalized.isEmpty())) {
                normalized.append(ch);
            }
        }
        return normalized.length() > 0 ? normalized.toString() : trimmed;
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("AISAR_TENANT_REQUIRED", "Tenant identifier is required");
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

    private String normalizeWhitespace(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String toLowerHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private record LeadCandidate(
            String eventType,
            String contactId,
            String conversationId,
            String name,
            String phone,
            String email,
            String channel,
            String messageText
    ) {
        private boolean hasAnyIdentity() {
            return StringUtils.hasText(phone) || StringUtils.hasText(email);
        }
    }

    private record PersonName(String firstName, String lastName) {
    }
}
