package com.ondeedu.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SavePaymentSourceRequest {

    @NotBlank
    private String name;

    private Integer sortOrder = 0;

    private Boolean active = true;
}
