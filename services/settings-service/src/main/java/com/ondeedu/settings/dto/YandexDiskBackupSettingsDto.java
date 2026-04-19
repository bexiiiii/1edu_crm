package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YandexDiskBackupSettingsDto {

    private Boolean enabled;
    private Boolean configured;
    private String oauthConnectUrl;
    private String folderPath;
    private String accessTokenMasked;
    private Instant lastBackupAt;
}
