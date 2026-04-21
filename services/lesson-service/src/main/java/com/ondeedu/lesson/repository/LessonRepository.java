package com.ondeedu.lesson.repository;

import com.ondeedu.lesson.entity.Lesson;
import com.ondeedu.lesson.entity.LessonStatus;
import com.ondeedu.lesson.entity.LessonType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    Page<Lesson> findByGroupId(UUID groupId, Pageable pageable);

    Page<Lesson> findByTeacherId(UUID teacherId, Pageable pageable);

    Page<Lesson> findByTeacherIdAndStatus(UUID teacherId, LessonStatus status, Pageable pageable);

    Page<Lesson> findByTeacherIdAndLessonDate(UUID teacherId, LocalDate date, Pageable pageable);

    Page<Lesson> findByTeacherIdAndLessonDateBetween(UUID teacherId, LocalDate from, LocalDate to, Pageable pageable);

    Page<Lesson> findByTeacherIdAndLessonType(UUID teacherId, LessonType type, Pageable pageable);

    Page<Lesson> findByTeacherIdAndLessonTypeAndLessonDateBetween(UUID teacherId,
                                                                   LessonType type,
                                                                   LocalDate from,
                                                                   LocalDate to,
                                                                   Pageable pageable);

    Page<Lesson> findByTeacherIdAndStatusAndLessonDateBetween(UUID teacherId,
                                                               LessonStatus status,
                                                               LocalDate from,
                                                               LocalDate to,
                                                               Pageable pageable);

    Page<Lesson> findByStatus(LessonStatus status, Pageable pageable);

    Page<Lesson> findByLessonDate(LocalDate date, Pageable pageable);

    Page<Lesson> findByLessonDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    Page<Lesson> findByGroupIdAndLessonDateBetween(UUID groupId, LocalDate from, LocalDate to, Pageable pageable);

    Page<Lesson> findByGroupIdAndTeacherId(UUID groupId, UUID teacherId, Pageable pageable);

    Page<Lesson> findByGroupIdAndTeacherIdAndLessonDateBetween(UUID groupId,
                                                                UUID teacherId,
                                                                LocalDate from,
                                                                LocalDate to,
                                                                Pageable pageable);

    Page<Lesson> findByLessonType(LessonType type, Pageable pageable);

    Page<Lesson> findByLessonTypeAndLessonDateBetween(LessonType type, LocalDate from, LocalDate to, Pageable pageable);

    Page<Lesson> findByStatusAndLessonDateBetween(LessonStatus status, LocalDate from, LocalDate to, Pageable pageable);

    List<Lesson> findByLessonDateBetweenOrderByLessonDateAscStartTimeAsc(LocalDate from, LocalDate to);

    List<Lesson> findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(UUID teacherId,
                                                                                       LocalDate from,
                                                                                       LocalDate to);

    // ── Branch filtering ──────────────────────────────────────────────

    @Query("SELECT l FROM Lesson l WHERE (:branchId IS NULL OR l.branchId = :branchId)")
    Page<Lesson> findAllByBranch(@Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT l FROM Lesson l WHERE l.groupId = :groupId AND (:branchId IS NULL OR l.branchId = :branchId)")
    Page<Lesson> findByGroupIdAndBranch(@Param("groupId") UUID groupId, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT l FROM Lesson l WHERE l.teacherId = :teacherId AND (:branchId IS NULL OR l.branchId = :branchId)")
    Page<Lesson> findByTeacherIdAndBranch(@Param("teacherId") UUID teacherId, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT l FROM Lesson l WHERE l.lessonDate = :lessonDate AND (:branchId IS NULL OR l.branchId = :branchId)")
    Page<Lesson> findByLessonDateAndBranch(@Param("lessonDate") LocalDate lessonDate, @Param("branchId") UUID branchId, Pageable pageable);
}
