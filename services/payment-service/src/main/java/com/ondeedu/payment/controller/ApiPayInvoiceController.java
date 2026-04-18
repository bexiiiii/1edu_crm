package com.ondeedu.payment.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.payment.dto.ApiPayInvoiceDto;
import com.ondeedu.payment.dto.GenerateApiPayInvoicesRequest;
import com.ondeedu.payment.dto.GenerateApiPayInvoicesResponse;
import com.ondeedu.payment.entity.ApiPayInvoiceStatus;
import com.ondeedu.payment.service.ApiPayInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/apipay")
@RequiredArgsConstructor
@Tag(name = "ApiPay Invoices", description = "ApiPay invoice generation and status tracking")
public class ApiPayInvoiceController {

    private final ApiPayInvoiceService apiPayInvoiceService;

    @PostMapping("/invoices/generate")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_CREATE')")
    @Operation(summary = "Generate ApiPay invoices for a month")
    public ApiResponse<GenerateApiPayInvoicesResponse> generateInvoices(
            @Valid @RequestBody(required = false) GenerateApiPayInvoicesRequest request) {
        String month = request != null ? request.getMonth() : null;
        return ApiResponse.success(apiPayInvoiceService.generateMonthlyInvoices(month),
                "ApiPay invoices generated");
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "List ApiPay invoices")
    public ApiResponse<List<ApiPayInvoiceDto>> listInvoices(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) ApiPayInvoiceStatus status) {
        return ApiResponse.success(apiPayInvoiceService.listInvoices(month, status));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "Get ApiPay invoice details")
    public ApiResponse<ApiPayInvoiceDto> getInvoice(@PathVariable UUID id) {
        return ApiResponse.success(apiPayInvoiceService.getInvoice(id));
    }
}
