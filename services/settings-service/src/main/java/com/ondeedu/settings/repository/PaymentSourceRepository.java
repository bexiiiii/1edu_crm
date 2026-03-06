package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.PaymentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentSourceRepository extends JpaRepository<PaymentSource, UUID> {

    List<PaymentSource> findAllByOrderBySortOrderAsc();
}
