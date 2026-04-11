package com.ondeedu.course.service;

import com.ondeedu.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CourseConstraintsService {

    private static final int DEFAULT_MAX_GROUP_SIZE = 20;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void validateEnrollmentLimitAgainstTenantSettings(Integer enrollmentLimit) {
        if (enrollmentLimit == null) {
            return;
        }

        Integer tenantMax = jdbcTemplate.query(
                """
                        SELECT max_group_size
                        FROM tenant_settings
                        ORDER BY created_at ASC
                        LIMIT 1
                        """,
                rs -> rs.next() ? (Integer) rs.getObject("max_group_size") : null
        );

        int effectiveMax = tenantMax != null ? tenantMax : DEFAULT_MAX_GROUP_SIZE;
        if (enrollmentLimit > effectiveMax) {
            throw new BusinessException(
                    "COURSE_MAX_GROUP_SIZE_EXCEEDED",
                    "Enrollment limit exceeds tenant settings max_group_size: " + effectiveMax
            );
        }
    }

    public void validateTeacherIsActive(UUID teacherId) {
        if (teacherId == null) {
            return;
        }

        String status = jdbcTemplate.query(
                "SELECT status FROM staff WHERE id = :id",
                new MapSqlParameterSource("id", teacherId),
                rs -> rs.next() ? rs.getString("status") : null
        );

        if (!"ACTIVE".equals(status)) {
            throw new BusinessException(
                    "COURSE_TEACHER_NOT_ACTIVE",
                    "Selected teacher is not active and cannot be assigned to a course"
            );
        }
    }

    public void validateStudentsAreActive(List<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status FROM students WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", studentIds)
        );

        Map<UUID, String> statusById = rows.stream().collect(
                java.util.stream.Collectors.toMap(
                        row -> (UUID) row.get("id"),
                        row -> (String) row.get("status")
                )
        );

        List<UUID> invalid = new ArrayList<>();
        for (UUID studentId : studentIds) {
            String status = statusById.get(studentId);
            if (!"ACTIVE".equals(status)) {
                invalid.add(studentId);
            }
        }

        if (!invalid.isEmpty()) {
            throw new BusinessException(
                    "COURSE_STUDENT_NOT_ACTIVE",
                    "Only ACTIVE students can be assigned to a course. Invalid IDs: " + invalid
            );
        }
    }
}