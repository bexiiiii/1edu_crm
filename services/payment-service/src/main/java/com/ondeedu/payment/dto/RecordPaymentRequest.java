package com.ondeedu.payment.dto;

import com.ondeedu.payment.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordPaymentRequest {

    @NotNull(message = "Student ID is required")
    private UUID studentId;

    @NotNull(message = "Subscription ID is required")
    private UUID subscriptionId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    /** Дата фактической оплаты. По умолчанию сегодня. */
    private LocalDate paidAt;

    /**
     * За какой месяц засчитывается платёж. Формат: YYYY-MM.
     * По умолчанию текущий месяц.
     */
    @NotBlank(message = "Payment month is required")
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Payment month must be in format YYYY-MM")
    private String paymentMonth;

    private PaymentMethod method;

    private String notes;
}
