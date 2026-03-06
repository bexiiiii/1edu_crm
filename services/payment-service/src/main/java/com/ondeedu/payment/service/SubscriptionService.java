package com.ondeedu.payment.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.payment.dto.CreateSubscriptionRequest;
import com.ondeedu.payment.dto.SubscriptionDto;
import com.ondeedu.payment.dto.UpdateSubscriptionRequest;
import com.ondeedu.payment.entity.PriceList;
import com.ondeedu.payment.entity.Subscription;
import com.ondeedu.payment.entity.SubscriptionStatus;
import com.ondeedu.payment.mapper.SubscriptionMapper;
import com.ondeedu.payment.repository.PriceListRepository;
import com.ondeedu.payment.repository.SubscriptionRepository;
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
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PriceListRepository priceListRepository;
    private final SubscriptionMapper subscriptionMapper;

    @Transactional
    @CacheEvict(value = "subscriptions", allEntries = true)
    public SubscriptionDto createSubscription(CreateSubscriptionRequest request) {
        Subscription subscription = subscriptionMapper.toEntity(request);

        if (request.getPriceListId() != null) {
            PriceList priceList = priceListRepository.findById(request.getPriceListId())
                    .orElseThrow(() -> new ResourceNotFoundException("PriceList", "id", request.getPriceListId()));

            subscription.setTotalLessons(priceList.getLessonsCount());
            subscription.setLessonsLeft(priceList.getLessonsCount());
            subscription.setAmount(priceList.getPrice());
            if (request.getStartDate() != null) {
                subscription.setEndDate(request.getStartDate().plusDays(priceList.getValidityDays()));
            }
        }

        subscription = subscriptionRepository.save(subscription);
        log.info("Created subscription {} for student {}", subscription.getId(), subscription.getStudentId());
        return subscriptionMapper.toDto(subscription);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "subscriptions", key = "#id", keyGenerator = "tenantCacheKeyGenerator")
    public SubscriptionDto getSubscription(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "id", id));
        return subscriptionMapper.toDto(subscription);
    }

    @Transactional
    @CacheEvict(value = "subscriptions", allEntries = true)
    public SubscriptionDto updateSubscription(UUID id, UpdateSubscriptionRequest request) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "id", id));

        subscriptionMapper.updateEntity(subscription, request);
        subscription = subscriptionRepository.save(subscription);

        log.info("Updated subscription {}", id);
        return subscriptionMapper.toDto(subscription);
    }

    @Transactional
    @CacheEvict(value = "subscriptions", allEntries = true)
    public void cancelSubscription(UUID id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "id", id));

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscriptionRepository.save(subscription);
        log.info("Cancelled subscription {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionDto> listByStudent(UUID studentId, SubscriptionStatus status, Pageable pageable) {
        Page<Subscription> page;
        if (status != null) {
            page = subscriptionRepository.findByStudentIdAndStatus(studentId, status, pageable);
        } else {
            page = subscriptionRepository.findByStudentId(studentId, pageable);
        }
        return PageResponse.from(page, subscriptionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionDto> listByCourse(UUID courseId, Pageable pageable) {
        Page<Subscription> page = subscriptionRepository.findByCourseId(courseId, pageable);
        return PageResponse.from(page, subscriptionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionDto> listAll(SubscriptionStatus status, Pageable pageable) {
        Page<Subscription> page;
        if (status != null) {
            page = subscriptionRepository.findByStatus(status, pageable);
        } else {
            page = subscriptionRepository.findAll(pageable);
        }
        return PageResponse.from(page, subscriptionMapper::toDto);
    }
}
