package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAisarSettingsRequest {

    private Boolean enabled;
    private String apiBaseUrl;
    private String apiKey;
    private String webhookSecret;
}
