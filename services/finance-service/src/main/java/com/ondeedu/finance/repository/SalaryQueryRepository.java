package com.ondeedu.finance.repository;

import com.ondeedu.common.payroll.SalaryType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SalaryQueryRepository {

    private final EntityManager entityManager;

    public List<SalaryComputationRow> listSalaryRows(LocalDate monthStart, LocalDate monthEnd) {
        return execute(monthStart, monthEnd, null);
    }

    public Optional<SalaryComputationRow> findSalaryRow(UUID staffId, LocalDate monthStart, LocalDate monthEnd) {
        return execute(monthStart, monthEnd, staffId).stream().findFirst();
    }

    @SuppressWarnings("unchecked")
    private List<SalaryComputationRow> execute(LocalDate monthStart, LocalDate monthEnd, UUID staffId) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    s.id,
                    s.first_name,
                    s.last_name,
                    s.role,
                    s.status,
                    COALESCE(s.salary, 0) AS fixed_salary,
                    COALESCE(s.salary_type, 'FIXED') AS salary_type,
                    COALESCE(s.salary_percentage, 0) AS salary_percentage,
                    s.hire_date,
                    COALESCE(SUM(CASE WHEN sg.id IS NOT NULL THEN COALESCE(c.base_price, 0) ELSE 0 END), 0) AS percentage_base_amount,
                    COUNT(sg.id) AS active_student_count
                FROM staff s
                LEFT JOIN schedules sch
                  ON sch.teacher_id = s.id
                 AND sch.start_date <= :monthEnd
                 AND (sch.end_date IS NULL OR sch.end_date >= :monthStart)
                LEFT JOIN student_groups sg
                  ON sg.group_id = sch.id
                 AND (sg.enrolled_at IS NULL OR sg.enrolled_at < :monthEndExclusive)
                 AND (sg.completed_at IS NULL OR sg.completed_at >= :monthStartTs)
                LEFT JOIN courses c
                  ON c.id = sch.course_id
                """);

        if (staffId != null) {
            sql.append(" WHERE s.id = :staffId ");
        }

        sql.append("""
                GROUP BY
                    s.id,
                    s.first_name,
                    s.last_name,
                    s.role,
                    s.status,
                    s.salary,
                    s.salary_type,
                    s.salary_percentage,
                    s.hire_date
                ORDER BY s.first_name, s.last_name
                """);

        var query = entityManager.createNativeQuery(sql.toString())
                .setParameter("monthStart", monthStart)
                .setParameter("monthEnd", monthEnd)
                .setParameter("monthStartTs", Timestamp.valueOf(monthStart.atStartOfDay()))
                .setParameter("monthEndExclusive", Timestamp.valueOf(monthEnd.plusDays(1).atStartOfDay()));

        if (staffId != null) {
            query.setParameter("staffId", staffId);
        }

        List<Object[]> rows = query.getResultList();
        return rows.stream().map(this::mapRow).toList();
    }

    private SalaryComputationRow mapRow(Object[] row) {
        return new SalaryComputationRow(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                toBigDecimal(row[5]),
                SalaryType.valueOf((String) row[6]),
                toBigDecimal(row[7]),
                row[8] != null ? ((java.sql.Date) row[8]).toLocalDate() : null,
                toBigDecimal(row[9]),
                row[10] != null ? ((Number) row[10]).longValue() : 0L
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(value.toString());
    }
}
