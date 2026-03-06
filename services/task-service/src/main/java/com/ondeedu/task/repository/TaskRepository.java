package com.ondeedu.task.repository;

import com.ondeedu.task.entity.Task;
import com.ondeedu.task.entity.TaskPriority;
import com.ondeedu.task.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Page<Task> findByStatus(TaskStatus status, Pageable pageable);

    Page<Task> findByPriority(TaskPriority priority, Pageable pageable);

    Page<Task> findByAssignedTo(UUID assignedTo, Pageable pageable);

    Page<Task> findByAssignedToAndStatus(UUID assignedTo, TaskStatus status, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.dueDate <= :date AND t.status != 'DONE' AND t.status != 'CANCELLED'")
    Page<Task> findOverdue(@Param("date") LocalDate date, Pageable pageable);

    @Query("""
        SELECT t FROM Task t
        WHERE LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Task> search(@Param("query") String query, Pageable pageable);

    long countByStatus(TaskStatus status);

    long countByAssignedToAndStatus(UUID assignedTo, TaskStatus status);
}
