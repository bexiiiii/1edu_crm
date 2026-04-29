package com.ondeedu.tenant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Fire-and-forget уведомления в Telegram-бот SUPER_ADMIN.
 * Не блокирует основной поток — ошибки логируются и игнорируются.
 */
@Slf4j
@Service
public class TelegramNotificationService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String notifyUrl;

    public TelegramNotificationService(
            @Value("${telegram.bot.url:http://telegram-bot:8140}") String botUrl,
            ObjectMapper objectMapper
    ) {
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
        this.notifyUrl = botUrl.endsWith("/")
            ? botUrl + "internal/telegram/notify"
            : botUrl + "/internal/telegram/notify";
    }

    @Async
    public void notifyNewTenant(String tenantId, String tenantName, String plan) {
        send(Map.of(
            "type", "NEW_TENANT",
            "tenantId", tenantId,
            "tenantName", tenantName,
            "plan", plan != null ? plan : "BASIC"
        ));
    }

    @Async
    public void notifyPlanChanged(String tenantId, String tenantName, String oldPlan, String newPlan) {
        send(Map.of(
            "type", "PLAN_CHANGED",
            "tenantId", tenantId,
            "tenantName", tenantName != null ? tenantName : "",
            "plan", newPlan != null ? newPlan : "",
            "oldPlan", oldPlan != null ? oldPlan : ""
        ));
    }

    @Async
    public void notifyTrialExpired(String tenantId, String tenantName) {
        send(Map.of(
            "type", "TRIAL_EXPIRED",
            "tenantId", tenantId,
            "tenantName", tenantName
        ));
    }

    @Async
    public void notifyCustom(String message) {
        send(Map.of("type", "CUSTOM", "message", message));
    }

    private void send(Map<String, ?> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(notifyUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                log.warn("Telegram notification failed with status {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Telegram notification failed (non-critical): {}", e.getMessage());
        }
    }
}
