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
public class ApiPayWebhookRequest {

    @JsonAlias({"event", "type"})
    private String event;

    @JsonAlias({"amount"})
    private BigDecimal amount;

    @JsonAlias({"paid_at", "paidAt", "timestamp"})
    private String paidAt;

    @JsonAlias({"invoice_id", "invoiceId"})
    private String invoiceId;

    @JsonAlias({"reason", "error_message", "errorMessage"})
    private String reason;

    private InvoicePayload invoice;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoicePayload {
        @JsonAlias({"id"})
        private String id;

        @JsonAlias({"external_order_id", "externalOrderId"})
        private String externalOrderId;

        @JsonAlias({"status"})
        private String status;

        @JsonAlias({"amount"})
        private BigDecimal amount;

        @JsonAlias({"kaspi_invoice_id", "kaspiInvoiceId"})
        private String kaspiInvoiceId;

        @JsonAlias({"is_sandbox", "sandbox"})
        private Boolean sandbox;

        @JsonAlias({"paid_at", "paidAt"})
        private String paidAt;
    }
}
