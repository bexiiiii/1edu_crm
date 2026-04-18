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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleDriveBackupClient {

    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://www.googleapis.com")
            .build();

    public record UploadResult(String fileId, String fileName) {
    }

    public UploadResult upload(String accessToken, String folderId, TenantBackupArtifact artifact) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException("GOOGLE_DRIVE_ACCESS_TOKEN_REQUIRED",
                    "Google Drive access token is required");
        }

        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("name", artifact.fileName());
            if (StringUtils.hasText(folderId)) {
                metadata.put("parents", List.of(folderId.trim()));
            }

            String boundary = "ondeedu-drive-" + UUID.randomUUID();
            byte[] requestBody = buildMultipartBody(boundary, metadata, artifact);

            String responseBody = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/upload/drive/v3/files")
                            .queryParam("uploadType", "multipart")
                            .queryParam("fields", "id,name")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.trim())
                    .contentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary))
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new BusinessException("GOOGLE_DRIVE_UPLOAD_FAILED",
                                "Google Drive upload failed with status " + response.getStatusCode());
                    })
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String fileId = textOrNull(root, "id");
            String fileName = textOrNull(root, "name");

            if (!StringUtils.hasText(fileId)) {
                throw new BusinessException("GOOGLE_DRIVE_UPLOAD_FAILED",
                        "Google Drive did not return uploaded file id");
            }

            return new UploadResult(fileId, StringUtils.hasText(fileName) ? fileName : artifact.fileName());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google Drive upload failed", e);
            throw new BusinessException("GOOGLE_DRIVE_UPLOAD_FAILED",
                    "Failed to upload backup to Google Drive: " + e.getMessage());
        }
    }

    private byte[] buildMultipartBody(String boundary, Map<String, Object> metadata, TenantBackupArtifact artifact)
            throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] newline = "\r\n".getBytes(StandardCharsets.UTF_8);
        String partPrefix = "--" + boundary + "\r\n";

        out.write(partPrefix.getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(objectMapper.writeValueAsBytes(metadata));
        out.write(newline);

        out.write(partPrefix.getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + artifact.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(artifact.content());
        out.write(newline);

        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode child = node.path(fieldName);
        return child.isMissingNode() || child.isNull() ? null : child.asText(null);
    }
}
