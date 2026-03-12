package com.ondeedu.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerEfficiencyResponse {

    private List<ManagerRowDto> rows;

    @Data
    @Builder
@NoArgsConstructor
@AllArgsConstructor
    public static class ManagerRowDto {
        private String managerName;
        private long leadsCount;
        private long contractsCount;
        private double conversionPct;
        private double frtP50Days;
        private double frtP75Days;
        private double frtP90Days;
    }
}
