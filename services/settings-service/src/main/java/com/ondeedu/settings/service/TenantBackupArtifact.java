package com.ondeedu.settings.service;

import java.time.Instant;

public record TenantBackupArtifact(
        String fileName,
        byte[] content,
        String contentType,
        Instant createdAt
) {
}
