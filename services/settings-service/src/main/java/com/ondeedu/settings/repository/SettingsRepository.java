package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.TenantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettingsRepository extends JpaRepository<TenantSettings, UUID> {

    Optional<TenantSettings> findFirstBy();
}
