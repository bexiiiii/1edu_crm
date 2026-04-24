package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.AttendanceStatusConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceStatusConfigRepository extends JpaRepository<AttendanceStatusConfig, UUID> {

    @Query("SELECT a FROM AttendanceStatusConfig a WHERE (:branchId IS NULL OR a.branchId = :branchId) ORDER BY a.sortOrder ASC")
    List<AttendanceStatusConfig> findAllByBranchOrderBySortOrderAsc(@Param("branchId") UUID branchId);

    @Query("SELECT COUNT(a) FROM AttendanceStatusConfig a WHERE (:branchId IS NULL OR a.branchId = :branchId)")
    long countByBranch(@Param("branchId") UUID branchId);
}
