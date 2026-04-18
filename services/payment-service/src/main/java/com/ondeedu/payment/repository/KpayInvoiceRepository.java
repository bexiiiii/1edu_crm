package com.ondeedu.payment.repository;

import com.ondeedu.payment.entity.KpayInvoice;
import com.ondeedu.payment.entity.KpayInvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KpayInvoiceRepository extends JpaRepository<KpayInvoice, UUID>, JpaSpecificationExecutor<KpayInvoice> {

    boolean existsBySubscriptionIdAndPaymentMonth(UUID subscriptionId, String paymentMonth);

    Optional<KpayInvoice> findByMerchantInvoiceId(String merchantInvoiceId);

    long countByPaymentMonthAndStatus(String paymentMonth, KpayInvoiceStatus status);
}
