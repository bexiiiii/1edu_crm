package com.ondeedu.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Абонемент, требующий продления.
 *
 * <p>Категории:
 * <ul>
 *   <li>EXPIRING_BY_DATE — {@code end_date} в ближайшие 7 дней</li>
 *   <li>EXPIRING_BY_REMAINING — {@code lessons_left} <= 2</li>
 *   <li>OVERDUE — {@code end_date} < сегодня или {@code lessons_left} = 0</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiredSubscriptionDto {

    private UUID        subscriptionId;
    private UUID        studentId;
    private String      studentName;
    private String      groupName;
    private int         lessonsLeft;
    private BigDecimal  amount;
    private LocalDate   endDate;
    private String      category;   // EXPIRING_BY_DATE | EXPIRING_BY_REMAINING | OVERDUE
}
