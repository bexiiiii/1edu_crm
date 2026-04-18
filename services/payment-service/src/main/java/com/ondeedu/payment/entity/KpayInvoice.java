package com.ondeedu.payment.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "kpay_invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_kpay_invoices_merchant_invoice", columnNames = {"merchant_invoice_id"}),
                @UniqueConstraint(name = "uq_kpay_invoices_sub_month", columnNames = {"subscription_id", "payment_month"})
        },
        indexes = {
                @Index(name = "idx_kpay_invoices_status", columnList = "status"),
                @Index(name = "idx_kpay_invoices_student", columnList = "student_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpayInvoice extends BaseEntity {

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String provider = "KPAY";

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "payment_month", nullable = false, length = 7)
    private String paymentMonth;

    @Column(name = "recipient_field", nullable = false, length = 40)
    private String recipientField;

    @Column(name = "recipient_value", nullable = false, length = 50)
    private String recipientValue;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "KZT";

    @Column(name = "merchant_invoice_id", nullable = false, length = 120)
    private String merchantInvoiceId;

    @Column(name = "external_invoice_id", length = 120)
    private String externalInvoiceId;

    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private KpayInvoiceStatus status = KpayInvoiceStatus.CREATED;

    @Column(name = "external_payment_method", length = 50)
    private String externalPaymentMethod;

    @Column(name = "external_transaction_id", length = 120)
    private String externalTransactionId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "student_payment_id")
    private UUID studentPaymentId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "webhook_payload", columnDefinition = "TEXT")
    private String webhookPayload;
}
