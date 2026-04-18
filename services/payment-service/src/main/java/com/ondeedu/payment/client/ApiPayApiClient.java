package com.ondeedu.payment.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiPayApiClient {

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;

    public record CreateInvoiceResult(String externalInvoiceId, String paymentUrl, String rawResponse) {
    }

    public CreateInvoiceResult createInvoice(String apiBaseUrl,
                                             String apiKey,
                                             String merchantInvoiceId,
                                             BigDecimal amount,
                                             String recipient,
                                             String description) {
        RestTemplate restTemplate = restTemplateBuilder
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(20))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_order_id", merchantInvoiceId);
        payload.put("amount", amount);
        payload.put("phone_number", recipient);
        payload.put("description", description);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        String endpoint = trimEndSlash(apiBaseUrl) + "/invoices";
        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(endpoint, request, Object.class);
            Map<String, Object> body = toBodyMap(response.getBody());

            String externalInvoiceId = pickFirstString(body,
                    "id", "invoice_id", "invoiceId", "external_invoice_id", "externalInvoiceId");
            String paymentUrl = pickFirstString(body,
                    "payment_url", "paymentUrl", "url", "link");

            return new CreateInvoiceResult(externalInvoiceId, paymentUrl, toJson(body));
        } catch (RestClientException ex) {
            log.warn("ApiPay API call failed for invoice {}: {}", merchantInvoiceId, ex.getMessage());
            throw ex;
        }
    }

    private String trimEndSlash(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private String pickFirstString(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
            if (value instanceof Number n) {
                return String.valueOf(n);
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> toBodyMap(Object body) {
        if (!(body instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        Map<String, Object> mapped = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            mapped.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return mapped;
    }
}
