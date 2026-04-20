package com.ondeedu.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveBranchRequest {

    @NotBlank(message = "Branch name is required")
    @Size(max = 255, message = "Branch name must be at most 255 characters")
    private String name;

    @Size(max = 50, message = "Branch code must be at most 50 characters")
    private String code;

    @Size(max = 500, message = "Address must be at most 500 characters")
    private String address;

    @Size(max = 50, message = "Phone must be at most 50 characters")
    private String phone;

    private Boolean active;

    private Boolean isDefault;
}
