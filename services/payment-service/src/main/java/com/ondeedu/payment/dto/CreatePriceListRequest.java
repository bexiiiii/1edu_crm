package com.ondeedu.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CreatePriceListRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private UUID courseId;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    @NotNull(message = "Lessons count is required")
    private Integer lessonsCount;

    @NotNull(message = "Validity days is required")
    private Integer validityDays;

    private Boolean isActive;

    private String description;
}
