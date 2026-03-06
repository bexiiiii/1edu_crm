package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RetentionResponse {

    /** Когортный анализ */
    private List<CohortRowDto> cohorts;

    @Data
    @Builder
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
