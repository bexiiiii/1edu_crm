package com.ondeedu.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private String fileName;
    private String originalFileName;
    private String contentType;
    private long size;
    private String url;
    private String bucket;
    private Instant uploadedAt;
}
