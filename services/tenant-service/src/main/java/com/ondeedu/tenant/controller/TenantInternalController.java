package com.ondeedu.tenant.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.tenant.dto.TenantDto;
import com.ondeedu.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/tenants")
@RequiredArgsConstructor
public class TenantInternalController {

    private final TenantService tenantService;

    @GetMapping("/subdomain/{subdomain}")
    public ResponseEntity<ApiResponse<TenantDto>> getBySubdomain(@PathVariable String subdomain) {
        TenantDto dto = tenantService.getBySubdomain(subdomain);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }
}
