package com.ondeedu.payment.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class UpdatePriceListRequest {

    private String name;

    private UUID courseId;

    private BigDecimal price;

    private Integer lessonsCount;

    private Integer validityDays;

    private Boolean isActive;

    private String description;
}
