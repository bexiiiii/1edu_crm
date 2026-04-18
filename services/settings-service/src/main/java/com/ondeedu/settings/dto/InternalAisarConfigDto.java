package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalAisarConfigDto {

    private boolean enabled;
    private String apiBaseUrl;
    private String apiKey;
    private String webhookSecret;
}
