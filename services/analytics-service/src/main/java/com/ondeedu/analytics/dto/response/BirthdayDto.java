package com.ondeedu.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Студент с ближайшим днём рождения (в пределах 7 дней от сегодня).
 */
@Data
@Builder
public class BirthdayDto {

    private UUID      studentId;
    private String    fullName;
    private LocalDate birthDate;
    /** Сколько дней до дня рождения (0 = сегодня). */
    private int       daysUntil;
    /** Сколько лет исполняется. */
    private int       turnsAge;
}
