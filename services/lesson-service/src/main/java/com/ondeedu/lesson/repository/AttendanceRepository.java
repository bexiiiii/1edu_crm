package com.ondeedu.lesson.repository;

import com.ondeedu.lesson.entity.Attendance;
import com.ondeedu.lesson.entity.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    List<Attendance> findByLessonId(UUID lessonId);

    Page<Attendance> findByLessonId(UUID lessonId, Pageable pageable);

    Page<Attendance> findByStudentId(UUID studentId, Pageable pageable);

    Optional<Attendance> findByLessonIdAndStudentId(UUID lessonId, UUID studentId);

    boolean existsByLessonIdAndStudentId(UUID lessonId, UUID studentId);

    long countByLessonIdAndStatus(UUID lessonId, AttendanceStatus status);
}
