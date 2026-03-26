package com.ondeedu.settings.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.settings.dto.FinanceCategoryConfigDto;
import com.ondeedu.settings.dto.SaveFinanceCategoryRequest;
import com.ondeedu.settings.entity.FinanceCategoryConfig;
import com.ondeedu.settings.entity.FinanceCategoryType;
import com.ondeedu.settings.mapper.FinanceCategoryConfigMapper;
import com.ondeedu.settings.repository.FinanceCategoryConfigRepository;
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
public class FinanceCategoryConfigService {

    private final FinanceCategoryConfigRepository repository;
    private final FinanceCategoryConfigMapper mapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "finance-categories", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed(#type.name())")
    public List<FinanceCategoryConfigDto> getAll(FinanceCategoryType type) {
        return repository.findAllByTypeOrderBySortOrderAscNameAsc(type)
                .stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "finance-categories", allEntries = true)
    public FinanceCategoryConfigDto create(FinanceCategoryType type, SaveFinanceCategoryRequest request) {
        if (repository.existsByTypeAndNameIgnoreCase(type, request.getName())) {
            throw new BusinessException("DUPLICATE_FINANCE_CATEGORY",
                    type + " category with name '" + request.getName() + "' already exists");
        }

        FinanceCategoryConfig entity = mapper.toEntity(request);
        entity.setType(type);
        entity = repository.save(entity);
        log.info("Created finance category config: {} [{}]", entity.getName(), type);
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "finance-categories", allEntries = true)
    public FinanceCategoryConfigDto update(FinanceCategoryType type, UUID id, SaveFinanceCategoryRequest request) {
        FinanceCategoryConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinanceCategoryConfig", "id", id));

        if (entity.getType() != type) {
            throw new BusinessException("FINANCE_CATEGORY_TYPE_MISMATCH",
                    "Category belongs to " + entity.getType() + ", not " + type);
        }

        if (!entity.getName().equalsIgnoreCase(request.getName())
                && repository.existsByTypeAndNameIgnoreCase(type, request.getName())) {
            throw new BusinessException("DUPLICATE_FINANCE_CATEGORY",
                    type + " category with name '" + request.getName() + "' already exists");
        }

        mapper.updateEntity(entity, request);
        entity = repository.save(entity);
        log.info("Updated finance category config: {} [{}]", id, type);
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "finance-categories", allEntries = true)
    public void delete(FinanceCategoryType type, UUID id) {
        FinanceCategoryConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FinanceCategoryConfig", "id", id));

        if (entity.getType() != type) {
            throw new BusinessException("FINANCE_CATEGORY_TYPE_MISMATCH",
                    "Category belongs to " + entity.getType() + ", not " + type);
        }

        repository.delete(entity);
        log.info("Deleted finance category config: {} [{}]", id, type);
    }
}
