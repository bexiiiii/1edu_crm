package com.ondeedu.inventory.repository;

import com.ondeedu.inventory.entity.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    @Query("""
        SELECT i FROM InventoryItem i
        WHERE (:branchId IS NULL OR i.branchId = :branchId)
        """)
    Page<InventoryItem> findAllByBranch(@Param("branchId") UUID branchId, Pageable pageable);

    @Query("""
        SELECT i FROM InventoryItem i
        WHERE i.name ILIKE %:query%
          AND (:branchId IS NULL OR i.branchId = :branchId)
        """)
    Page<InventoryItem> searchByBranch(@Param("query") String query,
                                       @Param("branchId") UUID branchId,
                                       Pageable pageable);

    @Query("""
        SELECT i FROM InventoryItem i
        WHERE i.category.id = :categoryId
          AND (:branchId IS NULL OR i.branchId = :branchId)
        """)
    Page<InventoryItem> findByCategoryId(@Param("categoryId") UUID categoryId,
                                         @Param("branchId") UUID branchId,
                                         Pageable pageable);

    @Query("""
        SELECT i FROM InventoryItem i
        WHERE i.status = :status
          AND (:branchId IS NULL OR i.branchId = :branchId)
        """)
    Page<InventoryItem> findByStatusAndBranch(@Param("status") String status,
                                              @Param("branchId") UUID branchId,
                                              Pageable pageable);

    @Query("""
        SELECT i FROM InventoryItem i
        WHERE i.sku = :sku
          AND (:branchId IS NULL OR i.branchId = :branchId)
        """)
    Optional<InventoryItem> findBySkuAndBranch(@Param("sku") String sku,
                                               @Param("branchId") UUID branchId);

    boolean existsBySkuAndBranchId(String sku, UUID branchId);

    @Query("""
        SELECT COUNT(i) FROM InventoryItem i
        WHERE i.status = 'LOW_STOCK'
          AND (:branchId IS NULL OR i.branchId = :branchId)
        """)
    long countLowStockByBranch(@Param("branchId") UUID branchId);

    @Query("""
        SELECT COUNT(i) FROM InventoryItem i
        WHERE i.status = 'OUT_OF_STOCK'
          AND (:branchId IS NULL OR i.branchId = :branchId)
        """)
    long countOutOfStockByBranch(@Param("branchId") UUID branchId);

    @Query("""
        SELECT i FROM InventoryItem i
        WHERE i.requiresReorder = true
          AND (:branchId IS NULL OR i.branchId = :branchId)
        """)
    Page<InventoryItem> findReorderRequired(@Param("branchId") UUID branchId, Pageable pageable);
}
