package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.AttendanceStatusConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttendanceStatusConfigRepository extends JpaRepository<AttendanceStatusConfig, UUID> {

    List<AttendanceStatusConfig> findAllByOrderBySortOrderAsc();
}
