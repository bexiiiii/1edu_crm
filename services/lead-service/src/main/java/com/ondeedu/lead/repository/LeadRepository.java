package com.ondeedu.lead.repository;

import com.ondeedu.lead.entity.Lead;
import com.ondeedu.lead.entity.LeadStage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Page<Lead> findByStage(LeadStage stage, Pageable pageable);

    Page<Lead> findByAssignedTo(String assignedTo, Pageable pageable);

    @Query("""
        SELECT l FROM Lead l
        WHERE (:branchId IS NULL OR l.branchId = :branchId)
          AND LOWER(l.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(l.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
           OR l.phone LIKE CONCAT('%', :query, '%')
           OR LOWER(l.email) LIKE LOWER(CONCAT('%', :query, '%'))
        """)
    Page<Lead> search(@Param("query") String query, Pageable pageable);

    @Query("""
        SELECT l FROM Lead l
        WHERE (:branchId IS NULL OR l.branchId = :branchId)
          AND (
            LOWER(l.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
             OR LOWER(l.lastName) LIKE LOWER(CONCAT('%', :query, '%'))
             OR l.phone LIKE CONCAT('%', :query, '%')
             OR LOWER(l.email) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    Page<Lead> searchByBranch(@Param("branchId") UUID branchId,
                               @Param("query") String query,
                               Pageable pageable);

    @Query("""
        SELECT l FROM Lead l
        WHERE l.stage = :stage
          AND (:branchId IS NULL OR l.branchId = :branchId)
        """)
    Page<Lead> findByStageAndBranch(@Param("stage") LeadStage stage,
                                     @Param("branchId") UUID branchId,
                                     Pageable pageable);

    @Query("""
        SELECT l FROM Lead l
        WHERE l.assignedTo = :assignedTo
          AND (:branchId IS NULL OR l.branchId = :branchId)
        """)
    Page<Lead> findByAssignedToAndBranch(@Param("assignedTo") String assignedTo,
                                          @Param("branchId") UUID branchId,
                                          Pageable pageable);

    @Query("""
        SELECT l FROM Lead l
        WHERE (:branchId IS NULL OR l.branchId = :branchId)
        """)
    Page<Lead> findAllByBranch(@Param("branchId") UUID branchId, Pageable pageable);

    @Query(value = """
        SELECT *
        FROM leads l
        WHERE regexp_replace(COALESCE(l.phone, ''), '[^0-9]', '', 'g')
              = regexp_replace(CAST(:phone AS text), '[^0-9]', '', 'g')
          AND (:branchId IS NULL OR l.branch_id = :branchId)
        ORDER BY l.created_at DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Lead> findLatestByNormalizedPhoneAndBranch(@Param("phone") String phone,
                                                         @Param("branchId") UUID branchId);

    /** Compatibility alias — uses branch=null (searches all branches) */
    default Optional<Lead> findLatestByNormalizedPhone(String phone) {
        return findLatestByNormalizedPhoneAndBranch(phone, null);
    }

    Optional<Lead> findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    long countByStage(LeadStage stage);

    @Query("SELECT COUNT(l) FROM Lead l WHERE (:branchId IS NULL OR l.branchId = :branchId)")
    long countAllByBranch(@Param("branchId") UUID branchId);

    @Query("""
        SELECT COUNT(l)
        FROM Lead l
        WHERE l.stage = :stage
          AND (:branchId IS NULL OR l.branchId = :branchId)
        """)
    long countByStageAndBranch(@Param("stage") LeadStage stage, @Param("branchId") UUID branchId);
}
