package com.ondeedu.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class SaveRoleConfigRequest {

    @NotBlank
    @Pattern(
            regexp = "^[A-Z][A-Z0-9_]{1,99}$",
            message = "Role name must use uppercase letters, digits, and underscores only"
    )
    private String name;

    private String description;

    private List<String> permissions;
}
