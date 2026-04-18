package com.ondeedu.lead.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.lead.service.AisarWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/aisar")
@RequiredArgsConstructor
@Tag(name = "AISAR Webhook", description = "Public webhook endpoint for AISAR messenger events")
public class AisarWebhookController {

    private final AisarWebhookService aisarWebhookService;

    @PostMapping(value = "/webhook/{tenantId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Process AISAR webhook")
    public ApiResponse<Void> processWebhook(
            @PathVariable String tenantId,
            @RequestBody String payload,
            @RequestHeader(name = "X-AISAR-Signature", required = false) String signature) {
        aisarWebhookService.handleWebhook(tenantId, payload, signature);
        return ApiResponse.success("Webhook processed");
    }
}
