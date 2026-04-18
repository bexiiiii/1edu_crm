package com.ondeedu.payment.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.payment.service.ApiPayInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/apipay")
@RequiredArgsConstructor
@Tag(name = "ApiPay Webhook", description = "Internal webhook endpoint for ApiPay payment callbacks")
public class ApiPayWebhookController {

    private final ApiPayInvoiceService apiPayInvoiceService;

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Process ApiPay payment webhook")
    public ApiResponse<Void> processWebhook(
            @RequestBody String payload,
            @RequestHeader(name = "X-Webhook-Signature", required = false) String signature) {
        apiPayInvoiceService.handleWebhookPayload(payload, signature);
        return ApiResponse.success("Webhook processed");
    }
}
