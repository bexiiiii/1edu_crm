package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateYandexDiskBackupSettingsRequest {

    private Boolean enabled;
    private String folderPath;
    private String accessToken;
}
