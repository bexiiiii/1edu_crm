package com.ondeedu.course.repository;

import com.ondeedu.course.entity.Course;
import com.ondeedu.course.entity.CourseStatus;
import com.ondeedu.course.entity.CourseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    Page<Course> findByStatus(CourseStatus status, Pageable pageable);

    Page<Course> findByType(CourseType type, Pageable pageable);

    Page<Course> findByStatusAndType(CourseStatus status, CourseType type, Pageable pageable);

    Page<Course> findByTeacherId(UUID teacherId, Pageable pageable);

    @Query("""
        SELECT c FROM Course c
        WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Course> search(@Param("query") String query, Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.status = :status AND (:branchId IS NULL OR c.branchId = :branchId)")
    Page<Course> findByStatusAndBranch(@Param("status") CourseStatus status, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.type = :type AND (:branchId IS NULL OR c.branchId = :branchId)")
    Page<Course> findByTypeAndBranch(@Param("type") CourseType type, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.status = :status AND c.type = :type AND (:branchId IS NULL OR c.branchId = :branchId)")
    Page<Course> findByStatusAndTypeAndBranch(@Param("status") CourseStatus status, @Param("type") CourseType type, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("""
        SELECT c FROM Course c
        WHERE (:branchId IS NULL OR c.branchId = :branchId)
          AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(c.description) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<Course> searchByBranch(@Param("query") String query, @Param("branchId") UUID branchId, Pageable pageable);

    long countByStatus(CourseStatus status);

    @Query("SELECT c FROM Course c WHERE (:branchId IS NULL OR c.branchId = :branchId)")
    Page<Course> findAllByBranch(@Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT c FROM Course c WHERE c.teacherId = :teacherId AND (:branchId IS NULL OR c.branchId = :branchId)")
    Page<Course> findByTeacherIdAndBranch(@Param("teacherId") UUID teacherId, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Course c WHERE (:branchId IS NULL OR c.branchId = :branchId)")
    long countAllByBranch(@Param("branchId") UUID branchId);
}
