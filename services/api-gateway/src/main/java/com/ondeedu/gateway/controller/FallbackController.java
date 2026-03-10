package com.ondeedu.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/student")
    public Mono<ResponseEntity<Map<String, Object>>> studentFallback() {
        return fallbackResponse("Student service is temporarily unavailable");
    }

    @GetMapping("/course")
    public Mono<ResponseEntity<Map<String, Object>>> courseFallback() {
        return fallbackResponse("Course service is temporarily unavailable");
    }

    @GetMapping("/lead")
    public Mono<ResponseEntity<Map<String, Object>>> leadFallback() {
        return fallbackResponse("Lead service is temporarily unavailable");
    }

    @GetMapping("/payment")
    public Mono<ResponseEntity<Map<String, Object>>> paymentFallback() {
        return fallbackResponse("Payment service is temporarily unavailable");
    }

    @GetMapping("/schedule")
    public Mono<ResponseEntity<Map<String, Object>>> scheduleFallback() {
        return fallbackResponse("Schedule service is temporarily unavailable");
    }

    @GetMapping("/staff")
    public Mono<ResponseEntity<Map<String, Object>>> staffFallback() {
        return fallbackResponse("Staff service is temporarily unavailable");
    }

    @GetMapping("/notification")
    public Mono<ResponseEntity<Map<String, Object>>> notificationFallback() {
        return fallbackResponse("Notification service is temporarily unavailable");
    }

    @GetMapping("/file")
    public Mono<ResponseEntity<Map<String, Object>>> fileFallback() {
        return fallbackResponse("File service is temporarily unavailable");
    }

    @GetMapping("/tenant")
    public Mono<ResponseEntity<Map<String, Object>>> tenantFallback() {
        return fallbackResponse("Tenant service is temporarily unavailable");
    }

    @GetMapping("/analytics")
    public Mono<ResponseEntity<Map<String, Object>>> analyticsFallback() {
        return fallbackResponse("Analytics service is temporarily unavailable");
    }

    @GetMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        return fallbackResponse("Auth service is temporarily unavailable");
    }

    private Mono<ResponseEntity<Map<String, Object>>> fallbackResponse(String message) {
        return Mono.just(ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "success", false,
                "errorCode", "SERVICE_UNAVAILABLE",
                "message", message
            )));
    }
}