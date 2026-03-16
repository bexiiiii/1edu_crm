package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.FinanceCategoryConfig;
import com.ondeedu.settings.entity.FinanceCategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FinanceCategoryConfigRepository extends JpaRepository<FinanceCategoryConfig, UUID> {

    List<FinanceCategoryConfig> findAllByTypeOrderBySortOrderAscNameAsc(FinanceCategoryType type);

    boolean existsByTypeAndNameIgnoreCase(FinanceCategoryType type, String name);
}
