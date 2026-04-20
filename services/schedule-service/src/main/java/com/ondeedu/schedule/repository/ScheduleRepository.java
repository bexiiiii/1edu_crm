package com.ondeedu.schedule.repository;

import com.ondeedu.schedule.entity.Schedule;
import com.ondeedu.schedule.entity.ScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    Page<Schedule> findByStatus(ScheduleStatus status, Pageable pageable);

    Page<Schedule> findByStatusAndTeacherId(ScheduleStatus status, UUID teacherId, Pageable pageable);

    Page<Schedule> findByCourseId(UUID courseId, Pageable pageable);

    Page<Schedule> findByCourseIdAndTeacherId(UUID courseId, UUID teacherId, Pageable pageable);

    Page<Schedule> findByTeacherId(UUID teacherId, Pageable pageable);

    Page<Schedule> findByRoomId(UUID roomId, Pageable pageable);

    Page<Schedule> findByRoomIdAndTeacherId(UUID roomId, UUID teacherId, Pageable pageable);

    List<Schedule> findByRoomIdAndStatus(UUID roomId, ScheduleStatus status);

    @Query("""
        SELECT s FROM Schedule s
        WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Schedule> search(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT s FROM Schedule s
        WHERE s.teacherId = :teacherId
          AND LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Schedule> searchByTeacherId(@Param("query") String query,
                                     @Param("teacherId") UUID teacherId,
                                     Pageable pageable);

    long countByStatus(ScheduleStatus status);
}
