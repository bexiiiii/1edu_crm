package com.ondeedu.settings.client;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.exception.BusinessException;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileServiceClient {

    private final RestClient restClient;

    public FileServiceClient(@Value("${services.file-service.base-url:http://localhost:8118}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String uploadLogo(MultipartFile file, String bearerToken) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new NamedByteArrayResource(file.getBytes(), file.getOriginalFilename()));
            body.add("folder", "logos");

            ApiResponse<FileUploadPayload> response = restClient.post()
                    .uri("/api/v1/files/upload")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            if (response == null || !response.isSuccess() || response.getData() == null || response.getData().getUrl() == null) {
                throw new BusinessException("LOGO_UPLOAD_FAILED", "File service did not return a logo URL");
            }

            return response.getData().getUrl();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("LOGO_UPLOAD_FAILED", "Failed to upload logo: " + e.getMessage());
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String fileName;

        private NamedByteArrayResource(byte[] byteArray, String fileName) {
            super(byteArray);
            this.fileName = fileName != null && !fileName.isBlank() ? fileName : "logo";
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }

    @Data
    private static class FileUploadPayload {
        private String url;
    }
}
