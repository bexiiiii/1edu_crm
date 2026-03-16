package com.ondeedu.schedule.repository;

import com.ondeedu.schedule.entity.ScheduleStudentEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScheduleStudentEnrollmentRepository extends JpaRepository<ScheduleStudentEnrollment, UUID> {

    List<ScheduleStudentEnrollment> findByGroupId(UUID groupId);

    List<ScheduleStudentEnrollment> findByGroupIdAndStatus(UUID groupId, String status);

    List<ScheduleStudentEnrollment> findByGroupIdAndNotesStartingWith(UUID groupId, String notesPrefix);
}
