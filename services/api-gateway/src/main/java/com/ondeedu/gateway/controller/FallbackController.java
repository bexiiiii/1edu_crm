package com.ondeedu.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Map<String, String> SERVICE_NAMES = Map.ofEntries(
        Map.entry("student", "Student"),
        Map.entry("course", "Course"),
        Map.entry("lead", "Lead"),
        Map.entry("payment", "Payment"),
        Map.entry("schedule", "Schedule"),
        Map.entry("lesson", "Lesson"),
        Map.entry("settings", "Settings"),
        Map.entry("audit", "Audit"),
        Map.entry("staff", "Staff"),
        Map.entry("notification", "Notification"),
        Map.entry("file", "File"),
        Map.entry("auth", "Auth"),
        Map.entry("tenant", "Tenant"),
        Map.entry("finance", "Finance"),
        Map.entry("task", "Task"),
        Map.entry("analytics", "Analytics"),
        Map.entry("report", "Report")
    );

    @RequestMapping("/{service}")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(
            @PathVariable String service,
            ServerHttpRequest request) {
        String serviceName = SERVICE_NAMES.getOrDefault(service, "Upstream");
        return fallbackResponse(serviceName + " service is temporarily unavailable", service, request);
    }

    private Mono<ResponseEntity<Map<String, Object>>> fallbackResponse(
            String message,
            String service,
            ServerHttpRequest request) {
        return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "success", false,
                "errorCode", "SERVICE_UNAVAILABLE",
                "message", message,
                "service", service,
                "method", request.getMethod() != null ? request.getMethod().name() : "UNKNOWN",
                "path", request.getPath().value()
            )));
    }
}
