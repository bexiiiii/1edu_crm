package com.ondeedu.settings.service;

import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.settings.dto.PaymentSourceDto;
import com.ondeedu.settings.dto.SavePaymentSourceRequest;
import com.ondeedu.settings.entity.PaymentSource;
import com.ondeedu.settings.mapper.PaymentSourceMapper;
import com.ondeedu.settings.repository.PaymentSourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public List<PaymentSourceDto> getAll() {
        return repository.findAllByOrderBySortOrderAsc()
                .stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    public PaymentSourceDto create(SavePaymentSourceRequest request) {
        PaymentSource entity = mapper.toEntity(request);
        entity = repository.save(entity);
        log.info("Created payment source: {}", entity.getName());
        return mapper.toDto(entity);
    }

    @Transactional
    public PaymentSourceDto update(UUID id, SavePaymentSourceRequest request) {
        PaymentSource entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentSource", "id", id));
        mapper.updateEntity(entity, request);
        entity = repository.save(entity);
        log.info("Updated payment source: {}", id);
        return mapper.toDto(entity);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("PaymentSource", "id", id);
        }
        repository.deleteById(id);
        log.info("Deleted payment source: {}", id);
    }
}
