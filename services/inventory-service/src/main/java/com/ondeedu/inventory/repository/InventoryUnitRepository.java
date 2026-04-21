package com.ondeedu.inventory.repository;

import com.ondeedu.inventory.entity.InventoryUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryUnitRepository extends JpaRepository<InventoryUnit, UUID> {

    @Query("""
        SELECT u FROM InventoryUnit u
        WHERE (:branchId IS NULL OR u.branchId = :branchId)
        ORDER BY u.name ASC
        """)
    List<InventoryUnit> findAllByBranchOrdered(@Param("branchId") UUID branchId);

    @Query("""
        SELECT u FROM InventoryUnit u
        WHERE u.isActive = true
          AND (:branchId IS NULL OR u.branchId = :branchId)
        ORDER BY u.name ASC
        """)
    List<InventoryUnit> findActiveByBranch(@Param("branchId") UUID branchId);

    boolean existsByNameAndBranchId(String name, UUID branchId);

    boolean existsByAbbreviationAndBranchId(String abbreviation, UUID branchId);
}
