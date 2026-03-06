package com.ondeedu.payment.repository;

import com.ondeedu.payment.entity.PriceList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PriceListRepository extends JpaRepository<PriceList, UUID> {

    Page<PriceList> findByIsActive(boolean isActive, Pageable pageable);

    Page<PriceList> findByCourseId(UUID courseId, Pageable pageable);
}
