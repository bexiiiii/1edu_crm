package com.ondeedu.settings.service;

import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.settings.dto.PaymentSourceDto;
import com.ondeedu.settings.dto.SavePaymentSourceRequest;
import com.ondeedu.settings.entity.PaymentSource;
import com.ondeedu.settings.mapper.PaymentSourceMapper;
import com.ondeedu.settings.repository.PaymentSourceRepository;
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
public class PaymentSourceService {

    private final PaymentSourceRepository repository;
    private final PaymentSourceMapper mapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "payment-sources", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('all') + '::branch=' + T(com.ondeedu.common.tenant.TenantContext).getBranchId()")
    public List<PaymentSourceDto> getAll() {
        UUID branchId = resolveCurrentBranchId();
        return repository.findAllByBranchOrderBySortOrderAsc(branchId)
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "payment-sources", allEntries = true)
    public PaymentSourceDto create(SavePaymentSourceRequest request) {
        PaymentSource entity = mapper.toEntity(request);
        entity.setBranchId(resolveCurrentBranchId());
        entity = repository.save(entity);
        log.info("Created payment source: {}", entity.getName());
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "payment-sources", allEntries = true)
    public PaymentSourceDto update(UUID id, SavePaymentSourceRequest request) {
        PaymentSource entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentSource", "id", id));
        mapper.updateEntity(entity, request);
        entity = repository.save(entity);
        log.info("Updated payment source: {}", id);
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "payment-sources", allEntries = true)
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("PaymentSource", "id", id);
        }
        repository.deleteById(id);
        log.info("Deleted payment source: {}", id);
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
