package com.ondeedu.settings.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.settings.dto.AttendanceStatusConfigDto;
import com.ondeedu.settings.dto.SaveAttendanceStatusRequest;
import com.ondeedu.settings.entity.AttendanceStatusConfig;
import com.ondeedu.settings.mapper.AttendanceStatusConfigMapper;
import com.ondeedu.settings.repository.AttendanceStatusConfigRepository;
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
public class AttendanceStatusConfigService {

    private final AttendanceStatusConfigRepository repository;
    private final AttendanceStatusConfigMapper mapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "attendance-statuses", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('all')")
    public List<AttendanceStatusConfigDto> getAll() {
        return repository.findAllByOrderBySortOrderAsc()
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "attendance-statuses", allEntries = true)
    public AttendanceStatusConfigDto create(SaveAttendanceStatusRequest request) {
        AttendanceStatusConfig entity = mapper.toEntity(request);
        entity = repository.save(entity);
        log.info("Created attendance status config: {}", entity.getName());
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "attendance-statuses", allEntries = true)
    public AttendanceStatusConfigDto update(UUID id, SaveAttendanceStatusRequest request) {
        AttendanceStatusConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AttendanceStatusConfig", "id", id));
        mapper.updateEntity(entity, request);
        entity = repository.save(entity);
        log.info("Updated attendance status config: {}", id);
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "attendance-statuses", allEntries = true)
    public void delete(UUID id) {
        AttendanceStatusConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AttendanceStatusConfig", "id", id));
        if (Boolean.TRUE.equals(entity.getSystemStatus())) {
            throw new BusinessException("SYSTEM_STATUS", "Cannot delete a system attendance status");
        }
        repository.deleteById(id);
        log.info("Deleted attendance status config: {}", id);
    }
}
