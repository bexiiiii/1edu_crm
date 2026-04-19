package com.ondeedu.payment.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.payment.dto.CreateKpayInvoiceRequest;
import com.ondeedu.payment.dto.GenerateKpayInvoicesRequest;
import com.ondeedu.payment.dto.GenerateKpayInvoicesResponse;
import com.ondeedu.payment.dto.KpayInvoiceDto;
import com.ondeedu.payment.entity.KpayInvoiceStatus;
import com.ondeedu.payment.service.KpayInvoiceService;
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
@RequestMapping("/api/v1/payments/kpay")
@RequiredArgsConstructor
@Tag(name = "KPAY Invoices", description = "KPAY invoice generation and status tracking")
public class KpayInvoiceController {

    private final KpayInvoiceService kpayInvoiceService;

    @PostMapping("/invoices/generate")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_CREATE')")
    @Operation(summary = "Generate KPAY invoices for a month")
    public ApiResponse<GenerateKpayInvoicesResponse> generateInvoices(
            @Valid @RequestBody(required = false) GenerateKpayInvoicesRequest request) {
        String month = request != null ? request.getMonth() : null;
        return ApiResponse.success(kpayInvoiceService.generateMonthlyInvoices(month),
                "KPAY invoices generated");
    }

    @PostMapping("/invoices/single")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_CREATE')")
    @Operation(summary = "Create single KPAY invoice for one student")
    public ApiResponse<KpayInvoiceDto> createSingleInvoice(@Valid @RequestBody CreateKpayInvoiceRequest request) {
        return ApiResponse.success(kpayInvoiceService.createSingleInvoice(request),
                "KPAY invoice created");
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "List KPAY invoices")
    public ApiResponse<List<KpayInvoiceDto>> listInvoices(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) KpayInvoiceStatus status) {
        return ApiResponse.success(kpayInvoiceService.listInvoices(month, status));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "Get KPAY invoice details")
    public ApiResponse<KpayInvoiceDto> getInvoice(@PathVariable UUID id) {
        return ApiResponse.success(kpayInvoiceService.getInvoice(id));
    }
}
