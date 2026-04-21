package com.ondeedu.inventory.repository;

import com.ondeedu.inventory.entity.InventoryCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryCategoryRepository extends JpaRepository<InventoryCategory, UUID> {

    @Query("""
        SELECT c FROM InventoryCategory c
        WHERE (:branchId IS NULL OR c.branchId = :branchId)
        ORDER BY c.sortOrder ASC, c.name ASC
        """)
    List<InventoryCategory> findAllByBranchOrdered(@Param("branchId") UUID branchId);

    @Query("""
        SELECT c FROM InventoryCategory c
        WHERE c.isActive = true
          AND (:branchId IS NULL OR c.branchId = :branchId)
        ORDER BY c.sortOrder ASC, c.name ASC
        """)
    List<InventoryCategory> findActiveByBranch(@Param("branchId") UUID branchId);

    boolean existsByNameAndBranchId(String name, UUID branchId);

    @Query("""
        SELECT COUNT(c) FROM InventoryCategory c
        WHERE c.isActive = true
          AND (:branchId IS NULL OR c.branchId = :branchId)
        """)
    long countActiveByBranch(@Param("branchId") UUID branchId);
}
