package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class RoomLoadResponse {

    private List<RoomLoadRowDto> rows;

    /** Таймлайн на конкретный день */
    private LocalDate timelineDate;
    private List<RoomTimelineDto> timeline;

    @Data
    @Builder
    public static class RoomLoadRowDto {
        private UUID roomId;
        private String roomName;
        private int lessonsCount;
        private int totalStudents;
        private int totalCapacity;
        private double loadPct;
    }

    @Data
    @Builder
    public static class RoomTimelineDto {
        private UUID roomId;
        private String roomName;
        /** доля занятого времени за день (0..100) */
        private double occupancyPct;
    }
}
