package com.ondeedu.payment.repository;

import com.ondeedu.payment.entity.Subscription;
import com.ondeedu.payment.entity.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Page<Subscription> findByStudentId(UUID studentId, Pageable pageable);

    Page<Subscription> findByStudentIdAndStatus(UUID studentId, SubscriptionStatus status, Pageable pageable);

    Page<Subscription> findByCourseId(UUID courseId, Pageable pageable);

    Page<Subscription> findByStatus(SubscriptionStatus status, Pageable pageable);

    boolean existsByStudentIdAndCourseIdAndStatus(UUID studentId, UUID courseId, SubscriptionStatus status);

    List<Subscription> findByStudentIdAndCourseIdAndStatusOrderByCreatedAtDesc(
            UUID studentId,
            UUID courseId,
            SubscriptionStatus status
    );

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Subscription s WHERE s.studentId = :studentId")
    BigDecimal sumAmountByStudentId(@Param("studentId") UUID studentId);

    /** Все подписки студента без пагинации (для payment history). */
    List<Subscription> findByStudentIdOrderByStartDateDesc(UUID studentId);

    /**
     * Подписки, активные в заданном периоде — для monthly overview.
     * Заменяет findAll() + фильтрацию на Java-стороне.
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status IN :statuses
              AND s.startDate <= :lastDay
              AND (s.endDate IS NULL OR s.endDate >= :firstDay)
              AND (:branchId IS NULL OR s.branchId = :branchId)
            """)
    List<Subscription> findActiveInPeriod(
            @Param("statuses") List<SubscriptionStatus> statuses,
            @Param("firstDay") LocalDate firstDay,
            @Param("lastDay") LocalDate lastDay,
            @Param("branchId") UUID branchId);

    /**
     * Активные подписки, ещё не истёкшие на дату today — для debtors.
     */
    @Query("""
            SELECT s FROM Subscription s
            WHERE s.status = :status
              AND (s.endDate IS NULL OR s.endDate >= :today)
              AND (:branchId IS NULL OR s.branchId = :branchId)
            """)
    List<Subscription> findActiveNotExpiredBefore(
            @Param("status") SubscriptionStatus status,
            @Param("today") LocalDate today,
            @Param("branchId") UUID branchId);

    // ── Branch filtering ──────────────────────────────────────────────

    @Query("SELECT s FROM Subscription s WHERE (:branchId IS NULL OR s.branchId = :branchId)")
    Page<Subscription> findAllByBranch(@Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT s FROM Subscription s WHERE s.studentId = :studentId AND (:branchId IS NULL OR s.branchId = :branchId)")
    Page<Subscription> findByStudentIdAndBranch(@Param("studentId") UUID studentId, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT s FROM Subscription s WHERE s.status = :status AND (:branchId IS NULL OR s.branchId = :branchId)")
    Page<Subscription> findByStatusAndBranch(@Param("status") SubscriptionStatus status, @Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Subscription s WHERE (:branchId IS NULL OR s.branchId = :branchId)")
    long countAllByBranch(@Param("branchId") UUID branchId);

    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.status = :status AND (:branchId IS NULL OR s.branchId = :branchId)")
    long countByStatusAndBranch(@Param("status") SubscriptionStatus status, @Param("branchId") UUID branchId);
}
