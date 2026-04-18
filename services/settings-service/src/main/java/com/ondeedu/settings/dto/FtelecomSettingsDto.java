package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FtelecomSettingsDto {

    private Boolean enabled;
    private Boolean configured;
    private String apiBaseUrl;
    private String crmTokenMasked;
    private String webhookUrl;
    private String tokenField;
}
