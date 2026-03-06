package com.ondeedu.lead.repository;

import com.ondeedu.lead.entity.Lead;
import com.ondeedu.lead.entity.LeadStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Page<Lead> findByStage(LeadStage stage, Pageable pageable);

    Page<Lead> findByAssignedTo(String assignedTo, Pageable pageable);

    @Query("""
        SELECT l FROM Lead l
        WHERE LOWER(l.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(l.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR l.phone LIKE CONCAT('%', :query, '%')
           OR LOWER(l.email) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Lead> search(@Param("query") String query, Pageable pageable);

    long countByStage(LeadStage stage);
}
