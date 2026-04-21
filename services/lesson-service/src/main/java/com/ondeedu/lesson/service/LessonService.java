package com.ondeedu.lesson.service;

import com.ondeedu.common.audit.AuditAction;
import com.ondeedu.common.audit.AuditLogPublisher;
import com.ondeedu.common.audit.TenantAuditEvent;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.lesson.dto.CreateLessonRequest;
import com.ondeedu.lesson.dto.LessonDto;
import com.ondeedu.lesson.dto.RescheduleLessonRequest;
import com.ondeedu.lesson.dto.UpdateLessonRequest;
import com.ondeedu.lesson.entity.Lesson;
import com.ondeedu.lesson.entity.LessonStatus;
import com.ondeedu.lesson.entity.LessonType;
import com.ondeedu.lesson.mapper.LessonMapper;
import com.ondeedu.lesson.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonMapper lessonMapper;
    private final AuditLogPublisher auditLogPublisher;

    @Transactional
    @CacheEvict(value = "lessons", allEntries = true)
    public LessonDto createLesson(CreateLessonRequest request) {
        Lesson lesson = lessonMapper.toEntity(request);
        lesson.setBranchId(resolveCurrentBranchId());
        lesson = lessonRepository.save(lesson);
        log.info("Created lesson: {} on {}", lesson.getId(), lesson.getLessonDate());
        auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                .tenantId(TenantContext.getTenantId())
                .action(AuditAction.LESSON_CREATED)
                .category("LESSONS")
                .actorId(TenantContext.getUserId())
                .targetType("LESSON")
                .targetId(lesson.getId().toString())
                .targetName(lesson.getLessonDate().toString())
                .build());
        return lessonMapper.toDto(lesson);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto getLesson(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        enforceTeacherOwnLesson(lesson);
        return lessonMapper.toDto(lesson);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto updateLesson(UUID id, UpdateLessonRequest request) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        enforceTeacherOwnLesson(lesson);

        lessonMapper.updateEntity(lesson, request);
        lesson = lessonRepository.save(lesson);

        log.info("Updated lesson: {}", id);
        auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                .tenantId(TenantContext.getTenantId())
                .action(AuditAction.LESSON_UPDATED)
                .category("LESSONS")
                .actorId(TenantContext.getUserId())
                .targetType("LESSON")
                .targetId(id.toString())
                .build());
        return lessonMapper.toDto(lesson);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public void deleteLesson(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        enforceTeacherOwnLesson(lesson);

        if (!lessonRepository.existsById(id)) {
            throw new ResourceNotFoundException("Lesson", "id", id);
        }
        lessonRepository.deleteById(id);
        log.info("Deleted lesson: {}", id);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto completeLesson(UUID id, String topic, String homework) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        enforceTeacherOwnLesson(lesson);

        lesson.setStatus(LessonStatus.COMPLETED);
        if (topic != null) {
            lesson.setTopic(topic);
        }
        if (homework != null) {
            lesson.setHomework(homework);
        }
        lesson = lessonRepository.save(lesson);

        log.info("Completed lesson: {}", id);
        auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                .tenantId(TenantContext.getTenantId())
                .action(AuditAction.LESSON_COMPLETED)
                .category("LESSONS")
                .actorId(TenantContext.getUserId())
                .targetType("LESSON")
                .targetId(id.toString())
                .build());
        return lessonMapper.toDto(lesson);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto cancelLesson(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        enforceTeacherOwnLesson(lesson);

        lesson.setStatus(LessonStatus.CANCELLED);
        lesson = lessonRepository.save(lesson);

        log.info("Cancelled lesson: {}", id);
        auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                .tenantId(TenantContext.getTenantId())
                .action(AuditAction.LESSON_CANCELLED)
                .category("LESSONS")
                .actorId(TenantContext.getUserId())
                .targetType("LESSON")
                .targetId(id.toString())
                .build());
        return lessonMapper.toDto(lesson);
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonDto> listLessons(LessonType type, LessonStatus status, LocalDate date,
                                                LocalDate from, LocalDate to, Pageable pageable) {
        UUID branchId = resolveCurrentBranchId();
        Page<Lesson> page;

        boolean hasDateRange = from != null && to != null;

        if (isTeacherUser()) {
            UUID teacherId = resolveCurrentTeacherStaffId();
            if (type != null && hasDateRange) {
                page = lessonRepository.findByTeacherIdAndLessonTypeAndLessonDateBetween(
                        teacherId,
                        type,
                        from,
                        to,
                        pageable
                );
            } else if (status != null && hasDateRange) {
                page = lessonRepository.findByTeacherIdAndStatusAndLessonDateBetween(
                        teacherId,
                        status,
                        from,
                        to,
                        pageable
                );
            } else if (hasDateRange) {
                page = lessonRepository.findByTeacherIdAndLessonDateBetween(teacherId, from, to, pageable);
            } else if (type != null) {
                page = lessonRepository.findByTeacherIdAndLessonType(teacherId, type, pageable);
            } else if (status != null) {
                page = lessonRepository.findByTeacherIdAndStatus(teacherId, status, pageable);
            } else if (date != null) {
                page = lessonRepository.findByTeacherIdAndLessonDate(teacherId, date, pageable);
            } else {
                page = lessonRepository.findByTeacherIdAndBranch(teacherId, branchId, pageable);
            }

            return PageResponse.from(page, lessonMapper::toDto);
        }

        if (type != null && hasDateRange) {
            page = lessonRepository.findByLessonTypeAndLessonDateBetween(type, from, to, pageable);
        } else if (status != null && hasDateRange) {
            page = lessonRepository.findByStatusAndLessonDateBetween(status, from, to, pageable);
        } else if (hasDateRange) {
            page = lessonRepository.findByLessonDateBetween(from, to, pageable);
        } else if (type != null) {
            page = lessonRepository.findByLessonType(type, pageable);
        } else if (status != null) {
            page = lessonRepository.findByStatus(status, pageable);
        } else if (date != null) {
            page = lessonRepository.findByLessonDateAndBranch(date, branchId, pageable);
        } else {
            page = lessonRepository.findAllByBranch(branchId, pageable);
        }

        return PageResponse.from(page, lessonMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonDto> listByGroup(UUID groupId, LocalDate from, LocalDate to, Pageable pageable) {
        UUID branchId = resolveCurrentBranchId();
        Page<Lesson> page;

        if (isTeacherUser()) {
            UUID teacherId = resolveCurrentTeacherStaffId();
            if (from != null && to != null) {
                page = lessonRepository.findByGroupIdAndTeacherIdAndLessonDateBetween(
                        groupId,
                        teacherId,
                        from,
                        to,
                        pageable
                );
            } else {
                page = lessonRepository.findByGroupIdAndTeacherId(groupId, teacherId, pageable);
            }
        } else if (from != null && to != null) {
            page = lessonRepository.findByGroupIdAndLessonDateBetween(groupId, from, to, pageable);
        } else {
            page = lessonRepository.findByGroupIdAndBranch(groupId, branchId, pageable);
        }

        return PageResponse.from(page, lessonMapper::toDto);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto markTeacherAbsent(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        enforceTeacherOwnLesson(lesson);
        lesson.setStatus(LessonStatus.TEACHER_ABSENT);
        lesson = lessonRepository.save(lesson);
        log.info("Marked lesson {} as teacher absent", id);
        return lessonMapper.toDto(lesson);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto markTeacherSick(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        enforceTeacherOwnLesson(lesson);
        lesson.setStatus(LessonStatus.TEACHER_SICK);
        lesson = lessonRepository.save(lesson);
        log.info("Marked lesson {} as teacher sick", id);
        return lessonMapper.toDto(lesson);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto rescheduleLesson(UUID id, RescheduleLessonRequest request) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        enforceTeacherOwnLesson(lesson);
        lesson.setLessonDate(request.getNewDate());
        lesson.setStartTime(request.getNewStartTime());
        lesson.setEndTime(request.getNewEndTime());
        lesson.setStatus(LessonStatus.PLANNED);
        lesson = lessonRepository.save(lesson);
        log.info("Rescheduled lesson {} to {}", id, request.getNewDate());
        return lessonMapper.toDto(lesson);
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonDto> listByTeacher(UUID teacherId, Pageable pageable) {
        if (isTeacherUser() && !resolveCurrentTeacherStaffId().equals(teacherId)) {
            throw new BusinessException(
                    "TEACHER_SCOPE_DENIED",
                    "Teacher can access only own lessons",
                    HttpStatus.FORBIDDEN
            );
        }
        Page<Lesson> page = lessonRepository.findByTeacherIdAndBranch(teacherId, resolveCurrentBranchId(), pageable);
        return PageResponse.from(page, lessonMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<LessonDto> listCalendar(LocalDate from, LocalDate to) {
        List<Lesson> lessons;
        if (isTeacherUser()) {
            lessons = lessonRepository.findByTeacherIdAndLessonDateBetweenOrderByLessonDateAscStartTimeAsc(
                    resolveCurrentTeacherStaffId(),
                    from,
                    to
            );
        } else {
            lessons = lessonRepository.findByLessonDateBetweenOrderByLessonDateAscStartTimeAsc(from, to);
        }

        return lessons
                .stream()
                .map(lessonMapper::toDto)
                .collect(Collectors.toList());
    }

    private void enforceTeacherOwnLesson(Lesson lesson) {
        if (!isTeacherUser()) {
            return;
        }

        UUID currentTeacherId = resolveCurrentTeacherStaffId();
        if (!currentTeacherId.equals(lesson.getTeacherId())) {
            throw new BusinessException(
                    "TEACHER_SCOPE_DENIED",
                    "Teacher can access only own lessons",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private boolean isTeacherUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_TEACHER".equals(authority.getAuthority()));
    }

    private UUID resolveCurrentTeacherStaffId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new BusinessException(
                    "TEACHER_STAFF_ID_REQUIRED",
                    "Teacher profile is not linked to staff account",
                    HttpStatus.FORBIDDEN
            );
        }

        String staffId = jwt.getClaimAsString("staff_id");
        if (!StringUtils.hasText(staffId)) {
            throw new BusinessException(
                    "TEACHER_STAFF_ID_REQUIRED",
                    "Teacher profile is not linked to staff account",
                    HttpStatus.FORBIDDEN
            );
        }

        try {
            return UUID.fromString(staffId);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(
                    "TEACHER_STAFF_ID_INVALID",
                    "Teacher staff linkage is invalid",
                    HttpStatus.FORBIDDEN
            );
        }
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
