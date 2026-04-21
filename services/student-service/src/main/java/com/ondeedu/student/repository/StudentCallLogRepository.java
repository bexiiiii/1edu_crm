package com.ondeedu.student.repository;

import com.ondeedu.student.entity.StudentCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface StudentCallLogRepository extends JpaRepository<StudentCallLog, UUID> {

    @Query("""
        SELECT cl FROM StudentCallLog cl
        WHERE cl.studentId = :studentId
          AND (:branchId IS NULL OR cl.branchId = :branchId)
        ORDER BY cl.callDate DESC, cl.callTime DESC
        """)
    Page<StudentCallLog> findByStudentId(@Param("studentId") UUID studentId,
                                         @Param("branchId") UUID branchId,
                                         Pageable pageable);

    @Query("""
        SELECT cl FROM StudentCallLog cl
        WHERE cl.studentId = :studentId
          AND cl.callDate BETWEEN :fromDate AND :toDate
          AND (:branchId IS NULL OR cl.branchId = :branchId)
        ORDER BY cl.callDate DESC, cl.callTime DESC
        """)
    Page<StudentCallLog> findByStudentIdAndDateRange(@Param("studentId") UUID studentId,
                                                      @Param("fromDate") LocalDate fromDate,
                                                      @Param("toDate") LocalDate toDate,
                                                      @Param("branchId") UUID branchId,
                                                      Pageable pageable);

    @Query("""
        SELECT COUNT(cl) FROM StudentCallLog cl
        WHERE (:branchId IS NULL OR cl.branchId = :branchId)
        """)
    long countAllByBranch(@Param("branchId") UUID branchId);

    @Query("""
        SELECT COUNT(cl) FROM StudentCallLog cl
        WHERE cl.studentId = :studentId
          AND (:branchId IS NULL OR cl.branchId = :branchId)
        """)
    long countByStudentId(@Param("studentId") UUID studentId,
                          @Param("branchId") UUID branchId);

    @Query("""
        SELECT cl FROM StudentCallLog cl
        WHERE cl.callerStaffId = :staffId
          AND (:branchId IS NULL OR cl.branchId = :branchId)
        ORDER BY cl.callDate DESC, cl.callTime DESC
        """)
    Page<StudentCallLog> findByCallerStaffId(@Param("staffId") UUID staffId,
                                              @Param("branchId") UUID branchId,
                                              Pageable pageable);
}
