package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.PaymentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentSourceRepository extends JpaRepository<PaymentSource, UUID> {

    @Query("SELECT p FROM PaymentSource p WHERE (:branchId IS NULL OR p.branchId = :branchId) ORDER BY p.sortOrder ASC")
    List<PaymentSource> findAllByBranchOrderBySortOrderAsc(@Param("branchId") UUID branchId);
}
