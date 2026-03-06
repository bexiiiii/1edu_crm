package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GroupLoadResponse {

    private List<GroupLoadRowDto> rows;

    @Data
    @Builder
    public static class GroupLoadRowDto {
        private UUID groupId;
        private String groupName;
        private int studentsCount;
        private int capacity;
        private double loadPct;
    }
}
