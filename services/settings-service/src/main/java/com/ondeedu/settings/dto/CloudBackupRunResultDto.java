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
public class CloudBackupRunResultDto {

    private String provider;
    private String fileName;
    private String remoteId;
    private String remotePath;
    private Instant completedAt;
}
