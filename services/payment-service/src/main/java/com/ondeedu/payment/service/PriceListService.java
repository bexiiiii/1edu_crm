package com.ondeedu.payment.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.payment.dto.CreatePriceListRequest;
import com.ondeedu.payment.dto.PriceListDto;
import com.ondeedu.payment.dto.UpdatePriceListRequest;
import com.ondeedu.payment.entity.PriceList;
import com.ondeedu.payment.mapper.PriceListMapper;
import com.ondeedu.payment.repository.PriceListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceListService {

    private final PriceListRepository priceListRepository;
    private final PriceListMapper priceListMapper;

    @Transactional
    @CacheEvict(value = "price-lists", allEntries = true)
    public PriceListDto createPriceList(CreatePriceListRequest request) {
        PriceList priceList = priceListMapper.toEntity(request);

        if (request.getIsActive() != null) {
            priceList.setActive(request.getIsActive());
        }

        priceList = priceListRepository.save(priceList);
        log.info("Created price list: {}", priceList.getName());
        return priceListMapper.toDto(priceList);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "price-lists", key = "#id", keyGenerator = "tenantCacheKeyGenerator")
    public PriceListDto getPriceList(UUID id) {
        PriceList priceList = priceListRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PriceList", "id", id));
        return priceListMapper.toDto(priceList);
    }

    @Transactional
    @CacheEvict(value = "price-lists", allEntries = true)
    public PriceListDto updatePriceList(UUID id, UpdatePriceListRequest request) {
        PriceList priceList = priceListRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PriceList", "id", id));

        priceListMapper.updateEntity(priceList, request);

        if (request.getIsActive() != null) {
            priceList.setActive(request.getIsActive());
        }

        priceList = priceListRepository.save(priceList);
        log.info("Updated price list {}", id);
        return priceListMapper.toDto(priceList);
    }

    @Transactional
    @CacheEvict(value = "price-lists", allEntries = true)
    public void deletePriceList(UUID id) {
        if (!priceListRepository.existsById(id)) {
            throw new ResourceNotFoundException("PriceList", "id", id);
        }
        priceListRepository.deleteById(id);
        log.info("Deleted price list {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<PriceListDto> list(Boolean isActive, Pageable pageable) {
        Page<PriceList> page;
        if (isActive != null) {
            page = priceListRepository.findByIsActive(isActive, pageable);
        } else {
            page = priceListRepository.findAll(pageable);
        }
        return PageResponse.from(page, priceListMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<PriceListDto> listByCourse(UUID courseId, Pageable pageable) {
        Page<PriceList> page = priceListRepository.findByCourseId(courseId, pageable);
        return PageResponse.from(page, priceListMapper::toDto);
    }
}
