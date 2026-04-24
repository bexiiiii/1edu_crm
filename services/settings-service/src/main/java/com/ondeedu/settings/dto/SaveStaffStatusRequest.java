package com.ondeedu.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class SaveStaffStatusRequest {

    @NotBlank
    private String name;

    private String color;
    private Integer sortOrder;
    private Boolean active;

    private UUID branchId;
}
