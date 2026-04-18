package com.ondeedu.payment.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.payment.service.KpayInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/kpay")
@RequiredArgsConstructor
@Tag(name = "KPAY Webhook", description = "Internal webhook endpoint for KPAY payment callbacks")
public class KpayWebhookController {

    private final KpayInvoiceService kpayInvoiceService;

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Process KPAY payment webhook")
    public ApiResponse<Void> processWebhook(@RequestBody String payload) {
        kpayInvoiceService.handleWebhookPayload(payload);
        return ApiResponse.success("Webhook processed");
    }
}
