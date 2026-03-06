package com.ondeedu.payment.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.payment.dto.CreateSubscriptionRequest;
import com.ondeedu.payment.dto.SubscriptionDto;
import com.ondeedu.payment.dto.UpdateSubscriptionRequest;
import com.ondeedu.payment.entity.SubscriptionStatus;
import com.ondeedu.payment.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "Subscription management API")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SUBSCRIPTIONS_CREATE')")
    @Operation(summary = "Create a new subscription")
    public ApiResponse<SubscriptionDto> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return ApiResponse.success(subscriptionService.createSubscription(request), "Subscription created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SUBSCRIPTIONS_VIEW')")
    @Operation(summary = "Get subscription by ID")
    public ApiResponse<SubscriptionDto> getSubscription(@PathVariable UUID id) {
        return ApiResponse.success(subscriptionService.getSubscription(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SUBSCRIPTIONS_EDIT')")
    @Operation(summary = "Update subscription")
    public ApiResponse<SubscriptionDto> updateSubscription(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        return ApiResponse.success(subscriptionService.updateSubscription(id, request), "Subscription updated successfully");
    }

    @DeleteMapping("/{id}/cancel")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SUBSCRIPTIONS_EDIT')")
    @Operation(summary = "Cancel subscription")
    public ApiResponse<Void> cancelSubscription(@PathVariable UUID id) {
        subscriptionService.cancelSubscription(id);
        return ApiResponse.success("Subscription cancelled successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SUBSCRIPTIONS_VIEW')")
    @Operation(summary = "List all subscriptions with optional filters")
    public ApiResponse<PageResponse<SubscriptionDto>> listAll(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) UUID courseId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (courseId != null) {
            return ApiResponse.success(subscriptionService.listByCourse(courseId, pageable));
        }
        return ApiResponse.success(subscriptionService.listAll(status, pageable));
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('SUBSCRIPTIONS_VIEW')")
    @Operation(summary = "List subscriptions by student")
    public ApiResponse<PageResponse<SubscriptionDto>> listByStudent(
            @PathVariable UUID studentId,
            @RequestParam(required = false) SubscriptionStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(subscriptionService.listByStudent(studentId, status, pageable));
    }
}
