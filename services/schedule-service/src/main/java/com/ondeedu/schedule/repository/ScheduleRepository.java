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

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    Page<Schedule> findByStatus(ScheduleStatus status, Pageable pageable);

    Page<Schedule> findByCourseId(UUID courseId, Pageable pageable);

    Page<Schedule> findByTeacherId(UUID teacherId, Pageable pageable);

    Page<Schedule> findByRoomId(UUID roomId, Pageable pageable);

    @Query("""
        SELECT s FROM Schedule s
        WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Schedule> search(@Param("query") String query, Pageable pageable);

    long countByStatus(ScheduleStatus status);
}
