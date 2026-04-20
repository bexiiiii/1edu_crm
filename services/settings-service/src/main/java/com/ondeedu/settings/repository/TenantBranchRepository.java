package com.ondeedu.settings.repository;

import com.ondeedu.settings.entity.TenantBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantBranchRepository extends JpaRepository<TenantBranch, UUID> {

    List<TenantBranch> findAllByOrderByIsDefaultDescNameAsc();

    boolean existsByNameIgnoreCase(String name);

    Optional<TenantBranch> findFirstByIsDefaultTrue();

    long countByActiveTrue();
}
