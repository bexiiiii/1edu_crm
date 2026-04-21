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
}
