package com.ondeedu.settings.dto;

import com.ondeedu.common.payment.ApiPayRecipientField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalApiPayConfigDto {

    private boolean enabled;
    private String apiBaseUrl;
    private ApiPayRecipientField recipientField;
    private String apiKey;
    private String webhookSecret;
}