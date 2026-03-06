package com.ondeedu.student.repository;

import com.ondeedu.student.entity.Student;
import com.ondeedu.student.entity.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {

    Optional<Student> findByPhone(String phone);

    Optional<Student> findByEmail(String email);

    Page<Student> findByStatus(StudentStatus status, Pageable pageable);

    @Query("""
        SELECT s FROM Student s
        WHERE LOWER(s.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR s.phone LIKE CONCAT('%', :query, '%')
           OR LOWER(s.email) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Student> search(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT s FROM Student s
        JOIN StudentGroup sg ON sg.studentId = s.id
        WHERE sg.groupId = :groupId AND sg.status = 'ACTIVE'
        """)
    Page<Student> findByGroupId(@Param("groupId") UUID groupId, Pageable pageable);

    long countByStatus(StudentStatus status);

    @Query("SELECT COUNT(s) FROM Student s WHERE s.createdAt >= :startDate")
    long countNewStudentsSince(@Param("startDate") java.time.Instant startDate);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}