package com.ondeedu.payment.dto;

import com.ondeedu.payment.entity.PaymentMethod;
import com.ondeedu.payment.entity.PaymentAmountChangeReasonCode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class StudentPaymentDto {
    private UUID id;
    private UUID studentId;
    private UUID subscriptionId;
    private BigDecimal amount;
    private LocalDate paidAt;
    private String paymentMonth;
    private PaymentMethod method;
    private PaymentAmountChangeReasonCode amountChangeReasonCode;
    private String amountChangeReasonOther;
    private String notes;
    private Instant createdAt;
}
