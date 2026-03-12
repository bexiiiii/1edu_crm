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
public class GroupAttendanceResponse {

    private UUID groupId;
    private String groupName;
    private double avgAttendanceRate;
    private List<MonthlyAttendanceDto> monthly;
}
