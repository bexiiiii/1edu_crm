package com.ondeedu.schedule.dto;

import jakarta.validation.constraints.Min;
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
public class CreateRoomRequest {

    @NotBlank(message = "Room name is required")
    @Size(max = 255, message = "Room name must be less than 255 characters")
    private String name;

    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    private String description;

    @Size(max = 50, message = "Color must be less than 50 characters")
    private String color;
}
