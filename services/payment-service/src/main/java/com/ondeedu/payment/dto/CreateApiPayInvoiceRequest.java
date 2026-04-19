package com.ondeedu.payment.dto;

import com.ondeedu.common.payment.ApiPayRecipientField;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiPayInvoiceRequest {

    @NotNull(message = "studentId is required")
    private UUID studentId;

    private UUID subscriptionId;

    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "month must be in format YYYY-MM")
    private String month;

    private ApiPayRecipientField recipientField;

    @DecimalMin(value = "0.01", message = "amount must be greater than 0")
    private BigDecimal amount;

    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be 3-letter ISO code")
    private String currency;

    private String description;
}
