package com.ondeedu.payment.dto;

import com.ondeedu.payment.entity.KpayInvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpayInvoiceDto {

    private UUID id;
    private UUID studentId;
    private UUID subscriptionId;
    private String paymentMonth;
    private String recipientField;
    private String recipientValue;
    private BigDecimal amount;
    private String currency;
    private String merchantInvoiceId;
    private String externalInvoiceId;
    private String paymentUrl;
    private KpayInvoiceStatus status;
    private String externalPaymentMethod;
    private String externalTransactionId;
    private Instant paidAt;
    private UUID studentPaymentId;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
