package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZadarmaSettingsDto {

    private Boolean enabled;
    private Boolean configured;
    private String apiBaseUrl;
    private String userKeyMasked;
    private String userSecretMasked;
    private String webhookUrl;
    private String validationMode;
    private String signatureHeader;
    private String signatureAlgorithm;
}
