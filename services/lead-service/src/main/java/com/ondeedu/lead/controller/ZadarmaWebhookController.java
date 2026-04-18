package com.ondeedu.lead.controller;

import com.ondeedu.lead.service.ZadarmaWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/zadarma")
@RequiredArgsConstructor
@Tag(name = "Zadarma Webhook", description = "Public webhook endpoint for Zadarma PBX notifications")
public class ZadarmaWebhookController {

    private final ZadarmaWebhookService zadarmaWebhookService;

    @GetMapping(value = "/webhook/{tenantId}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Validate Zadarma webhook URL")
    public ResponseEntity<String> validateWebhook(
            @PathVariable String tenantId,
            @RequestParam(name = "zd_echo", required = false) String echo
    ) {
        zadarmaWebhookService.validateTenant(tenantId);
        return ResponseEntity.ok(echo != null ? echo : "");
    }

    @PostMapping(value = "/webhook/{tenantId}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Process Zadarma PBX webhook")
    public ResponseEntity<String> processWebhook(
            @PathVariable String tenantId,
            @RequestParam MultiValueMap<String, String> formData,
            @RequestHeader(name = "Signature", required = false) String signature
    ) {
        zadarmaWebhookService.handleWebhook(tenantId, formData.toSingleValueMap(), signature);
        return ResponseEntity.ok("");
    }
}
