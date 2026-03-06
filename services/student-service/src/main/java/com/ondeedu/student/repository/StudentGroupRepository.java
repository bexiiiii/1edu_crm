package com.ondeedu.student.repository;

import com.ondeedu.student.entity.EnrollmentStatus;
import com.ondeedu.student.entity.StudentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentGroupRepository extends JpaRepository<StudentGroup, UUID> {

    List<StudentGroup> findByStudentId(UUID studentId);

    List<StudentGroup> findByGroupId(UUID groupId);

    Optional<StudentGroup> findByStudentIdAndGroupId(UUID studentId, UUID groupId);

    List<StudentGroup> findByStudentIdAndStatus(UUID studentId, EnrollmentStatus status);

    long countByGroupIdAndStatus(UUID groupId, EnrollmentStatus status);

    boolean existsByStudentIdAndGroupIdAndStatus(UUID studentId, UUID groupId, EnrollmentStatus status);
}