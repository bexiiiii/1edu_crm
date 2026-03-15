package com.ondeedu.course.repository;

import com.ondeedu.course.entity.CourseStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourseStudentRepository extends JpaRepository<CourseStudent, UUID> {

    List<CourseStudent> findByCourseIdOrderByCreatedAtAsc(UUID courseId);

    List<CourseStudent> findByCourseIdInOrderByCourseIdAscCreatedAtAsc(Collection<UUID> courseIds);

    void deleteByCourseId(UUID courseId);
}
