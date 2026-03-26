package com.ondeedu.settings.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
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
    @Cacheable(value = "staff-statuses", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('all')")
    public List<StaffStatusConfigDto> getAll() {
        return repository.findAllByOrderBySortOrderAscNameAsc()
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "staff-statuses", allEntries = true)
    public StaffStatusConfigDto create(SaveStaffStatusRequest request) {
        if (repository.existsByNameIgnoreCase(request.getName())) {
            throw new BusinessException("DUPLICATE_STAFF_STATUS", "Staff status with name '" + request.getName() + "' already exists");
        }

        StaffStatusConfig entity = mapper.toEntity(request);
        entity = repository.save(entity);
        log.info("Created staff status config: {}", entity.getName());
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "staff-statuses", allEntries = true)
    public StaffStatusConfigDto update(UUID id, SaveStaffStatusRequest request) {
        StaffStatusConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StaffStatusConfig", "id", id));

        if (!entity.getName().equalsIgnoreCase(request.getName()) && repository.existsByNameIgnoreCase(request.getName())) {
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
}
