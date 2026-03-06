package com.ondeedu.schedule.repository;

import com.ondeedu.schedule.entity.Room;
import com.ondeedu.schedule.entity.RoomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    Page<Room> findByStatus(RoomStatus status, Pageable pageable);

    @Query("""
        SELECT r FROM Room r
        WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Room> search(@Param("query") String query, Pageable pageable);

    long countByStatus(RoomStatus status);
}
