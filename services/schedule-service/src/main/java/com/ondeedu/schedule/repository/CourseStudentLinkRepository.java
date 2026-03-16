package com.ondeedu.schedule.repository;

import com.ondeedu.schedule.entity.CourseStudentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CourseStudentLinkRepository extends JpaRepository<CourseStudentLink, UUID> {

    @Query("""
            SELECT cs.studentId
            FROM CourseStudentLink cs
            WHERE cs.courseId = :courseId
            ORDER BY cs.createdAt ASC
            """)
    List<UUID> findStudentIdsByCourseIdOrderByCreatedAtAsc(@Param("courseId") UUID courseId);
}
