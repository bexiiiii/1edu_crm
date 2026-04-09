package com.ondeedu.tenant.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.tenant.dto.TenantDto;
import com.ondeedu.tenant.dto.subscription.SubscriptionStatusDto;
import com.ondeedu.tenant.service.SubscriptionService;
import com.ondeedu.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/tenants")
@RequiredArgsConstructor
public class TenantInternalController {

    private final TenantService tenantService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/subdomain/{subdomain}")
    public ResponseEntity<ApiResponse<TenantDto>> getBySubdomain(@PathVariable String subdomain) {
        TenantDto dto = tenantService.getBySubdomain(subdomain);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    /** Called by API Gateway to check subscription access without JWT overhead */
    @GetMapping("/{id}/subscription-status")
    public ResponseEntity<ApiResponse<SubscriptionStatusDto>> getSubscriptionStatus(@PathVariable UUID id) {
        SubscriptionStatusDto status = subscriptionService.getStatusForGateway(id);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(status));
    }
}
