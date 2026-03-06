package com.ondeedu.settings.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.settings.dto.PaymentSourceDto;
import com.ondeedu.settings.dto.SavePaymentSourceRequest;
import com.ondeedu.settings.service.PaymentSourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/payment-sources")
@RequiredArgsConstructor
@Tag(name = "Payment Sources", description = "Payment source management")
public class PaymentSourceController {

    private final PaymentSourceService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','RECEPTIONIST')")
    @Operation(summary = "Get all payment sources ordered by sort order")
    public ApiResponse<List<PaymentSourceDto>> getAll() {
        return ApiResponse.success(service.getAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Create a new payment source")
    public ApiResponse<PaymentSourceDto> create(@Valid @RequestBody SavePaymentSourceRequest request) {
        return ApiResponse.success(service.create(request), "Payment source created successfully");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Update a payment source")
    public ApiResponse<PaymentSourceDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody SavePaymentSourceRequest request) {
        return ApiResponse.success(service.update(id, request), "Payment source updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    @Operation(summary = "Delete a payment source")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success("Payment source deleted successfully");
    }
}
