package com.ondeedu.task.dto;

import com.ondeedu.task.entity.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {
    @NotBlank
    private String title;
    private String description;
    private TaskPriority priority;
    private UUID assignedTo;
    private LocalDate dueDate;
    private String notes;
}
