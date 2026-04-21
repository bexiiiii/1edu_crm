package com.ondeedu.student.entity;

import com.ondeedu.common.dto.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "student_call_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentCallLog extends BaseEntity {

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "caller_staff_id")
    private UUID callerStaffId;

    @Column(name = "call_date", nullable = false)
    private LocalDate callDate;

    @Column(name = "call_time", nullable = false)
    private LocalTime callTime;

    @Column(name = "call_result", length = 50)
    private String callResult;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "follow_up_required")
    @Builder.Default
    private Boolean followUpRequired = false;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "update_reason", columnDefinition = "TEXT")
    private String updateReason;
}
