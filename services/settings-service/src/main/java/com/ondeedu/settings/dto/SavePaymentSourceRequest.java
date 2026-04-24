package com.ondeedu.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class SavePaymentSourceRequest {

    @NotBlank
    private String name;

    private Integer sortOrder = 0;

    private Boolean active = true;

    private UUID branchId;
}
