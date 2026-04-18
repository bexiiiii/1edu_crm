package com.ondeedu.settings.dto;

import com.ondeedu.common.payment.KpayRecipientField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpaySettingsDto {

    private Boolean enabled;
    private Boolean configured;
    private String merchantId;
    private String apiBaseUrl;
    private KpayRecipientField recipientField;
    private String apiKeyMasked;
    private String apiSecretMasked;
}
