package com.ondeedu.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Посещение (attendance) без действующего абонемента.
 *
 * <p>Критерий «неоплаченного»: студент посетил занятие, но у него нет
 * активного абонемента с {@code lessons_left > 0} на эту группу/услугу.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnpaidVisitDto {

    private UUID      studentId;
    private String    studentName;
    private UUID      lessonId;
    private String    groupName;
    private LocalDate lessonDate;
}
