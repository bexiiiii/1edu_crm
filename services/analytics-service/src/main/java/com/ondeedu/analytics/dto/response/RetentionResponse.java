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
public class RetentionResponse {

    /** Когортный анализ */
    private List<CohortRowDto> cohorts;

    @Data
    @Builder
@NoArgsConstructor
@AllArgsConstructor
    public static class CohortRowDto {
        private String cohort;    // "2026 янв."
        private String cohortKey; // "2026-01"
        private int size;
        private double m0;
        private double m1;
        private double m2;
        private double m3;
        private double m4;
        private double m5;
    }
}
