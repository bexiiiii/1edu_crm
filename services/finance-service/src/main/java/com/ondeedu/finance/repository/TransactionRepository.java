package com.ondeedu.finance.repository;

import com.ondeedu.finance.entity.Transaction;
import com.ondeedu.finance.entity.TransactionStatus;
import com.ondeedu.finance.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByType(TransactionType type, Pageable pageable);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    Page<Transaction> findByStudentId(UUID studentId, Pageable pageable);

    List<Transaction> findBySalaryMonthAndStaffIdIsNotNullOrderByTransactionDateDescCreatedAtDesc(String salaryMonth);

    @Query("SELECT t FROM Transaction t WHERE t.salaryMonth = :salaryMonth AND t.staffId IS NOT NULL AND (:branchId IS NULL OR t.branchId = :branchId) ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findBySalaryMonthAndStaffIdIsNotNullAndBranch(@Param("salaryMonth") String salaryMonth, @Param("branchId") UUID branchId);

    List<Transaction> findByStaffIdAndSalaryMonthIsNotNullOrderByTransactionDateDescCreatedAtDesc(UUID staffId);

    Page<Transaction> findByTransactionDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = :type AND t.status = 'COMPLETED' AND t.transactionDate BETWEEN :from AND :to")
    BigDecimal sumByTypeAndDateRange(@Param("type") TransactionType type, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.staffId = :staffId
              AND t.salaryMonth = :salaryMonth
              AND t.type = com.ondeedu.finance.entity.TransactionType.EXPENSE
              AND t.status = com.ondeedu.finance.entity.TransactionStatus.COMPLETED
            """)
    BigDecimal sumSalaryPaymentsByStaffIdAndMonth(@Param("staffId") UUID staffId, @Param("salaryMonth") String salaryMonth);

    // ── Branch filtering ──────────────────────────────────────────────

    @Query("SELECT t FROM Transaction t WHERE (:branchId IS NULL OR t.branchId = :branchId)")
    Page<Transaction> findAllByBranch(@Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.type = :type AND (:branchId IS NULL OR t.branchId = :branchId)")
    Page<Transaction> findByTypeAndBranch(@Param("type") TransactionType type, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.status = :status AND (:branchId IS NULL OR t.branchId = :branchId)")
    Page<Transaction> findByStatusAndBranch(@Param("status") TransactionStatus status, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.studentId = :studentId AND (:branchId IS NULL OR t.branchId = :branchId)")
    Page<Transaction> findByStudentIdAndBranch(@Param("studentId") UUID studentId, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.transactionDate BETWEEN :startDate AND :endDate AND (:branchId IS NULL OR t.branchId = :branchId)")
    Page<Transaction> findByTransactionDateBetweenAndBranch(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE (:branchId IS NULL OR t.branchId = :branchId)")
    long countAllByBranch(@Param("branchId") UUID branchId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = :type AND t.status = 'COMPLETED' AND t.transactionDate BETWEEN :from AND :to AND (:branchId IS NULL OR t.branchId = :branchId)")
    BigDecimal sumByTypeAndDateRangeAndBranch(@Param("type") TransactionType type, @Param("from") LocalDate from, @Param("to") LocalDate to, @Param("branchId") UUID branchId);
}
