package com.ondeedu.notification.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.notification.dto.BroadcastNotificationRequest;
import com.ondeedu.notification.dto.BroadcastNotificationResultDto;
import com.ondeedu.notification.dto.NotificationDto;
import com.ondeedu.notification.entity.NotificationStatus;
import com.ondeedu.notification.entity.NotificationType;
import com.ondeedu.notification.service.BroadcastNotificationService;
import com.ondeedu.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final Set<String> ELEVATED_AUTHORITIES = Set.of(
            "ROLE_MANAGER",
            "ROLE_RECEPTIONIST",
            "ROLE_SUPER_ADMIN"
    );

    private final NotificationService notificationService;
    private final BroadcastNotificationService broadcastNotificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<PageResponse<NotificationDto>> listLogs(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        String recipientEmail = resolveRecipientEmail(jwt, authentication, mine);
        PageResponse<NotificationDto> result = notificationService.listLogs(
                TenantContext.getTenantId(),
                recipientEmail,
                type,
                status,
                pageable
        );
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<NotificationDto> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean mine) {
        return ApiResponse.success(notificationService.getById(
                id,
                TenantContext.getTenantId(),
                resolveRecipientEmail(jwt, authentication, mine)
        ));
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<BroadcastNotificationResultDto> broadcast(
            @Valid @RequestBody BroadcastNotificationRequest request) {
        return ApiResponse.success(
                broadcastNotificationService.broadcast(TenantContext.getTenantId(), request),
                "Broadcast notification sent successfully"
        );
    }

    private String resolveRecipientEmail(Jwt jwt, Authentication authentication, boolean mine) {
        if (authentication != null && isElevated(authentication) && !mine) {
            return null;
        }

        if (jwt == null) {
            return "";
        }

        String email = jwt.getClaimAsString("email");
        if (StringUtils.hasText(email)) {
            return email;
        }

        String preferredUsername = jwt.getClaimAsString("preferred_username");
        return StringUtils.hasText(preferredUsername) ? preferredUsername : "";
    }

    private boolean isElevated(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .anyMatch(ELEVATED_AUTHORITIES::contains);
    }
}
