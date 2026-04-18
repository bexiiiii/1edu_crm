package com.ondeedu.payment.repository;

import com.ondeedu.payment.entity.ApiPayInvoice;
import com.ondeedu.payment.entity.ApiPayInvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiPayInvoiceRepository extends JpaRepository<ApiPayInvoice, UUID>, JpaSpecificationExecutor<ApiPayInvoice> {

    boolean existsBySubscriptionIdAndPaymentMonth(UUID subscriptionId, String paymentMonth);

    Optional<ApiPayInvoice> findByMerchantInvoiceId(String merchantInvoiceId);

    long countByPaymentMonthAndStatus(String paymentMonth, ApiPayInvoiceStatus status);
}
