package com.ondeedu.schedule.dto;

import com.ondeedu.schedule.entity.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDto {

    private UUID id;
    private String name;
    private Integer capacity;
    private String description;
    private String color;
    private RoomStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
