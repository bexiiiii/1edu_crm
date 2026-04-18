package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AisarSettingsDto {

    private Boolean enabled;
    private Boolean configured;
    private String apiBaseUrl;
    private String apiKeyMasked;
    private String webhookSecretMasked;
    private String webhookUrl;
    private String signatureHeader;
    private String signatureAlgorithm;
}
