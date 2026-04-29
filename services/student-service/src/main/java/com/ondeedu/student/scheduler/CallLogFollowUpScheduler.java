package com.ondeedu.student.scheduler;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.event.AssignmentNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ежедневно проверяет звонки с followUpDate=сегодня и рассылает IN-APP уведомления
 * всем менеджерам/администраторам тенанта о необходимости перезвонить студенту.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallLogFollowUpScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final RabbitTemplate rabbitTemplate;

    // Каждый день в 08:00 по UTC
    @Scheduled(cron = "${call-log.follow-up.cron:0 0 8 * * *}")
    public void checkFollowUps() {
        log.info("CallLogFollowUpScheduler: checking follow-ups for {}", LocalDate.now());

        List<Map<String, Object>> tenants = jdbcTemplate.queryForList(
            "SELECT id, schema_name FROM system.tenants WHERE schema_name IS NOT NULL AND schema_name != 'pending' AND deleted_at IS NULL"
        );

        for (Map<String, Object> tenant : tenants) {
            String schema = (String) tenant.get("schema_name");
            String tenantId = tenant.get("id").toString();
            try {
                processSchema(schema, tenantId);
            } catch (Exception e) {
                log.warn("CallLogFollowUpScheduler: error in schema {}: {}", schema, e.getMessage());
            }
        }
    }

    private void processSchema(String schema, String tenantId) {
        String today = LocalDate.now().toString();

        List<Map<String, Object>> logs = jdbcTemplate.queryForList("""
            SELECT cl.id, cl.student_id, cl.branch_id, cl.notes,
                   s.first_name, s.last_name
            FROM %s.student_call_logs cl
            LEFT JOIN %s.students s ON s.id = cl.student_id
            WHERE cl.follow_up_required = true
              AND cl.follow_up_date = CAST(? AS DATE)
            """.formatted(schema, schema), today);

        if (logs.isEmpty()) return;

        log.info("CallLogFollowUpScheduler: schema={}, {} follow-ups found", schema, logs.size());

        for (Map<String, Object> callLog : logs) {
            UUID callLogId = UUID.fromString(callLog.get("id").toString());
            UUID studentId = UUID.fromString(callLog.get("student_id").toString());
            String branchIdStr = callLog.get("branch_id") != null ? callLog.get("branch_id").toString() : null;
            String firstName = (String) callLog.get("first_name");
            String lastName = (String) callLog.get("last_name");
            String notes = (String) callLog.get("notes");
            String studentName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
            studentName = studentName.trim();

            // Получаем менеджеров и администраторов в этом филиале
            String managersQuery = branchIdStr != null
                ? """
                    SELECT id FROM %s.staff
                    WHERE role IN ('MANAGER', 'TENANT_ADMIN', 'RECEPTIONIST')
                      AND status = 'ACTIVE'
                      AND (branch_id = CAST(? AS UUID) OR branch_id IS NULL)
                    """.formatted(schema)
                : """
                    SELECT id FROM %s.staff
                    WHERE role IN ('MANAGER', 'TENANT_ADMIN', 'RECEPTIONIST')
                      AND status = 'ACTIVE'
                    """.formatted(schema);

            List<Map<String, Object>> managers = branchIdStr != null
                ? jdbcTemplate.queryForList(managersQuery, branchIdStr)
                : jdbcTemplate.queryForList(managersQuery);

            for (Map<String, Object> manager : managers) {
                UUID staffId = UUID.fromString(manager.get("id").toString());
                String subject = "Перезвонить: " + studentName;
                String body = buildBody(studentName, notes, studentId);

                AssignmentNotificationEvent event = new AssignmentNotificationEvent(
                    "CALL_LOG_FOLLOW_UP",
                    tenantId,
                    null,
                    staffId,
                    null,
                    null,
                    "STUDENT",
                    studentId,
                    studentName,
                    subject,
                    body
                );

                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ASSIGNMENT_KEY,
                    event
                );
                log.debug("CallLogFollowUpScheduler: sent follow-up to staff={} for callLog={}", staffId, callLogId);
            }
        }
    }

    private String buildBody(String studentName, String notes, UUID studentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Требуется перезвонить студенту ").append(studentName).append(".");
        if (notes != null && !notes.isBlank()) {
            sb.append("\nКомментарий: ").append(notes);
        }
        sb.append("\nСсылка на студента: /students/").append(studentId);
        return sb.toString();
    }
}
