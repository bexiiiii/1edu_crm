package com.ondeedu.tenant.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.tenant.dto.subscription.ActivateSubscriptionRequest;
import com.ondeedu.tenant.dto.subscription.SubscriptionPlanDto;
import com.ondeedu.tenant.dto.subscription.SubscriptionStatusDto;
import com.ondeedu.tenant.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /** Public — доступно без авторизации (для страницы выбора тарифа) */
    @GetMapping("/api/v1/subscription/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> listPlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.listPlans()));
    }

    /** Текущий статус подписки тенанта (для фронта / модалки) */
    @GetMapping("/api/v1/subscription/status")
    public ResponseEntity<ApiResponse<SubscriptionStatusDto>> getStatus() {
        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getStatus(tenantId)));
    }

    /** SUPER_ADMIN активирует подписку после подтверждения оплаты */
    @PostMapping("/api/v1/admin/tenants/{id}/subscription/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionStatusDto>> activate(
            @PathVariable UUID id,
            @Valid @RequestBody ActivateSubscriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.activate(id, request)));
    }
}
