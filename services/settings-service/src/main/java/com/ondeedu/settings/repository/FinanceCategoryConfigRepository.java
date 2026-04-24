package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.FinanceCategoryConfig;
import com.ondeedu.settings.entity.FinanceCategoryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FinanceCategoryConfigRepository extends JpaRepository<FinanceCategoryConfig, UUID> {

    @Query("SELECT f FROM FinanceCategoryConfig f WHERE f.type = :type AND (:branchId IS NULL OR f.branchId = :branchId) ORDER BY f.sortOrder ASC, f.name ASC")
    List<FinanceCategoryConfig> findAllByTypeAndBranchOrderBySortOrderAscNameAsc(@Param("type") FinanceCategoryType type, @Param("branchId") UUID branchId);

    boolean existsByTypeAndNameIgnoreCaseAndBranchId(FinanceCategoryType type, String name, UUID branchId);
}
