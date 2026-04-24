package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.StaffStatusConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StaffStatusConfigRepository extends JpaRepository<StaffStatusConfig, UUID> {

    @Query("SELECT s FROM StaffStatusConfig s WHERE (:branchId IS NULL OR s.branchId = :branchId) ORDER BY s.sortOrder ASC, s.name ASC")
    List<StaffStatusConfig> findAllByBranchOrderBySortOrderAscNameAsc(@Param("branchId") UUID branchId);

    boolean existsByNameIgnoreCaseAndBranchId(String name, UUID branchId);
}
