package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateZadarmaSettingsRequest {

    private Boolean enabled;
    private String apiBaseUrl;
    private String userKey;
    private String userSecret;
}
