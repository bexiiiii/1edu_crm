package com.ondeedu.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PaymentCompletedEvent extends BaseEvent {

    private UUID paymentId;
    private UUID studentId;
    private UUID invoiceId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;

    public PaymentCompletedEvent(String tenantId, String userId, UUID paymentId,
                                  UUID studentId, UUID invoiceId, BigDecimal amount,
                                  String currency, String paymentMethod) {
        super("PAYMENT_COMPLETED", tenantId, userId);
        this.paymentId = paymentId;
        this.studentId = studentId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
    }
}