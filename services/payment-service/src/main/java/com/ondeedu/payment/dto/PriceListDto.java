package com.ondeedu.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PriceListDto {

    private UUID id;

    private String name;

    private UUID courseId;

    private BigDecimal price;

    private Integer lessonsCount;

    private Integer validityDays;

    private boolean isActive;

    private String description;

    private Instant createdAt;
}
