package com.ondeedu.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.payment.KpayRecipientField;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.common.tenant.TenantSchemaResolver;
import com.ondeedu.payment.client.KpayApiClient;
import com.ondeedu.payment.client.SettingsGrpcClient;
import com.ondeedu.payment.client.StudentGrpcClient;
import com.ondeedu.payment.dto.GenerateKpayInvoicesResponse;
import com.ondeedu.payment.dto.KpayInvoiceDto;
import com.ondeedu.payment.dto.KpayWebhookRequest;
import com.ondeedu.payment.dto.RecordPaymentRequest;
import com.ondeedu.payment.entity.KpayInvoice;
import com.ondeedu.payment.entity.KpayInvoiceStatus;
import com.ondeedu.payment.entity.PaymentMethod;
import com.ondeedu.payment.entity.PriceList;
import com.ondeedu.payment.entity.Subscription;
import com.ondeedu.payment.entity.SubscriptionStatus;
import com.ondeedu.payment.repository.KpayInvoiceRepository;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
public class KpayInvoiceService {

    private final KpayInvoiceRepository kpayInvoiceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PriceListRepository priceListRepository;
    private final StudentGrpcClient studentGrpcClient;
    private final SettingsGrpcClient settingsGrpcClient;
    private final KpayApiClient kpayApiClient;
    private final StudentPaymentService studentPaymentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public GenerateKpayInvoicesResponse generateMonthlyInvoices(String month) {
        YearMonth targetMonth = parseYearMonth(month);

        SettingsGrpcClient.KpayConfigData config = settingsGrpcClient.getKpayConfig()
                .orElseThrow(() -> new BusinessException("KPAY_CONFIG_NOT_FOUND",
                        "KPay settings are not configured for current tenant"));

        if (!config.enabled()) {
            throw new BusinessException("KPAY_DISABLED", "KPay integration is disabled");
        }

        if (!config.configured() || !StringUtils.hasText(config.apiKey()) || !StringUtils.hasText(config.apiSecret())) {
            throw new BusinessException("KPAY_KEYS_REQUIRED", "KPay API credentials are not configured");
        }

        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "Tenant context is required for KPAY invoice generation");
        }

        LocalDate firstDay = targetMonth.atDay(1);
        LocalDate lastDay = targetMonth.atEndOfMonth();

        List<Subscription> subscriptions = subscriptionRepository.findActiveInPeriod(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED), firstDay, lastDay);

        if (subscriptions.isEmpty()) {
            return GenerateKpayInvoicesResponse.builder()
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
            if (kpayInvoiceRepository.existsBySubscriptionIdAndPaymentMonth(subscription.getId(), targetMonth.toString())) {
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

            String recipient = resolveRecipient(studentOpt.get(), config.recipientField());
            if (!StringUtils.hasText(recipient)) {
                failed += createFailedInvoice(subscription, targetMonth, monthlyAmount,
                        config.recipientField(), "RECIPIENT_EMPTY", "Configured recipient field is empty");
                continue;
            }

            String merchantInvoiceId = buildMerchantInvoiceId(tenantId);
            KpayInvoice invoice = KpayInvoice.builder()
                    .studentId(subscription.getStudentId())
                    .subscriptionId(subscription.getId())
                    .paymentMonth(targetMonth.toString())
                    .recipientField(config.recipientField().name())
                    .recipientValue(recipient)
                    .amount(monthlyAmount)
                    .currency("KZT")
                    .merchantInvoiceId(merchantInvoiceId)
                    .status(KpayInvoiceStatus.CREATED)
                    .requestPayload(buildRequestPayload(merchantInvoiceId, monthlyAmount, recipient, "KZT"))
                    .build();

            invoice = kpayInvoiceRepository.save(invoice);

            try {
                KpayApiClient.CreateInvoiceResult result = kpayApiClient.createInvoice(
                        config.apiBaseUrl(),
                        config.apiKey(),
                        config.apiSecret(),
                        merchantInvoiceId,
                        monthlyAmount,
                        recipient,
                        "KZT"
                );

                invoice.setExternalInvoiceId(result.externalInvoiceId());
                invoice.setPaymentUrl(result.paymentUrl());
                invoice.setResponsePayload(result.rawResponse());
                invoice.setStatus(KpayInvoiceStatus.PENDING);
                invoice.setErrorMessage(null);
                kpayInvoiceRepository.save(invoice);
                generated++;
            } catch (RestClientException ex) {
                invoice.setStatus(KpayInvoiceStatus.FAILED);
                invoice.setErrorMessage(ex.getMessage());
                kpayInvoiceRepository.save(invoice);
                failed++;
            }
        }

        return GenerateKpayInvoicesResponse.builder()
                .month(targetMonth.toString())
                .totalSubscriptions(subscriptions.size())
                .generated(generated)
                .skipped(skipped)
                .failed(failed)
                .build();
    }

    @Transactional(readOnly = true)
    public List<KpayInvoiceDto> listInvoices(String month, KpayInvoiceStatus status) {
        Specification<KpayInvoice> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(month)) {
                predicates.add(cb.equal(root.get("paymentMonth"), month));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return kpayInvoiceRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public KpayInvoiceDto getInvoice(UUID id) {
        KpayInvoice invoice = kpayInvoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KpayInvoice", "id", id));
        return toDto(invoice);
    }

    @Transactional
    public void handleWebhookPayload(String payload) {
        KpayWebhookRequest request = parseWebhook(payload);
        if (!StringUtils.hasText(request.getInvoiceId())) {
            throw new BusinessException("KPAY_INVOICE_ID_REQUIRED", "invoice_id is required");
        }

        boolean contextInjected = false;
        if (!StringUtils.hasText(TenantContext.getTenantId())) {
            String tenantId = extractTenantIdFromMerchantInvoiceId(request.getInvoiceId());
            if (!StringUtils.hasText(tenantId)) {
                throw new BusinessException("KPAY_TENANT_ID_NOT_FOUND",
                        "Unable to resolve tenant from merchant invoice id");
            }
            String schemaName = TenantSchemaResolver.schemaNameForTenantId(tenantId);
            if (!StringUtils.hasText(schemaName)) {
                throw new BusinessException("KPAY_INVALID_TENANT", "Invalid tenant in merchant invoice id");
            }
            TenantContext.setTenantId(tenantId);
            TenantContext.setSchemaName(schemaName);
            contextInjected = true;
        }

        try {
            KpayInvoice invoice = kpayInvoiceRepository.findByMerchantInvoiceId(request.getInvoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("KpayInvoice", "merchantInvoiceId", request.getInvoiceId()));

            invoice.setWebhookPayload(payload);
            invoice.setExternalInvoiceId(firstNonBlank(request.getExternalInvoiceId(), invoice.getExternalInvoiceId()));
            invoice.setExternalTransactionId(firstNonBlank(request.getTransactionId(), invoice.getExternalTransactionId()));
            invoice.setExternalPaymentMethod(firstNonBlank(request.getPaymentMethod(), invoice.getExternalPaymentMethod()));

            KpayInvoiceStatus targetStatus = mapWebhookStatus(request.getStatus());
            invoice.setStatus(targetStatus);
            if (targetStatus == KpayInvoiceStatus.PAID) {
                Instant paidAt = parsePaidAt(request.getPaidAt());
                invoice.setPaidAt(paidAt);

                if (invoice.getStudentPaymentId() == null) {
                    RecordPaymentRequest recordPaymentRequest = RecordPaymentRequest.builder()
                            .studentId(invoice.getStudentId())
                            .subscriptionId(invoice.getSubscriptionId())
                            .amount(request.getAmount() != null ? request.getAmount() : invoice.getAmount())
                            .paymentMonth(invoice.getPaymentMonth())
                            .method(PaymentMethod.OTHER)
                            .paidAt(paidAt.atZone(ZoneId.systemDefault()).toLocalDate())
                            .notes(buildPaymentNote(request))
                            .build();

                    var payment = studentPaymentService.recordPayment(recordPaymentRequest);
                    invoice.setStudentPaymentId(payment.getId());
                }
                invoice.setErrorMessage(null);
            }

            kpayInvoiceRepository.save(invoice);
        } finally {
            if (contextInjected) {
                TenantContext.clear();
            }
        }
    }

    private KpayWebhookRequest parseWebhook(String payload) {
        try {
            return objectMapper.readValue(payload, KpayWebhookRequest.class);
        } catch (Exception e) {
            throw new BusinessException("KPAY_INVALID_WEBHOOK_PAYLOAD", "Invalid KPAY webhook payload");
        }
    }

    private int createFailedInvoice(Subscription subscription,
                                    YearMonth targetMonth,
                                    BigDecimal amount,
                                    KpayRecipientField field,
                                    String code,
                                    String errorMessage) {
        KpayInvoice failed = KpayInvoice.builder()
                .studentId(subscription.getStudentId())
                .subscriptionId(subscription.getId())
                .paymentMonth(targetMonth.toString())
                .recipientField(field.name())
                .recipientValue("N/A")
                .amount(amount)
                .currency("KZT")
                .merchantInvoiceId(buildMerchantInvoiceId(TenantContext.getTenantId()))
                .status(KpayInvoiceStatus.FAILED)
                .errorMessage(code + ": " + errorMessage)
                .build();
        kpayInvoiceRepository.save(failed);
        return 1;
    }

    private String resolveRecipient(StudentGrpcClient.StudentContactData student,
                                    KpayRecipientField field) {
        if (field == null) {
            field = KpayRecipientField.PARENT_PHONE;
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
        payload.put("invoice_id", merchantInvoiceId);
        payload.put("amount", amount);
        payload.put("recipient", recipient);
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

    private KpayInvoiceStatus mapWebhookStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return KpayInvoiceStatus.FAILED;
        }

        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "paid", "success", "completed" -> KpayInvoiceStatus.PAID;
            case "pending", "processing" -> KpayInvoiceStatus.PENDING;
            case "cancelled", "canceled" -> KpayInvoiceStatus.CANCELLED;
            case "expired" -> KpayInvoiceStatus.EXPIRED;
            default -> KpayInvoiceStatus.FAILED;
        };
    }

    private Instant parsePaidAt(String paidAt) {
        if (!StringUtils.hasText(paidAt)) {
            return Instant.now();
        }

        try {
            return Instant.parse(paidAt);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private String buildPaymentNote(KpayWebhookRequest request) {
        String transactionId = firstNonBlank(request.getTransactionId(), "-");
        return "Paid via KPAY, transactionId=" + transactionId;
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return second;
    }

    private KpayInvoiceDto toDto(KpayInvoice invoice) {
        return KpayInvoiceDto.builder()
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
