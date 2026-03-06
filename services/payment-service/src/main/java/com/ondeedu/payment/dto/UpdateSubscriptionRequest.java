package com.ondeedu.payment.dto;

import com.ondeedu.payment.entity.SubscriptionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class UpdateSubscriptionRequest {

    private Integer lessonsLeft;

    private SubscriptionStatus status;

    private LocalDate endDate;

    private String notes;
}
