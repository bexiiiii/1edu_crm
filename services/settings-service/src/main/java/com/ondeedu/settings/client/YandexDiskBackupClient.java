package com.ondeedu.settings.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.settings.service.TenantBackupArtifact;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class YandexDiskBackupClient {

    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://cloud-api.yandex.net")
            .build();

    public record UploadResult(String remotePath) {
    }

    public UploadResult upload(String accessToken, String folderPath, TenantBackupArtifact artifact) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException("YANDEX_DISK_ACCESS_TOKEN_REQUIRED",
                    "Yandex Disk OAuth token is required");
        }

        String normalizedFolderPath = normalizeFolderPath(folderPath);
        String remotePath = normalizedFolderPath.endsWith("/")
                ? normalizedFolderPath + artifact.fileName()
                : normalizedFolderPath + "/" + artifact.fileName();

        try {
            ensureFolderExists(accessToken, normalizedFolderPath);
            String uploadHref = requestUploadHref(accessToken, remotePath);

            RestClient.create().put()
                    .uri(uploadHref)
                    .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken.trim())
                    .contentType(MediaType.parseMediaType(artifact.contentType()))
                    .body(artifact.content())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new BusinessException("YANDEX_DISK_UPLOAD_FAILED",
                                "Yandex Disk upload failed with status " + response.getStatusCode());
                    })
                    .toBodilessEntity();

            return new UploadResult(remotePath);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Yandex Disk upload failed", e);
            throw new BusinessException("YANDEX_DISK_UPLOAD_FAILED",
                    "Failed to upload backup to Yandex Disk: " + e.getMessage());
        }
    }

    private void ensureFolderExists(String accessToken, String folderPath) {
        if (!StringUtils.hasText(folderPath) || "disk:/".equals(folderPath)) {
            return;
        }

        String[] parts = folderPath.replaceFirst("^disk:/", "").split("/");
        StringBuilder current = new StringBuilder("disk:/");
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            if (current.length() > "disk:/".length()) {
                current.append("/");
            }
            current.append(part.trim());
            createFolderIfNeeded(accessToken, current.toString());
        }
    }

    private void createFolderIfNeeded(String accessToken, String folderPath) {
        try {
            restClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/disk/resources")
                            .queryParam("path", folderPath)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken.trim())
                    .retrieve()
                    .onStatus(status -> status.value() >= 400 && status.value() != 409, (request, response) -> {
                        throw new BusinessException("YANDEX_DISK_FOLDER_CREATE_FAILED",
                                "Yandex Disk folder create failed with status " + response.getStatusCode());
                    })
                    .toBodilessEntity();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.debug("Ignoring Yandex Disk folder create error for {}: {}", folderPath, e.getMessage());
        }
    }

    private String requestUploadHref(String accessToken, String remotePath) throws Exception {
        String responseBody = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/disk/resources/upload")
                        .queryParam("path", remotePath)
                        .queryParam("overwrite", true)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "OAuth " + accessToken.trim())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new BusinessException("YANDEX_DISK_UPLOAD_URL_FAILED",
                            "Failed to obtain Yandex Disk upload URL: " + response.getStatusCode());
                })
                .body(String.class);

        JsonNode root = objectMapper.readTree(responseBody);
        String href = textOrNull(root, "href");
        if (!StringUtils.hasText(href)) {
            throw new BusinessException("YANDEX_DISK_UPLOAD_URL_FAILED",
                    "Yandex Disk did not return upload href");
        }
        return href;
    }

    private String normalizeFolderPath(String folderPath) {
        if (!StringUtils.hasText(folderPath)) {
            return "disk:/1edu-backups";
        }
        String trimmed = folderPath.trim().replaceAll("/+$", "");
        if (trimmed.startsWith("disk:/")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return "disk:" + trimmed;
        }
        return "disk:/" + trimmed;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode child = node.path(fieldName);
        return child.isMissingNode() || child.isNull() ? null : child.asText(null);
    }
}
