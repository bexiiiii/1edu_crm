package com.ondeedu.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastNotificationResultDto {
    private String scope;
    private int tenantsAffected;
    private int recipients;
}
