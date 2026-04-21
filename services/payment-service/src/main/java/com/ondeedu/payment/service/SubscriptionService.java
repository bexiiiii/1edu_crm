package com.ondeedu.payment.service;

import com.ondeedu.common.audit.AuditAction;
import com.ondeedu.common.audit.AuditLogPublisher;
import com.ondeedu.common.audit.TenantAuditEvent;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final String AUTO_COURSE_SUBSCRIPTION_PREFIX = "[AUTO_COURSE_SUBSCRIPTION]";
    private static final String DEFAULT_CURRENCY = "KZT";

    private final SubscriptionRepository subscriptionRepository;
    private final PriceListRepository priceListRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final AuditLogPublisher auditLogPublisher;

    @Transactional
    @CacheEvict(value = "subscriptions", allEntries = true)
    public SubscriptionDto createSubscription(CreateSubscriptionRequest request) {
        Subscription subscription = subscriptionMapper.toEntity(request);
        subscription.setBranchId(resolveCurrentBranchId());

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
        auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                .tenantId(TenantContext.getTenantId())
                .action(AuditAction.SUBSCRIPTION_CREATED)
                .category("FINANCE")
                .actorId(TenantContext.getUserId())
                .targetType("SUBSCRIPTION")
                .targetId(subscription.getId().toString())
                .targetName("Student " + subscription.getStudentId())
                .build());
        return subscriptionMapper.toDto(subscription);
    }

    @Transactional
    @CacheEvict(value = "subscriptions", allEntries = true)
    public SubscriptionDto ensureCourseSubscription(UUID studentId, UUID courseId, String courseName,
                                                    BigDecimal amount, String currency) {
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findByStudentIdAndCourseIdAndStatusOrderByCreatedAtDesc(
                        studentId,
                        courseId,
                        SubscriptionStatus.ACTIVE
                );

        Subscription autoSubscription = activeSubscriptions.stream()
                .filter(this::isAutoCourseSubscription)
                .findFirst()
                .orElse(null);

        if (autoSubscription != null) {
            BigDecimal normalizedAmount = normalizeAmount(amount);
            String normalizedCurrency = normalizeCurrency(currency);
            String notes = buildAutoCourseSubscriptionNote(courseName);
            boolean changed = false;

            if (autoSubscription.getAmount() == null || autoSubscription.getAmount().compareTo(normalizedAmount) != 0) {
                autoSubscription.setAmount(normalizedAmount);
                changed = true;
            }
            if (!normalizedCurrency.equals(autoSubscription.getCurrency())) {
                autoSubscription.setCurrency(normalizedCurrency);
                changed = true;
            }
            if (!notes.equals(autoSubscription.getNotes())) {
                autoSubscription.setNotes(notes);
                changed = true;
            }

            if (changed) {
                autoSubscription = subscriptionRepository.save(autoSubscription);
                log.info("Updated auto course subscription {} for course {}", autoSubscription.getId(), courseId);
            }
            return subscriptionMapper.toDto(autoSubscription);
        }

        if (!activeSubscriptions.isEmpty()) {
            Subscription existingSubscription = activeSubscriptions.getFirst();
            log.info("Skipping auto course subscription for student {} and course {} because active subscription {} already exists",
                    studentId, courseId, existingSubscription.getId());
            return subscriptionMapper.toDto(existingSubscription);
        }

        Subscription subscription = Subscription.builder()
                .studentId(studentId)
                .courseId(courseId)
                .branchId(resolveCurrentBranchId())
                .totalLessons(1)
                .lessonsLeft(1)
                .startDate(LocalDate.now())
                .amount(normalizeAmount(amount))
                .currency(normalizeCurrency(currency))
                .notes(buildAutoCourseSubscriptionNote(courseName))
                .status(SubscriptionStatus.ACTIVE)
                .build();

        subscription = subscriptionRepository.save(subscription);
        log.info("Created auto course subscription {} for student {} and course {}",
                subscription.getId(), studentId, courseId);
        return subscriptionMapper.toDto(subscription);
    }

    @Transactional
    @CacheEvict(value = "subscriptions", allEntries = true)
    public void cancelCourseSubscription(UUID studentId, UUID courseId) {
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findByStudentIdAndCourseIdAndStatusOrderByCreatedAtDesc(
                        studentId,
                        courseId,
                        SubscriptionStatus.ACTIVE
                );

        List<Subscription> autoSubscriptions = activeSubscriptions.stream()
                .filter(this::isAutoCourseSubscription)
                .toList();

        if (autoSubscriptions.isEmpty()) {
            log.debug("No active auto course subscriptions found for student {} and course {}", studentId, courseId);
            return;
        }

        autoSubscriptions.forEach(subscription -> subscription.setStatus(SubscriptionStatus.CANCELLED));
        subscriptionRepository.saveAll(autoSubscriptions);
        log.info("Cancelled {} auto course subscriptions for student {} and course {}",
                autoSubscriptions.size(), studentId, courseId);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "subscriptions", keyGenerator = "tenantCacheKeyGenerator")
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
        UUID branchId = resolveCurrentBranchId();
        Page<Subscription> page;
        if (status != null) {
            page = subscriptionRepository.findByStudentIdAndBranch(studentId, branchId, pageable);
        } else {
            page = subscriptionRepository.findByStudentIdAndBranch(studentId, branchId, pageable);
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
        UUID branchId = resolveCurrentBranchId();
        Page<Subscription> page;
        if (status != null) {
            page = subscriptionRepository.findByStatusAndBranch(status, branchId, pageable);
        } else {
            page = subscriptionRepository.findAllByBranch(branchId, pageable);
        }
        return PageResponse.from(page, subscriptionMapper::toDto);
    }

    private boolean isAutoCourseSubscription(Subscription subscription) {
        return subscription.getNotes() != null
                && subscription.getNotes().startsWith(AUTO_COURSE_SUBSCRIPTION_PREFIX);
    }

    private String buildAutoCourseSubscriptionNote(String courseName) {
        String normalizedCourseName = courseName == null || courseName.isBlank()
                ? "Course"
                : courseName.trim();
        return AUTO_COURSE_SUBSCRIPTION_PREFIX + " " + normalizedCourseName;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private String normalizeCurrency(String currency) {
        return currency != null && !currency.isBlank() ? currency : DEFAULT_CURRENCY;
    }

    private UUID resolveCurrentBranchId() {
        String rawBranchId = TenantContext.getBranchId();
        if (!StringUtils.hasText(rawBranchId)) return null;
        try {
            return UUID.fromString(rawBranchId.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("BRANCH_ID_INVALID", "Invalid branch_id in tenant context");
        }
    }
}
