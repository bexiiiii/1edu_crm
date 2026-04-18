package com.ondeedu.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGoogleDriveBackupSettingsRequest {

    private Boolean enabled;
    private String folderId;
    private String accessToken;
}
