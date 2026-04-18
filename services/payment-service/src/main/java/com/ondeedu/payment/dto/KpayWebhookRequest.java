package com.ondeedu.payment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpayWebhookRequest {

    @JsonAlias({"invoice_id", "invoiceId", "merchant_invoice_id", "merchantInvoiceId"})
    private String invoiceId;

    @JsonAlias({"status", "payment_status", "paymentStatus"})
    private String status;

    @JsonAlias({"transaction_id", "transactionId"})
    private String transactionId;

    @JsonAlias({"payment_method", "paymentMethod"})
    private String paymentMethod;

    @JsonAlias({"external_invoice_id", "externalInvoiceId"})
    private String externalInvoiceId;

    @JsonAlias({"amount"})
    private BigDecimal amount;

    @JsonAlias({"paid_at", "paidAt"})
    private String paidAt;
}
