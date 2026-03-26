package com.ondeedu.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.security.PermissionUtils;
import com.ondeedu.common.security.RoleNameUtils;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.settings.client.AuthRoleClient;
import com.ondeedu.settings.dto.RoleConfigDto;
import com.ondeedu.settings.dto.SaveRoleConfigRequest;
import com.ondeedu.settings.entity.Permission;
import com.ondeedu.settings.entity.RoleConfig;
import com.ondeedu.settings.repository.RoleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleConfigService {

    private final RoleConfigRepository repository;
    private final ObjectMapper objectMapper;
    private final AuthRoleClient authRoleClient;

    @Transactional(readOnly = true)
    @Cacheable(value = "role-configs", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('all')")
    public List<RoleConfigDto> getAll() {
        return repository.findAllByOrderByNameAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleConfigDto getById(UUID id) {
        return toDto(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoleConfig", "id", id)));
    }

    /** Returns all available permission codes (for the frontend picker) */
    @Cacheable(value = "role-permissions", key = "'all'")
    public List<String> getAllPermissions() {
        return Arrays.stream(Permission.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "role-configs", allEntries = true)
    public RoleConfigDto create(SaveRoleConfigRequest request) {
        String roleName = RoleNameUtils.normalizeRoleName(request.getName());
        List<String> normalizedPermissions = PermissionUtils.normalizePermissions(request.getPermissions());
        if (RoleNameUtils.isSystemRole(roleName)) {
            throw new BusinessException("SYSTEM_ROLE_CONFIG_FORBIDDEN",
                    "Built-in roles are reserved. Create tenant-specific custom roles with a new name.");
        }
        if (repository.existsByName(roleName)) {
            throw new BusinessException("DUPLICATE_ROLE", "Role with name '" + roleName + "' already exists");
        }
        RoleConfig entity = RoleConfig.builder()
                .name(roleName)
                .description(request.getDescription())
                .permissions(toJson(normalizedPermissions))
                .build();
        entity = repository.save(entity);
        authRoleClient.syncRole(requireTenantId(), entity.getName(), entity.getDescription(), fromJson(entity.getPermissions()));
        log.info("Created role config: {}", entity.getName());
        return toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "role-configs", allEntries = true)
    public RoleConfigDto update(UUID id, SaveRoleConfigRequest request) {
        RoleConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoleConfig", "id", id));
        String requestedRoleName = RoleNameUtils.normalizeRoleName(request.getName());
        List<String> normalizedPermissions = request.getPermissions() != null
                ? PermissionUtils.normalizePermissions(request.getPermissions())
                : null;

        if (RoleNameUtils.isSystemRole(entity.getName())) {
            throw new BusinessException("SYSTEM_ROLE_CONFIG_FORBIDDEN",
                    "Built-in roles cannot be managed through custom role configs.");
        }

        if (!entity.getName().equals(requestedRoleName)) {
            throw new BusinessException("ROLE_RENAME_NOT_SUPPORTED",
                    "Role renaming is not supported. Create a new role and migrate users instead.");
        }

        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (normalizedPermissions != null) {
            entity.setPermissions(toJson(normalizedPermissions));
        }
        entity = repository.save(entity);
        authRoleClient.syncRole(requireTenantId(), entity.getName(), entity.getDescription(), fromJson(entity.getPermissions()));
        log.info("Updated role config: {}", id);
        return toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "role-configs", allEntries = true)
    public void delete(UUID id) {
        RoleConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoleConfig", "id", id));
        if (RoleNameUtils.isSystemRole(entity.getName())) {
            throw new BusinessException("SYSTEM_ROLE_CONFIG_FORBIDDEN",
                    "Built-in roles cannot be deleted from custom role configs.");
        }
        authRoleClient.deleteRole(requireTenantId(), entity.getName());
        repository.deleteById(id);
        log.info("Deleted role config: {}", id);
    }

    // --- helpers ---

    private RoleConfigDto toDto(RoleConfig entity) {
        return RoleConfigDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .permissions(fromJson(entity.getPermissions()))
                .build();
    }

    private String toJson(List<String> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String requireTenantId() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException("TENANT_CONTEXT_MISSING", "Tenant context is required for role management");
        }
        return tenantId;
    }
}
