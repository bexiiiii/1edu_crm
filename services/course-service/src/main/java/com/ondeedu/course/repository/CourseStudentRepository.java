package com.ondeedu.course.repository;

import com.ondeedu.course.entity.CourseStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseStudentRepository extends JpaRepository<CourseStudent, UUID> {

    List<CourseStudent> findByCourseIdAndRemovedAtIsNullOrderByEnrolledAtAsc(UUID courseId);

    List<CourseStudent> findByCourseIdInAndRemovedAtIsNullOrderByCourseIdAscEnrolledAtAsc(Collection<UUID> courseIds);

    Optional<CourseStudent> findByCourseIdAndStudentIdAndRemovedAtIsNull(UUID courseId, UUID studentId);

    List<CourseStudent> findByStudentIdOrderByEnrolledAtDesc(UUID studentId);

    @Modifying
    @Query("UPDATE CourseStudent cs SET cs.removedAt = :now WHERE cs.courseId = :courseId AND cs.studentId IN :studentIds AND cs.removedAt IS NULL")
    void softDeleteByCourseIdAndStudentIdIn(@Param("courseId") UUID courseId,
                                            @Param("studentIds") Collection<UUID> studentIds,
                                            @Param("now") Instant now);

    @Modifying
    @Query("UPDATE CourseStudent cs SET cs.removedAt = :now WHERE cs.courseId = :courseId AND cs.removedAt IS NULL")
    void softDeleteByCourseId(@Param("courseId") UUID courseId, @Param("now") Instant now);

    void deleteByCourseId(UUID courseId);
}
