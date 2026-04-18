package com.ondeedu.payment.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateApiPayInvoicesRequest {

    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "month must be in format YYYY-MM")
    private String month;
}
