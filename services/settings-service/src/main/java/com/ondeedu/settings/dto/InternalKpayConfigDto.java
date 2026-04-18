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
public class InternalKpayConfigDto {

    private boolean enabled;
    private String merchantId;
    private String apiBaseUrl;
    private KpayRecipientField recipientField;
    private String apiKey;
    private String apiSecret;
}
