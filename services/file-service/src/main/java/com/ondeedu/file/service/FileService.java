package com.ondeedu.file.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.file.dto.FileUploadResponse;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.bucket}")
    private String bucket;

    public FileUploadResponse uploadFile(MultipartFile file, String folder) {
        try {
            String originalFilename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "unknown";
            String objectName = folder + "/" + UUID.randomUUID() + "_" + originalFilename;

            // Ensure bucket exists
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }

            // Upload the file
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }

            String url = minioUrl + "/" + bucket + "/" + objectName;
            log.debug("File uploaded to MinIO: {}", url);

            return FileUploadResponse.builder()
                    .fileName(objectName)
                    .originalFileName(originalFilename)
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .url(url)
                    .bucket(bucket)
                    .uploadedAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage());
            throw new BusinessException("FILE_UPLOAD_FAILED", "Failed to upload file: " + e.getMessage());
        }
    }

    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}: {}", objectName, e.getMessage());
            throw new BusinessException("PRESIGNED_URL_FAILED", "Failed to generate presigned URL: " + e.getMessage());
        }
    }

    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            log.debug("File deleted from MinIO: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to delete file {} from MinIO: {}", objectName, e.getMessage());
            throw new BusinessException("FILE_DELETE_FAILED", "Failed to delete file: " + e.getMessage());
        }
    }
}
