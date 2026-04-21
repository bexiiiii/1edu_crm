package com.ondeedu.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveInventoryUnitRequest {

    @NotBlank(message = "name is required")
    @Size(max = 50)
    private String name;

    @NotBlank(message = "abbreviation is required")
    @Size(max = 10)
    private String abbreviation;

    @NotBlank(message = "unitType is required")
    @Size(max = 20)
    private String unitType;

    @Size(max = 1000)
    private String description;
}
