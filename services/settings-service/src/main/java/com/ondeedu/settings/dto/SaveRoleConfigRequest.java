package com.ondeedu.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SaveRoleConfigRequest {

    @NotBlank
    private String name;

    private String description;

    private List<String> permissions;
}
