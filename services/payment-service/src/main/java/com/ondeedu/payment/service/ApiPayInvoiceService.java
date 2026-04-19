package com.ondeedu.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.payment.ApiPayRecipientField;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.common.tenant.TenantSchemaResolver;
import com.ondeedu.payment.client.ApiPayApiClient;
import com.ondeedu.payment.client.SettingsGrpcClient;
import com.ondeedu.payment.client.StudentGrpcClient;
import com.ondeedu.payment.dto.ApiPayInvoiceDto;
import com.ondeedu.payment.dto.ApiPayWebhookRequest;
import com.ondeedu.payment.dto.GenerateApiPayInvoicesResponse;
import com.ondeedu.payment.dto.RecordPaymentRequest;
import com.ondeedu.payment.entity.ApiPayInvoice;
import com.ondeedu.payment.entity.ApiPayInvoiceStatus;
import com.ondeedu.payment.entity.PaymentMethod;
import com.ondeedu.payment.entity.PriceList;
import com.ondeedu.payment.entity.Subscription;
import com.ondeedu.payment.entity.SubscriptionStatus;
import com.ondeedu.payment.repository.ApiPayInvoiceRepository;
import com.ondeedu.payment.repository.PriceListRepository;
import com.ondeedu.payment.repository.SubscriptionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPayInvoiceService {

    private static final String APIPAY_SIGNATURE_HEADER = "X-Webhook-Signature";

    private final ApiPayInvoiceRepository apiPayInvoiceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PriceListRepository priceListRepository;
    private final StudentGrpcClient studentGrpcClient;
    private final SettingsGrpcClient settingsGrpcClient;
    private final ApiPayApiClient apiPayApiClient;
    private final StudentPaymentService studentPaymentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public GenerateApiPayInvoicesResponse generateMonthlyInvoices(String month) {
        YearMonth targetMonth = parseYearMonth(month);

        SettingsGrpcClient.ApiPayConfigData config = settingsGrpcClient.getApiPayConfig()
                .orElseThrow(() -> new BusinessException("APIPAY_CONFIG_NOT_FOUND",
                        "ApiPay settings are not configured for current tenant"));

        if (!config.enabled()) {
            throw new BusinessException("APIPAY_DISABLED", "ApiPay integration is disabled");
        }

        if (!config.configured() || !StringUtils.hasText(config.apiKey())) {
            throw new BusinessException("APIPAY_API_KEY_REQUIRED", "ApiPay API key is not configured");
        }

        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "Tenant context is required for ApiPay invoice generation");
        }

        LocalDate firstDay = targetMonth.atDay(1);
        LocalDate lastDay = targetMonth.atEndOfMonth();

        List<Subscription> subscriptions = subscriptionRepository.findActiveInPeriod(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED), firstDay, lastDay);

        if (subscriptions.isEmpty()) {
            return GenerateApiPayInvoicesResponse.builder()
                    .month(targetMonth.toString())
                    .totalSubscriptions(0)
                    .generated(0)
                    .skipped(0)
                    .failed(0)
                    .build();
        }

        Set<UUID> priceListIds = subscriptions.stream()
                .map(Subscription::getPriceListId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, PriceList> priceListMap = priceListRepository.findAllById(priceListIds).stream()
                .collect(Collectors.toMap(PriceList::getId, p -> p));

        int generated = 0;
        int skipped = 0;
        int failed = 0;

        for (Subscription subscription : subscriptions) {
            if (apiPayInvoiceRepository.existsBySubscriptionIdAndPaymentMonth(subscription.getId(), targetMonth.toString())) {
                skipped++;
                continue;
            }

            BigDecimal monthlyAmount = calcMonthlyExpected(subscription, priceListMap);
            if (monthlyAmount == null || monthlyAmount.compareTo(BigDecimal.ZERO) <= 0) {
                skipped++;
                continue;
            }

            Optional<StudentGrpcClient.StudentContactData> studentOpt =
                    studentGrpcClient.getStudentContact(subscription.getStudentId());
            if (studentOpt.isEmpty()) {
                failed += createFailedInvoice(subscription, targetMonth, monthlyAmount,
                        config.recipientField(), "STUDENT_NOT_FOUND", "Student data is not available");
                continue;
            }

            String recipientRaw = resolveRecipient(studentOpt.get(), config.recipientField());
            String recipient = normalizeRecipientForApiPay(recipientRaw);
            if (!StringUtils.hasText(recipient)) {
                failed += createFailedInvoice(subscription, targetMonth, monthlyAmount,
                        config.recipientField(), "RECIPIENT_INVALID",
                        "Configured recipient phone must be in format 8XXXXXXXXXX");
                continue;
            }

            String merchantInvoiceId = buildMerchantInvoiceId(tenantId);
            ApiPayInvoice invoice = ApiPayInvoice.builder()
                    .studentId(subscription.getStudentId())
                    .subscriptionId(subscription.getId())
                    .paymentMonth(targetMonth.toString())
                    .recipientField(config.recipientField().name())
                    .recipientValue(recipient)
                    .amount(monthlyAmount)
                    .currency("KZT")
                    .merchantInvoiceId(merchantInvoiceId)
                    .status(ApiPayInvoiceStatus.CREATED)
                    .requestPayload(buildRequestPayload(merchantInvoiceId, monthlyAmount, recipient, "KZT"))
                    .build();

            invoice = apiPayInvoiceRepository.save(invoice);

            try {
                ApiPayApiClient.CreateInvoiceResult result = apiPayApiClient.createInvoice(
                        config.apiBaseUrl(),
                        config.apiKey(),
                        merchantInvoiceId,
                        monthlyAmount,
                        recipient,
                        "Оплата абонемента " + targetMonth
                );

                invoice.setExternalInvoiceId(result.externalInvoiceId());
                invoice.setPaymentUrl(result.paymentUrl());
                invoice.setResponsePayload(result.rawResponse());
                invoice.setStatus(ApiPayInvoiceStatus.PENDING);
                invoice.setErrorMessage(null);
                apiPayInvoiceRepository.save(invoice);
                generated++;
            } catch (RestClientException ex) {
                invoice.setStatus(ApiPayInvoiceStatus.FAILED);
                invoice.setErrorMessage(ex.getMessage());
                apiPayInvoiceRepository.save(invoice);
                failed++;
            }
        }

        return GenerateApiPayInvoicesResponse.builder()
                .month(targetMonth.toString())
                .totalSubscriptions(subscriptions.size())
                .generated(generated)
                .skipped(skipped)
                .failed(failed)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ApiPayInvoiceDto> listInvoices(String month, ApiPayInvoiceStatus status) {
        Specification<ApiPayInvoice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(month)) {
                predicates.add(cb.equal(root.get("paymentMonth"), month));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return apiPayInvoiceRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiPayInvoiceDto getInvoice(UUID id) {
        ApiPayInvoice invoice = apiPayInvoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApiPayInvoice", "id", id));
        return toDto(invoice);
    }

    @Transactional
    public void handleWebhookPayload(String payload, String signatureHeader) {
        ApiPayWebhookRequest request = parseWebhook(payload);
        String merchantInvoiceId = extractMerchantInvoiceId(request);

        if (!StringUtils.hasText(merchantInvoiceId)) {
            throw new BusinessException("APIPAY_INVOICE_ID_REQUIRED", "invoice id is required");
        }

        boolean contextInjected = false;
        if (!StringUtils.hasText(TenantContext.getTenantId())) {
            String tenantId = extractTenantIdFromMerchantInvoiceId(merchantInvoiceId);
            if (!StringUtils.hasText(tenantId)) {
                throw new BusinessException("APIPAY_TENANT_ID_NOT_FOUND",
                        "Unable to resolve tenant from merchant invoice id");
            }
            String schemaName = TenantSchemaResolver.schemaNameForTenantId(tenantId);
            if (!StringUtils.hasText(schemaName)) {
                throw new BusinessException("APIPAY_INVALID_TENANT", "Invalid tenant in merchant invoice id");
            }
            TenantContext.setTenantId(tenantId);
            TenantContext.setSchemaName(schemaName);
            contextInjected = true;
        }

        try {
            SettingsGrpcClient.ApiPayConfigData config = settingsGrpcClient.getApiPayConfig()
                    .orElseThrow(() -> new BusinessException("APIPAY_CONFIG_NOT_FOUND",
                            "ApiPay settings are not configured for current tenant"));

            verifyWebhookSignature(payload, signatureHeader, config.webhookSecret());

            ApiPayInvoice invoice = apiPayInvoiceRepository.findByMerchantInvoiceId(merchantInvoiceId)
                    .orElseThrow(() -> new ResourceNotFoundException("ApiPayInvoice", "merchantInvoiceId", merchantInvoiceId));

            invoice.setWebhookPayload(payload);
            if (request.getInvoice() != null) {
                invoice.setExternalInvoiceId(firstNonBlank(request.getInvoice().getKaspiInvoiceId(), invoice.getExternalInvoiceId()));
            }

            ApiPayInvoiceStatus targetStatus = mapWebhookStatus(request);
            invoice.setStatus(targetStatus);

            if (targetStatus == ApiPayInvoiceStatus.PAID) {
                Instant paidAt = parsePaidAt(request);
                invoice.setPaidAt(paidAt);

                if (invoice.getStudentPaymentId() == null) {
                    BigDecimal paidAmount = request.getAmount() != null
                            ? request.getAmount()
                            : request.getInvoice() != null && request.getInvoice().getAmount() != null
                                    ? request.getInvoice().getAmount()
                                    : invoice.getAmount();

                    RecordPaymentRequest recordPaymentRequest = RecordPaymentRequest.builder()
                            .studentId(invoice.getStudentId())
                            .subscriptionId(invoice.getSubscriptionId())
                            .amount(paidAmount)
                            .paymentMonth(invoice.getPaymentMonth())
                            .method(PaymentMethod.OTHER)
                            .paidAt(paidAt.atZone(ZoneId.systemDefault()).toLocalDate())
                            .notes(buildPaymentNote(merchantInvoiceId))
                            .build();

                    var payment = studentPaymentService.recordPayment(recordPaymentRequest);
                    invoice.setStudentPaymentId(payment.getId());
                }
                invoice.setErrorMessage(null);
            } else if (targetStatus == ApiPayInvoiceStatus.FAILED) {
                invoice.setErrorMessage(firstNonBlank(request.getReason(), "ApiPay webhook marked invoice as failed"));
            }

            apiPayInvoiceRepository.save(invoice);
        } finally {
            if (contextInjected) {
                TenantContext.clear();
            }
        }
    }

    private ApiPayWebhookRequest parseWebhook(String payload) {
        try {
            return objectMapper.readValue(payload, ApiPayWebhookRequest.class);
        } catch (Exception e) {
            throw new BusinessException("APIPAY_INVALID_WEBHOOK_PAYLOAD", "Invalid ApiPay webhook payload");
        }
    }

    private void verifyWebhookSignature(String payload, String signatureHeader, String secret) {
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("APIPAY_WEBHOOK_SECRET_REQUIRED", "ApiPay webhook secret is not configured");
        }
        if (!StringUtils.hasText(signatureHeader)) {
            throw new BusinessException("APIPAY_SIGNATURE_MISSING", APIPAY_SIGNATURE_HEADER + " is required");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(digest);

            boolean valid = MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.trim().getBytes(StandardCharsets.UTF_8)
            );

            if (!valid) {
                throw new BusinessException("APIPAY_SIGNATURE_INVALID", "Invalid ApiPay webhook signature");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("APIPAY_SIGNATURE_VERIFY_FAILED", "Unable to verify ApiPay webhook signature");
        }
    }

    private String extractMerchantInvoiceId(ApiPayWebhookRequest request) {
        if (request == null) {
            return null;
        }
        if (StringUtils.hasText(request.getInvoiceId())) {
            return request.getInvoiceId();
        }
        if (request.getInvoice() != null && StringUtils.hasText(request.getInvoice().getId())) {
            return request.getInvoice().getId();
        }
        return null;
    }

    private int createFailedInvoice(Subscription subscription,
                                    YearMonth targetMonth,
                                    BigDecimal amount,
                                    ApiPayRecipientField field,
                                    String code,
                                    String errorMessage) {
        ApiPayInvoice failed = ApiPayInvoice.builder()
                .studentId(subscription.getStudentId())
                .subscriptionId(subscription.getId())
                .paymentMonth(targetMonth.toString())
                .recipientField(field.name())
                .recipientValue("N/A")
                .amount(amount)
                .currency("KZT")
                .merchantInvoiceId(buildMerchantInvoiceId(TenantContext.getTenantId()))
                .status(ApiPayInvoiceStatus.FAILED)
                .errorMessage(code + ": " + errorMessage)
                .build();
        apiPayInvoiceRepository.save(failed);
        return 1;
    }

    private String resolveRecipient(StudentGrpcClient.StudentContactData student,
                                    ApiPayRecipientField field) {
        if (field == null) {
            field = ApiPayRecipientField.PARENT_PHONE;
        }

        return switch (field) {
            case PHONE -> normalize(student.phone());
            case STUDENT_PHONE -> normalize(student.studentPhone());
            case PARENT_PHONE -> normalize(student.parentPhone());
            case ADDITIONAL_PHONE_1 -> student.additionalPhones() != null
                    ? student.additionalPhones().stream().map(this::normalize).filter(Objects::nonNull).findFirst().orElse(null)
                    : null;
        };
    }

    private BigDecimal calcMonthlyExpected(Subscription sub, Map<UUID, PriceList> plMap) {
        int months = calcMonthsDuration(sub, plMap);
        if (months <= 0) {
            return sub.getAmount();
        }
        return sub.getAmount().divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    private int calcMonthsDuration(Subscription sub, Map<UUID, PriceList> plMap) {
        if (sub.getPriceListId() != null) {
            PriceList priceList = plMap.get(sub.getPriceListId());
            if (priceList != null) {
                return (int) Math.ceil(priceList.getValidityDays() / 30.0);
            }
        }
        if (sub.getEndDate() != null) {
            long days = ChronoUnit.DAYS.between(sub.getStartDate(), sub.getEndDate());
            return Math.max(1, (int) Math.ceil(days / 30.0));
        }
        return 1;
    }

    private YearMonth parseYearMonth(String value) {
        if (!StringUtils.hasText(value)) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(value);
        } catch (Exception e) {
            throw new BusinessException("INVALID_MONTH_FORMAT", "month must be in format YYYY-MM");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRecipientForApiPay(String phone) {
        String normalized = normalize(phone);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        String digits = normalized.replaceAll("\\D", "");
        if (!StringUtils.hasText(digits)) {
            return null;
        }

        if (digits.length() == 11 && digits.startsWith("8")) {
            return digits;
        }

        if (digits.length() == 11 && digits.startsWith("7")) {
            return "8" + digits.substring(1);
        }

        if (digits.length() == 10) {
            return "8" + digits;
        }

        return null;
    }

    private String buildMerchantInvoiceId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "Tenant context is required");
        }
        String tenantPart = tenantId.replace("-", "");
        String randomPart = UUID.randomUUID().toString().replace("-", "");
        return tenantPart + "_" + randomPart;
    }

    private String buildRequestPayload(String merchantInvoiceId, BigDecimal amount, String recipient, String currency) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_order_id", merchantInvoiceId);
        payload.put("amount", amount);
        payload.put("phone_number", recipient);
        payload.put("currency", currency);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return payload.toString();
        }
    }

    private String extractTenantIdFromMerchantInvoiceId(String merchantInvoiceId) {
        if (!StringUtils.hasText(merchantInvoiceId) || !merchantInvoiceId.contains("_")) {
            return null;
        }
        String compactTenantId = merchantInvoiceId.substring(0, merchantInvoiceId.indexOf('_'));
        if (compactTenantId.length() != 32) {
            return null;
        }

        String normalized = compactTenantId.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{32}")) {
            return null;
        }

        return normalized.substring(0, 8) + "-"
                + normalized.substring(8, 12) + "-"
                + normalized.substring(12, 16) + "-"
                + normalized.substring(16, 20) + "-"
                + normalized.substring(20, 32);
    }

    private ApiPayInvoiceStatus mapWebhookStatus(ApiPayWebhookRequest request) {
        String event = request.getEvent() != null ? request.getEvent().trim().toLowerCase(Locale.ROOT) : "";
        if ("invoice.refunded".equals(event)) {
            return ApiPayInvoiceStatus.REFUNDED;
        }

        String rawStatus = request.getInvoice() != null ? request.getInvoice().getStatus() : null;
        if (rawStatus == null || rawStatus.isBlank()) {
            return ApiPayInvoiceStatus.FAILED;
        }

        String status = rawStatus.trim().toLowerCase(Locale.ROOT);

        return switch (status) {
            case "paid", "success", "completed" -> ApiPayInvoiceStatus.PAID;
            case "pending", "processing", "cancelling" -> ApiPayInvoiceStatus.PENDING;
            case "cancelled", "canceled" -> ApiPayInvoiceStatus.CANCELLED;
            case "expired" -> ApiPayInvoiceStatus.EXPIRED;
            case "refunded", "partially_refunded" -> ApiPayInvoiceStatus.REFUNDED;
            default -> ApiPayInvoiceStatus.FAILED;
        };
    }

    private Instant parsePaidAt(ApiPayWebhookRequest request) {
        String paidAt = request.getPaidAt();
        if (!StringUtils.hasText(paidAt) && request.getInvoice() != null) {
            paidAt = request.getInvoice().getPaidAt();
        }

        if (!StringUtils.hasText(paidAt)) {
            return Instant.now();
        }

        try {
            return Instant.parse(paidAt);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private String buildPaymentNote(String merchantInvoiceId) {
        return "Paid via APIPAY, invoice=" + firstNonBlank(merchantInvoiceId, "-");
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second;
    }

    private ApiPayInvoiceDto toDto(ApiPayInvoice invoice) {
        return ApiPayInvoiceDto.builder()
                .id(invoice.getId())
                .studentId(invoice.getStudentId())
                .subscriptionId(invoice.getSubscriptionId())
                .paymentMonth(invoice.getPaymentMonth())
                .recipientField(invoice.getRecipientField())
                .recipientValue(invoice.getRecipientValue())
                .amount(invoice.getAmount())
                .currency(invoice.getCurrency())
                .merchantInvoiceId(invoice.getMerchantInvoiceId())
                .externalInvoiceId(invoice.getExternalInvoiceId())
                .paymentUrl(invoice.getPaymentUrl())
                .status(invoice.getStatus())
                .externalPaymentMethod(invoice.getExternalPaymentMethod())
                .externalTransactionId(invoice.getExternalTransactionId())
                .paidAt(invoice.getPaidAt())
                .studentPaymentId(invoice.getStudentPaymentId())
                .errorMessage(invoice.getErrorMessage())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }
}
