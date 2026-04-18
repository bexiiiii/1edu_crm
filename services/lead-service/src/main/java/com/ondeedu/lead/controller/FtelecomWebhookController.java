package com.ondeedu.lead.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.lead.service.FtelecomWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ftelecom")
@RequiredArgsConstructor
@Tag(name = "Freedom Telecom Webhook", description = "Public webhook endpoint for Freedom Telecom PBX events")
public class FtelecomWebhookController {

    private final FtelecomWebhookService ftelecomWebhookService;

    @PostMapping(value = "/webhook/{tenantId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Process Freedom Telecom webhook")
    public ApiResponse<Void> processWebhook(
            @PathVariable String tenantId,
            @RequestBody String payload) {
        ftelecomWebhookService.handleWebhook(tenantId, payload);
        return ApiResponse.success("Webhook processed");
    }
}
