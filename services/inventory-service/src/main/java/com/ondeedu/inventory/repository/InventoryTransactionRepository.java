package com.ondeedu.inventory.repository;

import com.ondeedu.inventory.entity.InventoryTransaction;
import com.ondeedu.inventory.entity.InventoryTransaction.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.itemId = :itemId
          AND (:branchId IS NULL OR t.branchId = :branchId)
        ORDER BY t.transactionDate DESC
        """)
    Page<InventoryTransaction> findByItemId(@Param("itemId") UUID itemId,
                                            @Param("branchId") UUID branchId,
                                            Pageable pageable);

    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.transactionType = :transactionType
          AND (:branchId IS NULL OR t.branchId = :branchId)
        ORDER BY t.transactionDate DESC
        """)
    Page<InventoryTransaction> findByTransactionType(@Param("transactionType") TransactionType transactionType,
                                                     @Param("branchId") UUID branchId,
                                                     Pageable pageable);

    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.transactionDate BETWEEN :fromDate AND :toDate
          AND (:branchId IS NULL OR t.branchId = :branchId)
        ORDER BY t.transactionDate DESC
        """)
    Page<InventoryTransaction> findByDateRange(@Param("fromDate") LocalDateTime fromDate,
                                               @Param("toDate") LocalDateTime toDate,
                                               @Param("branchId") UUID branchId,
                                               Pageable pageable);

    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.itemId = :itemId
          AND t.transactionDate BETWEEN :fromDate AND :toDate
          AND (:branchId IS NULL OR t.branchId = :branchId)
        ORDER BY t.transactionDate DESC
        """)
    Page<InventoryTransaction> findByItemIdAndDateRange(@Param("itemId") UUID itemId,
                                                        @Param("fromDate") LocalDateTime fromDate,
                                                        @Param("toDate") LocalDateTime toDate,
                                                        @Param("branchId") UUID branchId,
                                                        Pageable pageable);

    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.performedBy = :staffId
          AND (:branchId IS NULL OR t.branchId = :branchId)
        ORDER BY t.transactionDate DESC
        """)
    Page<InventoryTransaction> findByPerformedBy(@Param("staffId") UUID staffId,
                                                 @Param("branchId") UUID branchId,
                                                 Pageable pageable);

    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.referenceType = :referenceType
          AND t.referenceId = :referenceId
          AND (:branchId IS NULL OR t.branchId = :branchId)
        ORDER BY t.transactionDate DESC
        """)
    Page<InventoryTransaction> findByReference(@Param("referenceType") String referenceType,
                                               @Param("referenceId") UUID referenceId,
                                               @Param("branchId") UUID branchId,
                                               Pageable pageable);

    @Query("""
        SELECT COUNT(t) FROM InventoryTransaction t
        WHERE (:branchId IS NULL OR t.branchId = :branchId)
        """)
    long countAllByBranch(@Param("branchId") UUID branchId);

    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.transactionDate BETWEEN :fromDate AND :toDate
          AND (:branchId IS NULL OR t.branchId = :branchId)
        ORDER BY t.transactionDate DESC
        """)
    List<InventoryTransaction> findAllByDateRangeForExport(@Param("fromDate") LocalDateTime fromDate,
                                                           @Param("toDate") LocalDateTime toDate,
                                                           @Param("branchId") UUID branchId);

    @Query(value = """
        SELECT t.* FROM inventory_transactions t
        JOIN inventory_items i ON i.id = t.item_id
        WHERE (:fromDate IS NULL OR t.transaction_date >= CAST(:fromDate AS TIMESTAMP))
          AND (:toDate IS NULL OR t.transaction_date <= CAST(:toDate AS TIMESTAMP))
          AND (:transactionType IS NULL OR t.transaction_type = :transactionType)
          AND (:search IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:branchId IS NULL OR t.branch_id = CAST(:branchId AS UUID))
        ORDER BY t.transaction_date DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM inventory_transactions t
        JOIN inventory_items i ON i.id = t.item_id
        WHERE (:fromDate IS NULL OR t.transaction_date >= CAST(:fromDate AS TIMESTAMP))
          AND (:toDate IS NULL OR t.transaction_date <= CAST(:toDate AS TIMESTAMP))
          AND (:transactionType IS NULL OR t.transaction_type = :transactionType)
          AND (:search IS NULL OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:branchId IS NULL OR t.branch_id = CAST(:branchId AS UUID))
        """,
        nativeQuery = true)
    Page<InventoryTransaction> findByFilters(@Param("fromDate") LocalDateTime fromDate,
                                             @Param("toDate") LocalDateTime toDate,
                                             @Param("transactionType") String transactionType,
                                             @Param("search") String search,
                                             @Param("branchId") String branchId,
                                             Pageable pageable);
}
