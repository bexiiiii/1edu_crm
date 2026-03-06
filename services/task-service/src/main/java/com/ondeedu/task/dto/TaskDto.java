package com.ondeedu.task.dto;

import com.ondeedu.task.entity.TaskPriority;
import com.ondeedu.task.entity.TaskStatus;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {
    private UUID id;
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private UUID assignedTo;
    private LocalDate dueDate;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
