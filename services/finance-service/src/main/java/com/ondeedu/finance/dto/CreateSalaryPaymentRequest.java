package com.ondeedu.finance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class CreateSalaryPaymentRequest {

    @NotNull
    private UUID staffId;

    @NotBlank
    private String salaryMonth;

    @NotNull
    @Positive
    private BigDecimal amount;

    private String currency;

    private LocalDate paymentDate;

    private String notes;
}
