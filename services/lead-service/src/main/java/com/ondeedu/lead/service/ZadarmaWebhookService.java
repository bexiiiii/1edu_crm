package com.ondeedu.lead.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.common.tenant.TenantSchemaResolver;
import com.ondeedu.lead.client.SettingsGrpcClient;
import com.ondeedu.lead.dto.CreateLeadRequest;
import com.ondeedu.lead.entity.Lead;
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
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZadarmaWebhookService {

    private final SettingsGrpcClient settingsGrpcClient;
    private final LeadRepository leadRepository;
    private final LeadService leadService;

    public void validateTenant(String tenantId) {
        normalizeTenantId(tenantId);
    }

    public void handleWebhook(String tenantId, Map<String, String> formData, String signature) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        String schemaName = TenantSchemaResolver.schemaNameForTenantId(normalizedTenantId);

        TenantContext.setTenantId(normalizedTenantId);
        TenantContext.setSchemaName(schemaName);

        try {
            SettingsGrpcClient.ZadarmaConfigData config = settingsGrpcClient.getZadarmaConfig()
                    .orElseThrow(() -> new BusinessException("ZADARMA_CONFIG_NOT_FOUND",
                            "Zadarma settings are not configured for current tenant"));

            if (!config.enabled()) {
                throw new BusinessException("ZADARMA_DISABLED",
                        "Zadarma integration is disabled", HttpStatus.CONFLICT);
            }

            verifySignature(formData, signature, config.userSecret());

            String event = firstNonBlank(formData.get("event"), "UNKNOWN");
            String phone = normalizePhone(formData.get("caller_id"));

            if (!shouldCreateLead(event, phone)) {
                log.info("Zadarma webhook accepted without lead creation [tenant={}, event={}, phone={}]",
                        normalizedTenantId, event, phone);
                return;
            }

            if (isDuplicateLead(phone)) {
                log.info("Zadarma webhook skipped duplicate lead [tenant={}, event={}, phone={}]",
                        normalizedTenantId, event, phone);
                return;
            }

            CreateLeadRequest request = CreateLeadRequest.builder()
                    .firstName(phone)
                    .lastName("Zadarma")
                    .phone(phone)
                    .source("ZADARMA")
                    .notes(buildNotes(formData))
                    .build();

            leadService.createLead(request);
            log.info("Created lead from Zadarma webhook [tenant={}, event={}, phone={}]",
                    normalizedTenantId, event, phone);
        } finally {
            TenantContext.clear();
        }
    }

    private void verifySignature(Map<String, String> formData, String providedSignature, String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("ZADARMA_USER_SECRET_REQUIRED",
                    "Zadarma user secret is not configured");
        }
        if (!StringUtils.hasText(providedSignature)) {
            throw new BusinessException("ZADARMA_SIGNATURE_MISSING",
                    "Signature header is required", HttpStatus.FORBIDDEN);
        }

        String dataToSign = buildSignaturePayload(formData);
        String expectedSignature = hmacSha1Base64(dataToSign, secret);

        boolean valid = MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.trim().getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            throw new BusinessException("ZADARMA_SIGNATURE_INVALID",
                    "Invalid Zadarma signature", HttpStatus.FORBIDDEN);
        }
    }

    private String buildSignaturePayload(Map<String, String> formData) {
        String event = firstNonBlank(formData.get("event"), "UNKNOWN");
        return switch (event) {
            case "NOTIFY_INTERNAL", "NOTIFY_END", "NOTIFY_START", "NOTIFY_IVR" ->
                    firstNonBlank(formData.get("caller_id"))
                            + firstNonBlank(formData.get("called_did"))
                            + firstNonBlank(formData.get("call_start"));
            case "NOTIFY_ANSWER" ->
                    firstNonBlank(formData.get("caller_id"))
                            + firstNonBlank(formData.get("destination"))
                            + firstNonBlank(formData.get("call_start"));
            case "NOTIFY_OUT_START", "NOTIFY_OUT_END" ->
                    firstNonBlank(formData.get("internal"))
                            + firstNonBlank(formData.get("destination"))
                            + firstNonBlank(formData.get("call_start"));
            case "NOTIFY_RECORD" ->
                    firstNonBlank(formData.get("pbx_call_id"))
                            + firstNonBlank(formData.get("call_id_with_rec"));
            default -> throw new BusinessException("ZADARMA_UNSUPPORTED_EVENT",
                    "Unsupported Zadarma event for signature verification: " + event);
        };
    }

    private String hmacSha1Base64(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("ZADARMA_SIGNATURE_VERIFY_FAILED",
                    "Unable to verify Zadarma signature");
        }
    }

    private boolean shouldCreateLead(String event, String phone) {
        return StringUtils.hasText(phone)
                && ("NOTIFY_INTERNAL".equalsIgnoreCase(event) || "NOTIFY_END".equalsIgnoreCase(event));
    }

    private boolean isDuplicateLead(String phone) {
        Optional<Lead> existing = leadRepository.findLatestByNormalizedPhone(phone);
        return existing.isPresent();
    }

    private String buildNotes(Map<String, String> formData) {
        StringBuilder notes = new StringBuilder("Создано автоматически из Zadarma webhook.");

        appendNote(notes, "event", formData.get("event"));
        appendNote(notes, "pbx_call_id", formData.get("pbx_call_id"));
        appendNote(notes, "called_did", formData.get("called_did"));
        appendNote(notes, "internal", formData.get("internal"));
        appendNote(notes, "duration", formData.get("duration"));
        appendNote(notes, "disposition", formData.get("disposition"));
        appendNote(notes, "call_id_with_rec", formData.get("call_id_with_rec"));

        return notes.toString();
    }

    private void appendNote(StringBuilder notes, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        notes.append(System.lineSeparator()).append(label).append(": ").append(value.trim());
    }

    private String normalizeTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("ZADARMA_TENANT_REQUIRED", "Tenant identifier is required");
        }

        String normalized = tenantId.trim();
        String schemaName = TenantSchemaResolver.schemaNameForTenantId(normalized);
        if (!StringUtils.hasText(schemaName)) {
            throw new BusinessException("ZADARMA_INVALID_TENANT",
                    "Invalid tenant identifier", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizePhone(String rawPhone) {
        if (!StringUtils.hasText(rawPhone)) {
            return null;
        }

        String digits = rawPhone.replaceAll("[^0-9]", "");
        if (digits.length() < 6) {
            return null;
        }
        return "+" + digits;
    }

    private String firstNonBlank(String value) {
        return firstNonBlank(value, "");
    }

    private String firstNonBlank(String value, String fallback) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        return fallback;
    }
}
