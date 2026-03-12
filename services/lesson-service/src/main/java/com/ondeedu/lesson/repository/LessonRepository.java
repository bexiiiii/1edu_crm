package com.ondeedu.lesson.repository;

import com.ondeedu.lesson.entity.Lesson;
import com.ondeedu.lesson.entity.LessonStatus;
import com.ondeedu.lesson.entity.LessonType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    Page<Lesson> findByGroupId(UUID groupId, Pageable pageable);

    Page<Lesson> findByTeacherId(UUID teacherId, Pageable pageable);

    Page<Lesson> findByStatus(LessonStatus status, Pageable pageable);

    Page<Lesson> findByLessonDate(LocalDate date, Pageable pageable);

    Page<Lesson> findByLessonDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    Page<Lesson> findByGroupIdAndLessonDateBetween(UUID groupId, LocalDate from, LocalDate to, Pageable pageable);

    Page<Lesson> findByLessonType(LessonType type, Pageable pageable);

    Page<Lesson> findByLessonTypeAndLessonDateBetween(LessonType type, LocalDate from, LocalDate to, Pageable pageable);

    Page<Lesson> findByStatusAndLessonDateBetween(LessonStatus status, LocalDate from, LocalDate to, Pageable pageable);

    List<Lesson> findByLessonDateBetweenOrderByLessonDateAscStartTimeAsc(LocalDate from, LocalDate to);
}
