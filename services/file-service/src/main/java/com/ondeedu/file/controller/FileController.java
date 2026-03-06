package com.ondeedu.file.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.file.dto.FileUploadResponse;
import com.ondeedu.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "general") String folder) {
        FileUploadResponse response = fileService.uploadFile(file, folder);
        return ApiResponse.success(response, "File uploaded successfully");
    }

    @GetMapping("/presigned-url")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<String> getPresignedUrl(@RequestParam String objectName) {
        String url = fileService.getPresignedUrl(objectName);
        return ApiResponse.success(url);
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    public ApiResponse<Void> deleteFile(@RequestParam String objectName) {
        fileService.deleteFile(objectName);
        return ApiResponse.success("File deleted successfully");
    }
}
