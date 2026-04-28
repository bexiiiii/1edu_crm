package com.ondeedu.inventory.repository;

import com.ondeedu.inventory.entity.InventoryRevision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryRevisionRepository extends JpaRepository<InventoryRevision, UUID> {

    @Query("""
        SELECT r FROM InventoryRevision r
        WHERE (:branchId IS NULL OR r.branchId = :branchId)
        ORDER BY r.revisionDate DESC, r.createdAt DESC
        """)
    Page<InventoryRevision> findAllByBranch(@Param("branchId") UUID branchId, Pageable pageable);
}
