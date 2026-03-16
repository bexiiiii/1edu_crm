package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.StaffStatusConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StaffStatusConfigRepository extends JpaRepository<StaffStatusConfig, UUID> {

    List<StaffStatusConfig> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
