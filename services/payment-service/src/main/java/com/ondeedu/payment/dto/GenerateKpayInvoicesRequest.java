package com.ondeedu.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class GenerateKpayInvoicesRequest {

    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "month must be in format YYYY-MM")
    private String month;
}
