package com.ondeedu.settings.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.settings.dto.SaveStaffStatusRequest;
import com.ondeedu.settings.dto.StaffStatusConfigDto;
import com.ondeedu.settings.entity.StaffStatusConfig;
import com.ondeedu.settings.mapper.StaffStatusConfigMapper;
import com.ondeedu.settings.repository.StaffStatusConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffStatusConfigService {

    private final StaffStatusConfigRepository repository;
    private final StaffStatusConfigMapper mapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "staff-statuses", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('all') + '::branch=' + T(com.ondeedu.common.tenant.TenantContext).getBranchId()")
    public List<StaffStatusConfigDto> getAll() {
        UUID branchId = resolveCurrentBranchId();
        return repository.findAllByBranchOrderBySortOrderAscNameAsc(branchId)
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "staff-statuses", allEntries = true)
    public StaffStatusConfigDto create(SaveStaffStatusRequest request) {
        UUID branchId = resolveCurrentBranchId();
        if (repository.existsByNameIgnoreCaseAndBranchId(request.getName(), branchId)) {
            throw new BusinessException("DUPLICATE_STAFF_STATUS", "Staff status with name '" + request.getName() + "' already exists");
        }

        StaffStatusConfig entity = mapper.toEntity(request);
        entity.setBranchId(branchId);
        entity = repository.save(entity);
        log.info("Created staff status config: {}", entity.getName());
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "staff-statuses", allEntries = true)
    public StaffStatusConfigDto update(UUID id, SaveStaffStatusRequest request) {
        StaffStatusConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StaffStatusConfig", "id", id));

        UUID branchId = resolveCurrentBranchId();
        if (!entity.getName().equalsIgnoreCase(request.getName()) && repository.existsByNameIgnoreCaseAndBranchId(request.getName(), branchId)) {
            throw new BusinessException("DUPLICATE_STAFF_STATUS", "Staff status with name '" + request.getName() + "' already exists");
        }

        mapper.updateEntity(entity, request);
        entity = repository.save(entity);
        log.info("Updated staff status config: {}", id);
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "staff-statuses", allEntries = true)
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("StaffStatusConfig", "id", id);
        }
        repository.deleteById(id);
        log.info("Deleted staff status config: {}", id);
    }

    private UUID resolveCurrentBranchId() {
        String rawBranchId = TenantContext.getBranchId();
        if (!StringUtils.hasText(rawBranchId)) return null;
        try {
            return UUID.fromString(rawBranchId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
