package com.ondeedu.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupLoadResponse {

    private List<GroupLoadRowDto> rows;

    @Data
    @Builder
@NoArgsConstructor
@AllArgsConstructor
    public static class GroupLoadRowDto {
        private UUID groupId;
        private String groupName;
        private int studentsCount;
        private int capacity;
        private double loadPct;
    }
}
