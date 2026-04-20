package com.ondeedu.lesson.repository;

import com.ondeedu.lesson.entity.Attendance;
import com.ondeedu.lesson.entity.AttendanceStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {

    List<Attendance> findByLessonId(UUID lessonId);

    Page<Attendance> findByLessonId(UUID lessonId, Pageable pageable);

    Page<Attendance> findByStudentId(UUID studentId, Pageable pageable);

    @Query("""
            SELECT a
            FROM Attendance a
            JOIN Lesson l ON l.id = a.lessonId
            WHERE a.studentId = :studentId
              AND l.teacherId = :teacherId
            """)
    Page<Attendance> findByStudentIdAndTeacherId(@Param("studentId") UUID studentId,
                                                 @Param("teacherId") UUID teacherId,
                                                 Pageable pageable);

    Optional<Attendance> findByLessonIdAndStudentId(UUID lessonId, UUID studentId);

    boolean existsByLessonIdAndStudentId(UUID lessonId, UUID studentId);

    long countByLessonIdAndStatus(UUID lessonId, AttendanceStatus status);

    @Query(value = """
            SELECT attendance_window_days
            FROM tenant_settings
            ORDER BY created_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Integer findAttendanceWindowDays();

    @Query(value = """
            SELECT auto_mark_attendance
            FROM tenant_settings
            ORDER BY created_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Boolean findAutoMarkAttendance();

    @Query(value = """
            SELECT COUNT(*)
            FROM students s
            WHERE s.id = :studentId
              AND s.status = 'ACTIVE'
            """, nativeQuery = true)
    long countActiveStudentById(@Param("studentId") UUID studentId);

    @Query(value = """
            SELECT COUNT(*)
            FROM attendances a
            JOIN lessons l ON l.id = a.lesson_id
            WHERE a.student_id = :studentId
              AND a.status = 'ABSENT'
              AND l.lesson_date BETWEEN :weekStart AND :weekEnd
            """, nativeQuery = true)
    long countAbsentByStudentAndLessonDateBetween(
            @Param("studentId") UUID studentId,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd
    );

    @Query(value = """
            SELECT sg.student_id
            FROM student_groups sg
            JOIN students s ON s.id = sg.student_id
            WHERE sg.group_id = :groupId
              AND sg.status = 'ACTIVE'
              AND s.status = 'ACTIVE'
              AND (sg.enrolled_at IS NULL OR CAST(sg.enrolled_at AS DATE) <= :lessonDate)
              AND (sg.completed_at IS NULL OR CAST(sg.completed_at AS DATE) >= :lessonDate)
            """, nativeQuery = true)
    List<UUID> findActiveStudentIdsByGroupAndDate(
            @Param("groupId") UUID groupId,
            @Param("lessonDate") LocalDate lessonDate
    );

    @Query(value = """
            SELECT s.course_id
            FROM schedules s
            WHERE s.id = :groupId
            """, nativeQuery = true)
    UUID findCourseIdByGroupId(@Param("groupId") UUID groupId);

    @Query(value = """
            SELECT cs.student_id
            FROM course_students cs
            JOIN students s ON s.id = cs.student_id
            WHERE cs.course_id = :courseId
              AND s.status = 'ACTIVE'
              AND (cs.created_at IS NULL OR CAST(cs.created_at AS DATE) <= :lessonDate)
            """, nativeQuery = true)
    List<UUID> findActiveStudentIdsByCourseAndDate(
            @Param("courseId") UUID courseId,
            @Param("lessonDate") LocalDate lessonDate
    );
}
