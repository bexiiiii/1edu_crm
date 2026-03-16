package com.ondeedu.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastNotificationRequest {

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    @Builder.Default
    private boolean alsoEmail = false;
}
