package com.ondeedu.tenant.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.tenant.client.CertIssuerClient;
import com.ondeedu.tenant.dto.CreateTenantRequest;
import com.ondeedu.tenant.dto.TenantDto;
import com.ondeedu.tenant.dto.UpdateTenantRequest;
import com.ondeedu.tenant.entity.Tenant;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.mapper.TenantMapper;
import com.ondeedu.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;
    private final CertIssuerClient certIssuerClient;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public TenantDto createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsBySubdomain(request.getSubdomain())) {
            throw new BusinessException("DUPLICATE_SUBDOMAIN",
                "Tenant with subdomain '" + request.getSubdomain() + "' already exists");
        }

        Tenant tenant = tenantMapper.toEntity(request);
        // Set a temporary placeholder — will be updated after save
        tenant.setSchemaName("pending");
        tenant = tenantRepository.save(tenant);

        // Now set the real schema name based on the generated ID
        String schemaName = "tenant_" + tenant.getId().toString().replace("-", "");
        tenant.setSchemaName(schemaName);
        tenant = tenantRepository.save(tenant);

        log.info("Created tenant: {} with schema: {}", tenant.getName(), schemaName);

        // Create the tenant schema in the database
        try {
            entityManager.createNativeQuery("SELECT system.create_tenant_schema(:schemaName)")
                .setParameter("schemaName", schemaName)
                .getSingleResult();
            entityManager.createNativeQuery("SELECT system.migrate_tenant_schema(:schemaName)")
                .setParameter("schemaName", schemaName)
                .getSingleResult();
            entityManager.createNativeQuery("SELECT system.ensure_course_students_schema(:schemaName)")
                .setParameter("schemaName", schemaName)
                .getSingleResult();
            entityManager.createNativeQuery("SELECT system.ensure_payroll_schema(:schemaName)")
                .setParameter("schemaName", schemaName)
                .getSingleResult();
            entityManager.createNativeQuery("SELECT system.ensure_settings_schema(:schemaName)")
                .setParameter("schemaName", schemaName)
                .getSingleResult();
            entityManager.createNativeQuery("SELECT system.ensure_extended_settings_schema(:schemaName)")
                .setParameter("schemaName", schemaName)
                .getSingleResult();
            entityManager.createNativeQuery("SELECT system.add_performance_indexes(:schemaName)")
                .setParameter("schemaName", schemaName)
                .getSingleResult();
            entityManager.createNativeQuery("SELECT system.add_extended_indexes(:schemaName)")
                .setParameter("schemaName", schemaName)
                .getSingleResult();
            log.info("Schema created successfully: {}", schemaName);
        } catch (Exception e) {
            log.error("Failed to create schema for tenant {}: {}", tenant.getId(), e.getMessage());
            throw new BusinessException("SCHEMA_CREATION_FAILED",
                "Failed to create tenant schema: " + e.getMessage());
        }

        certIssuerClient.issueCert(tenant.getSubdomain());
        return tenantMapper.toDto(tenant);
    }

    @Transactional(readOnly = true)
    public TenantDto getTenant(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));
        return tenantMapper.toDto(tenant);
    }

    @Transactional(readOnly = true)
    public TenantDto getBySubdomain(String subdomain) {
        Tenant tenant = tenantRepository.findBySubdomain(subdomain)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "subdomain", subdomain));
        return tenantMapper.toDto(tenant);
    }

    @Transactional
    public TenantDto updateTenant(UUID id, UpdateTenantRequest request) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));

        tenantMapper.updateEntity(tenant, request);
        tenant = tenantRepository.save(tenant);

        log.info("Updated tenant: {}", id);
        return tenantMapper.toDto(tenant);
    }

    @Transactional
    public void forceDeleteTenant(UUID id) {
        tenantRepository.findById(id).ifPresent(tenantRepository::delete);
        log.warn("Force-deleted tenant: {} (registration rollback)", id);
    }

    @Transactional
    public void deleteTenant(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", "id", id));

        if (tenant.getStatus() != TenantStatus.INACTIVE) {
            throw new BusinessException("TENANT_NOT_INACTIVE",
                "Tenant can only be deleted when status is INACTIVE. Current status: " + tenant.getStatus());
        }

        tenantRepository.delete(tenant);
        log.info("Deleted tenant: {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantDto> listTenants(TenantStatus status, Pageable pageable) {
        Page<Tenant> page;
        if (status != null) {
            page = tenantRepository.findByStatus(status, pageable);
        } else {
            page = tenantRepository.findAll(pageable);
        }
        return PageResponse.from(page, tenantMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantDto> searchTenants(String query, Pageable pageable) {
        Page<Tenant> page = tenantRepository.search(query, pageable);
        return PageResponse.from(page, tenantMapper::toDto);
    }
}
