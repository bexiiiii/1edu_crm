package com.ondeedu.payment.repository;

import com.ondeedu.payment.entity.StudentPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StudentPaymentRepository extends JpaRepository<StudentPayment, UUID> {

    List<StudentPayment> findByStudentIdOrderByPaidAtDesc(UUID studentId);

    List<StudentPayment> findBySubscriptionIdOrderByPaidAtDesc(UUID subscriptionId);

    List<StudentPayment> findByStudentIdAndPaymentMonthOrderByPaidAtDesc(UUID studentId, String paymentMonth);

    @Query("SELECT sp FROM StudentPayment sp WHERE sp.paymentMonth = :month ORDER BY sp.paidAt DESC")
    List<StudentPayment> findByPaymentMonth(@Param("month") String month);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM StudentPayment sp WHERE sp.subscriptionId = :subscriptionId AND sp.paymentMonth = :month")
    BigDecimal sumBySubscriptionAndMonth(@Param("subscriptionId") UUID subscriptionId, @Param("month") String month);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM StudentPayment sp WHERE sp.subscriptionId = :subscriptionId")
    BigDecimal sumBySubscription(@Param("subscriptionId") UUID subscriptionId);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM StudentPayment sp WHERE sp.studentId = :studentId AND sp.paymentMonth = :month")
    BigDecimal sumByStudentAndMonth(@Param("studentId") UUID studentId, @Param("month") String month);

    /** Один запрос вместо N*M — загружает все платежи по набору подписок. */
    @Query("SELECT sp FROM StudentPayment sp WHERE sp.subscriptionId IN :ids")
    List<StudentPayment> findBySubscriptionIdIn(@Param("ids") Collection<UUID> ids);
}
