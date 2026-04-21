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
public class SaveInventoryCategoryRequest {

    @NotBlank(message = "name is required")
    @Size(max = 100)
    private String name;

    @Size(max = 1000)
    private String description;

    @Size(max = 50)
    private String icon;

    private Integer sortOrder;
}
