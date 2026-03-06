package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.RoleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleConfigRepository extends JpaRepository<RoleConfig, UUID> {

    List<RoleConfig> findAllByOrderByNameAsc();

    Optional<RoleConfig> findByName(String name);

    boolean existsByName(String name);
}
