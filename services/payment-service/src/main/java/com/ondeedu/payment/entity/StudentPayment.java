package com.ondeedu.payment.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student_payments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentPayment extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    /** Сумма этого платежа */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Дата фактического внесения */
    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;

    /**
     * За какой месяц засчитывается платёж, формат 'YYYY-MM'.
     * Позволяет записать авансовый или просроченный платёж на нужный месяц.
     */
    @Column(name = "payment_month", nullable = false, length = 7)
    private String paymentMonth;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method = PaymentMethod.CASH;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
