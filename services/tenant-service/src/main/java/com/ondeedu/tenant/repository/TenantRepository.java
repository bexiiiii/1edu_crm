package com.ondeedu.tenant.repository;

import com.ondeedu.tenant.entity.Tenant;
import com.ondeedu.tenant.entity.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySubdomain(String subdomain);

    boolean existsBySubdomain(String subdomain);

    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

    @Query("SELECT t FROM Tenant t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(t.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Tenant> search(@Param("query") String query, Pageable pageable);

    // Native queries bypass @SQLRestriction — used by SUPER_ADMIN to access deleted/banned records

    @Query(value = "SELECT * FROM system.tenants WHERE id = :id", nativeQuery = true)
    Optional<Tenant> findByIdIncludingDeleted(@Param("id") UUID id);

    @Query(value = "SELECT * FROM system.tenants WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", nativeQuery = true)
    List<Tenant> findAllDeleted();

    @Query(value = "SELECT * FROM system.tenants WHERE status = 'BANNED' AND deleted_at IS NULL ORDER BY banned_at DESC", nativeQuery = true)
    List<Tenant> findAllBanned();
}
