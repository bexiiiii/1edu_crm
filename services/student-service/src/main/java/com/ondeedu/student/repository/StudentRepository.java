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

    Optional<Student> findByIdAndBranchId(UUID id, UUID branchId);

    Page<Student> findByStatus(StudentStatus status, Pageable pageable);

    @Query("""
        SELECT s FROM Student s
        WHERE s.status = :status
          AND (:branchId IS NULL OR s.branchId = :branchId)
        """)
    Page<Student> findByStatusAndBranch(@Param("status") StudentStatus status,
                                        @Param("branchId") UUID branchId,
                                        Pageable pageable);

    @Query("""
        SELECT s FROM Student s
        WHERE (:branchId IS NULL OR s.branchId = :branchId)
        """)
    Page<Student> findAllByBranch(@Param("branchId") UUID branchId, Pageable pageable);

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
        WHERE (:branchId IS NULL OR s.branchId = :branchId)
          AND (
            LOWER(s.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
             OR s.phone LIKE CONCAT('%', :query, '%')
             OR LOWER(s.email) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Student> searchByBranch(@Param("branchId") UUID branchId,
                                 @Param("query") String query,
                                 Pageable pageable);

    @Query("""
        SELECT s FROM Student s
        JOIN StudentGroup sg ON sg.studentId = s.id
        WHERE sg.groupId = :groupId
          AND sg.status = 'ACTIVE'
          AND (:branchId IS NULL OR s.branchId = :branchId)
        """)
    Page<Student> findByGroupId(@Param("groupId") UUID groupId,
                                @Param("branchId") UUID branchId,
                                Pageable pageable);

    long countByStatus(StudentStatus status);

    @Query("SELECT COUNT(s) FROM Student s WHERE (:branchId IS NULL OR s.branchId = :branchId)")
    long countAllByBranch(@Param("branchId") UUID branchId);

    @Query("""
        SELECT COUNT(s)
        FROM Student s
        WHERE s.status = :status
          AND (:branchId IS NULL OR s.branchId = :branchId)
        """)
    long countByStatusAndBranch(@Param("status") StudentStatus status, @Param("branchId") UUID branchId);

    @Query("""
        SELECT COUNT(s)
        FROM Student s
        WHERE s.createdAt >= :startDate
          AND (:branchId IS NULL OR s.branchId = :branchId)
        """)
    long countNewStudentsSince(@Param("startDate") java.time.Instant startDate,
                               @Param("branchId") UUID branchId);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndBranchId(String phone, UUID branchId);

    boolean existsByEmail(String email);

    boolean existsByEmailAndBranchId(String email, UUID branchId);

    boolean existsByIdAndBranchId(UUID id, UUID branchId);
}