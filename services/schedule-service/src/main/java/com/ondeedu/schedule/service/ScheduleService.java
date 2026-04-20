package com.ondeedu.schedule.service;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.event.AssignmentNotificationEvent;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.schedule.client.CourseGrpcClient;
import com.ondeedu.schedule.client.LessonGrpcClient;
import com.ondeedu.schedule.client.LessonGrpcClient.ManagedLesson;
import com.ondeedu.schedule.dto.CreateScheduleRequest;
import com.ondeedu.schedule.dto.ScheduleDto;
import com.ondeedu.schedule.dto.UpdateScheduleRequest;
import com.ondeedu.schedule.entity.Room;
import com.ondeedu.schedule.entity.ScheduleStudentEnrollment;
import com.ondeedu.schedule.entity.Schedule;
import com.ondeedu.schedule.entity.ScheduleStatus;
import com.ondeedu.schedule.mapper.ScheduleMapper;
import com.ondeedu.schedule.repository.CourseStudentLinkRepository;
import com.ondeedu.schedule.repository.RoomRepository;
import com.ondeedu.schedule.repository.ScheduleRepository;
import com.ondeedu.schedule.repository.ScheduleStudentEnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final String ACTIVE_ENROLLMENT_STATUS = "ACTIVE";
    private static final String DROPPED_ENROLLMENT_STATUS = "DROPPED";
    private static final String AUTO_FROM_COURSE_NOTE_PREFIX = "AUTO_FROM_COURSE:";

    private final ScheduleRepository scheduleRepository;
    private final ScheduleMapper scheduleMapper;
    private final LessonGrpcClient lessonGrpcClient;
    private final CourseStudentLinkRepository courseStudentLinkRepository;
    private final ScheduleStudentEnrollmentRepository scheduleStudentEnrollmentRepository;
    private final CourseGrpcClient courseGrpcClient;
    private final RoomRepository roomRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ScheduleSettingsConstraintsService scheduleSettingsConstraintsService;

    @Transactional
    @CacheEvict(value = "schedules", allEntries = true)
    public ScheduleDto createSchedule(CreateScheduleRequest request) {
        validateScheduleRange(request.getStartDate(), request.getEndDate());
        Schedule schedule = scheduleMapper.toEntity(request);

        // Auto-populate teacherId and maxStudents from course — they cannot be overridden manually
        if (request.getCourseId() != null) {
            applyCourseDerivedFields(schedule, request.getCourseId());
        }

        scheduleSettingsConstraintsService.validate(schedule);
        validateRoomTimeConflict(schedule, null);

        // Validate room capacity: block if maxStudents > capacity, notify if at capacity
        if (schedule.getRoomId() != null) {
            checkRoomCapacity(schedule.getRoomId(), schedule.getMaxStudents(), schedule.getName());
        }

        schedule = scheduleRepository.saveAndFlush(schedule);

        synchronizeCourseStudents(schedule);
        synchronizeLessonsOnCreate(schedule);

        log.info("Created schedule: {} ({})", schedule.getName(), schedule.getId());
        return scheduleMapper.toDto(schedule);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "schedules", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public ScheduleDto getSchedule(UUID id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", "id", id));
        enforceTeacherOwnSchedule(schedule);
        return scheduleMapper.toDto(schedule);
    }

    @Transactional
    @CacheEvict(value = "schedules", allEntries = true)
    public ScheduleDto updateSchedule(UUID id, UpdateScheduleRequest request) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", "id", id));
        enforceTeacherOwnSchedule(schedule);

        // Determine effective courseId after the update
        UUID effectiveCourseId = request.getCourseId() != null ? request.getCourseId() : schedule.getCourseId();

        // teacherId and maxStudents are managed by the course — block manual changes when courseId is set
        if (effectiveCourseId != null) {
            if (request.getTeacherId() != null || request.getMaxStudents() != null) {
                throw new BusinessException(
                        "COURSE_BOUND_FIELD_IMMUTABLE",
                        "teacherId and maxStudents are automatically set from the course and cannot be changed directly"
                );
            }
        }

        List<ManagedLesson> existingLessons = lessonGrpcClient.listGroupLessons(id);
        scheduleMapper.updateEntity(schedule, request);

        // Re-apply course-derived fields if courseId changed or is still set
        if (effectiveCourseId != null) {
            applyCourseDerivedFields(schedule, effectiveCourseId);
        }

        scheduleSettingsConstraintsService.validate(schedule);

        validateScheduleRange(schedule.getStartDate(), schedule.getEndDate());
        validateRoomTimeConflict(schedule, id);

        // Validate room capacity after applying all changes
        if (schedule.getRoomId() != null) {
            checkRoomCapacity(schedule.getRoomId(), schedule.getMaxStudents(), schedule.getName());
        }

        schedule = scheduleRepository.saveAndFlush(schedule);

        synchronizeCourseStudents(schedule);
        synchronizeLessonsOnUpdate(schedule, existingLessons);

        log.info("Updated schedule: {}", id);
        return scheduleMapper.toDto(schedule);
    }

    @Transactional
    @CacheEvict(value = "schedules", allEntries = true)
    public void deleteSchedule(UUID id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", "id", id));
        enforceTeacherOwnSchedule(schedule);
        List<ManagedLesson> existingLessons = lessonGrpcClient.listGroupLessons(id);

        synchronizeLessonsOnDelete(id, existingLessons);
        closeGroupEnrollments(id);

        try {
            scheduleRepository.delete(schedule);
            scheduleRepository.flush();
        } catch (RuntimeException e) {
            restoreDeletedLessons(id, existingLessons);
            throw e;
        }

        log.info("Deleted schedule: {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScheduleDto> listSchedules(ScheduleStatus status, UUID courseId, UUID teacherId, Pageable pageable) {
        Page<Schedule> page;
        if (isTeacherUser()) {
            UUID currentTeacherId = resolveCurrentTeacherStaffId();
            if (status != null) {
                page = scheduleRepository.findByStatusAndTeacherId(status, currentTeacherId, pageable);
            } else if (courseId != null) {
                page = scheduleRepository.findByCourseIdAndTeacherId(courseId, currentTeacherId, pageable);
            } else {
                page = scheduleRepository.findByTeacherId(currentTeacherId, pageable);
            }
        } else if (status != null) {
            page = scheduleRepository.findByStatus(status, pageable);
        } else if (courseId != null) {
            page = scheduleRepository.findByCourseId(courseId, pageable);
        } else if (teacherId != null) {
            page = scheduleRepository.findByTeacherId(teacherId, pageable);
        } else {
            page = scheduleRepository.findAll(pageable);
        }
        return PageResponse.from(page, scheduleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScheduleDto> getSchedulesByRoom(UUID roomId, Pageable pageable) {
        Page<Schedule> page;
        if (isTeacherUser()) {
            page = scheduleRepository.findByRoomIdAndTeacherId(roomId, resolveCurrentTeacherStaffId(), pageable);
        } else {
            page = scheduleRepository.findByRoomId(roomId, pageable);
        }
        return PageResponse.from(page, scheduleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScheduleDto> searchSchedules(String query, Pageable pageable) {
        Page<Schedule> page;
        if (isTeacherUser()) {
            page = scheduleRepository.searchByTeacherId(query, resolveCurrentTeacherStaffId(), pageable);
        } else {
            page = scheduleRepository.search(query, pageable);
        }
        return PageResponse.from(page, scheduleMapper::toDto);
    }

    private void synchronizeCourseStudents(Schedule schedule) {
        if (schedule.getCourseId() == null) {
            dropAutoCourseEnrollments(schedule.getId());
            return;
        }

        List<UUID> courseStudentIds = courseStudentLinkRepository
                .findStudentIdsByCourseIdOrderByCreatedAtAsc(schedule.getCourseId());
        Set<UUID> targetStudentIds = new LinkedHashSet<>(courseStudentIds);
        List<ScheduleStudentEnrollment> groupEnrollments =
                scheduleStudentEnrollmentRepository.findByGroupId(schedule.getId());

        Map<UUID, ScheduleStudentEnrollment> autoEnrollmentsByStudentId = groupEnrollments.stream()
                .filter(this::isAutoCourseEnrollment)
                .collect(Collectors.toMap(
                        ScheduleStudentEnrollment::getStudentId,
                        enrollment -> enrollment,
                        (left, right) -> Comparator
                                .comparing((ScheduleStudentEnrollment enrollment) -> isActive(enrollment) ? 0 : 1)
                                .thenComparing(ScheduleStudentEnrollment::getCreatedAt,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .compare(left, right) <= 0 ? left : right
                ));

        Set<UUID> activeStudentIds = groupEnrollments.stream()
                .filter(this::isActive)
                .map(ScheduleStudentEnrollment::getStudentId)
                .collect(Collectors.toCollection(HashSet::new));

        Instant now = Instant.now();

        autoEnrollmentsByStudentId.forEach((studentId, enrollment) -> {
            if (!targetStudentIds.contains(studentId) && isActive(enrollment)) {
                markDropped(enrollment, now);
                activeStudentIds.remove(studentId);
            }
        });

        for (UUID studentId : targetStudentIds) {
            ScheduleStudentEnrollment autoEnrollment = autoEnrollmentsByStudentId.get(studentId);
            if (autoEnrollment != null) {
                reactivateAutoEnrollment(autoEnrollment, schedule.getCourseId(), now);
                activeStudentIds.add(studentId);
                continue;
            }

            if (activeStudentIds.contains(studentId)) {
                continue;
            }

            scheduleStudentEnrollmentRepository.save(ScheduleStudentEnrollment.builder()
                    .studentId(studentId)
                    .groupId(schedule.getId())
                    .status(ACTIVE_ENROLLMENT_STATUS)
                    .enrolledAt(now)
                    .notes(autoCourseNote(schedule.getCourseId()))
                    .build());
            activeStudentIds.add(studentId);
        }
    }

    private void dropAutoCourseEnrollments(UUID groupId) {
        Instant now = Instant.now();
        scheduleStudentEnrollmentRepository.findByGroupIdAndNotesStartingWith(groupId, AUTO_FROM_COURSE_NOTE_PREFIX)
                .forEach(enrollment -> {
                    if (isActive(enrollment)) {
                        markDropped(enrollment, now);
                    }
                });
    }

    private void closeGroupEnrollments(UUID groupId) {
        Instant now = Instant.now();
        scheduleStudentEnrollmentRepository.findByGroupIdAndStatus(groupId, ACTIVE_ENROLLMENT_STATUS)
                .forEach(enrollment -> markDropped(enrollment, now));
    }

    private void reactivateAutoEnrollment(ScheduleStudentEnrollment enrollment, UUID courseId, Instant now) {
        enrollment.setNotes(autoCourseNote(courseId));
        if (!isActive(enrollment)) {
            enrollment.setStatus(ACTIVE_ENROLLMENT_STATUS);
            enrollment.setCompletedAt(null);
            if (enrollment.getEnrolledAt() == null) {
                enrollment.setEnrolledAt(now);
            }
        }
    }

    private void markDropped(ScheduleStudentEnrollment enrollment, Instant now) {
        enrollment.setStatus(DROPPED_ENROLLMENT_STATUS);
        enrollment.setCompletedAt(now);
    }

    private boolean isActive(ScheduleStudentEnrollment enrollment) {
        return Objects.equals(ACTIVE_ENROLLMENT_STATUS, enrollment.getStatus());
    }

    private boolean isAutoCourseEnrollment(ScheduleStudentEnrollment enrollment) {
        return enrollment.getNotes() != null
                && enrollment.getNotes().startsWith(AUTO_FROM_COURSE_NOTE_PREFIX);
    }

    private String autoCourseNote(UUID courseId) {
        return AUTO_FROM_COURSE_NOTE_PREFIX + courseId;
    }

    private void synchronizeLessonsOnCreate(Schedule schedule) {
        List<Runnable> rollbackActions = new ArrayList<>();

        try {
            for (LocalDate lessonDate : resolveLessonDates(schedule)) {
                UUID lessonId = lessonGrpcClient.createGroupLesson(schedule, lessonDate);
                rollbackActions.add(() -> rollbackIgnoringErrors(
                        () -> lessonGrpcClient.deleteLesson(lessonId),
                        () -> "Failed to rollback lesson " + lessonId + " for schedule " + schedule.getId()
                ));
            }
        } catch (RuntimeException e) {
            rollbackSyncOperations(rollbackActions);
            throw e;
        }
    }

    private void synchronizeLessonsOnUpdate(Schedule schedule, List<ManagedLesson> existingLessons) {
        Set<LocalDate> desiredDates = new LinkedHashSet<>(resolveLessonDates(schedule));
        Map<LocalDate, List<ManagedLesson>> lessonsByDate = existingLessons.stream()
                .collect(Collectors.groupingBy(ManagedLesson::lessonDate));

        List<ManagedLesson> blockedLessons = existingLessons.stream()
                .filter(lesson -> !lesson.isPlanned())
                .filter(lesson -> !desiredDates.contains(lesson.lessonDate()))
                .toList();
        if (!blockedLessons.isEmpty()) {
            throw new BusinessException(
                    "SCHEDULE_UPDATE_BLOCKED",
                    "Schedule update would remove lessons with existing history: " + formatLessons(blockedLessons)
            );
        }

        List<Runnable> rollbackActions = new ArrayList<>();
        Set<UUID> retainedLessonIds = new HashSet<>();
        Set<LocalDate> coveredDates = new HashSet<>();

        try {
            for (LocalDate desiredDate : desiredDates) {
                ManagedLesson retainedLesson = pickRetainedLesson(lessonsByDate.get(desiredDate));
                if (retainedLesson == null) {
                    continue;
                }

                retainedLessonIds.add(retainedLesson.id());
                coveredDates.add(desiredDate);

                if (retainedLesson.isPlanned()) {
                    ManagedLesson snapshot = retainedLesson;
                    lessonGrpcClient.updateGroupLesson(retainedLesson.id(), schedule, desiredDate);
                    rollbackActions.add(() -> rollbackIgnoringErrors(
                            () -> lessonGrpcClient.restoreLesson(snapshot.id(), snapshot, schedule.getId()),
                            () -> "Failed to rollback lesson update " + snapshot.id() + " for schedule " + schedule.getId()
                    ));
                }
            }

            for (ManagedLesson lesson : existingLessons) {
                if (!lesson.isPlanned() || retainedLessonIds.contains(lesson.id())) {
                    continue;
                }

                lessonGrpcClient.deleteLesson(lesson.id());
                rollbackActions.add(() -> rollbackIgnoringErrors(
                        () -> lessonGrpcClient.restoreGroupLesson(schedule.getId(), lesson),
                        () -> "Failed to rollback lesson delete " + lesson.id() + " for schedule " + schedule.getId()
                ));
            }

            for (LocalDate desiredDate : desiredDates) {
                if (coveredDates.contains(desiredDate)) {
                    continue;
                }

                UUID createdLessonId = lessonGrpcClient.createGroupLesson(schedule, desiredDate);
                rollbackActions.add(() -> rollbackIgnoringErrors(
                        () -> lessonGrpcClient.deleteLesson(createdLessonId),
                        () -> "Failed to rollback created lesson " + createdLessonId + " for schedule " + schedule.getId()
                ));
            }
        } catch (RuntimeException e) {
            rollbackSyncOperations(rollbackActions);
            throw e;
        }
    }

    private void synchronizeLessonsOnDelete(UUID scheduleId, List<ManagedLesson> existingLessons) {
        List<ManagedLesson> blockedLessons = existingLessons.stream()
                .filter(lesson -> !lesson.isPlanned())
                .toList();
        if (!blockedLessons.isEmpty()) {
            throw new BusinessException(
                    "SCHEDULE_DELETE_BLOCKED",
                    "Schedule cannot be deleted because lessons already have history: " + formatLessons(blockedLessons)
            );
        }

        List<Runnable> rollbackActions = new ArrayList<>();

        try {
            for (ManagedLesson lesson : existingLessons) {
                lessonGrpcClient.deleteLesson(lesson.id());
                rollbackActions.add(() -> rollbackIgnoringErrors(
                        () -> lessonGrpcClient.restoreGroupLesson(scheduleId, lesson),
                        () -> "Failed to rollback lesson delete " + lesson.id() + " for schedule " + scheduleId
                ));
            }
        } catch (RuntimeException e) {
            rollbackSyncOperations(rollbackActions);
            throw e;
        }
    }

    private void restoreDeletedLessons(UUID scheduleId, List<ManagedLesson> existingLessons) {
        existingLessons.stream()
                .filter(ManagedLesson::isPlanned)
                .forEach(lesson -> rollbackIgnoringErrors(
                        () -> lessonGrpcClient.restoreGroupLesson(scheduleId, lesson),
                        () -> "Failed to restore lesson " + lesson.id() + " after schedule delete rollback"
                ));
    }

    private void rollbackSyncOperations(List<Runnable> rollbackActions) {
        for (int i = rollbackActions.size() - 1; i >= 0; i--) {
            rollbackActions.get(i).run();
        }
    }

    private void rollbackIgnoringErrors(Runnable action, Supplier<String> messageSupplier) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.error(messageSupplier.get(), ex);
        }
    }

    private ManagedLesson pickRetainedLesson(List<ManagedLesson> lessonsOnDate) {
        if (lessonsOnDate == null || lessonsOnDate.isEmpty()) {
            return null;
        }

        return lessonsOnDate.stream()
                .filter(lesson -> !lesson.isPlanned())
                .findFirst()
                .orElse(lessonsOnDate.getFirst());
    }

    private String formatLessons(List<ManagedLesson> lessons) {
        return lessons.stream()
                .map(lesson -> lesson.lessonDate() + " [" + lesson.status() + "]")
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private void applyCourseDerivedFields(Schedule schedule, UUID courseId) {
        courseGrpcClient.getCourseInfo(courseId).ifPresent(info -> {
            if (info.teacherId() != null) {
                schedule.setTeacherId(info.teacherId());
            }
            if (info.enrollmentLimit() != null) {
                schedule.setMaxStudents(info.enrollmentLimit());
            }
        });
    }

    private void checkRoomCapacity(UUID roomId, Integer maxStudents, String scheduleName) {
        if (maxStudents == null) return;
        roomRepository.findById(roomId).ifPresent(room -> {
            if (room.getCapacity() == null) return;
            if (maxStudents > room.getCapacity()) {
                throw new BusinessException(
                        "ROOM_CAPACITY_EXCEEDED",
                        "Количество учеников (" + maxStudents + ") превышает вместимость аудитории '"
                                + room.getName() + "' (" + room.getCapacity() + " мест)"
                );
            }
            if (maxStudents.equals(room.getCapacity())) {
                publishRoomAtCapacityNotification(room, scheduleName, maxStudents);
            }
        });
    }

    private void validateRoomTimeConflict(Schedule candidate, UUID excludeScheduleId) {
        if (candidate.getStatus() != null && candidate.getStatus() != ScheduleStatus.ACTIVE) {
            return;
        }
        if (candidate.getRoomId() == null
                || candidate.getStartDate() == null
                || candidate.getStartTime() == null
                || candidate.getEndTime() == null) {
            return;
        }

        Set<DayOfWeek> candidateDays = resolveEffectiveDays(candidate);
        if (candidateDays.isEmpty()) {
            return;
        }

        LocalDate candidateRangeStart = candidate.getStartDate();
        LocalDate candidateRangeEnd = resolveEffectiveRangeEnd(candidate);

        List<Schedule> roomSchedules = scheduleRepository.findByRoomIdAndStatus(
                candidate.getRoomId(),
                ScheduleStatus.ACTIVE
        );

        for (Schedule existing : roomSchedules) {
            if (excludeScheduleId != null && existing.getId().equals(excludeScheduleId)) {
                continue;
            }

            if (!dateRangesOverlap(
                    candidateRangeStart,
                    candidateRangeEnd,
                    existing.getStartDate(),
                    resolveEffectiveRangeEnd(existing)
            )) {
                continue;
            }

            if (!daysOverlap(candidateDays, resolveEffectiveDays(existing))) {
                continue;
            }

            if (!timeRangesOverlap(
                    candidate.getStartTime(),
                    candidate.getEndTime(),
                    existing.getStartTime(),
                    existing.getEndTime()
            )) {
                continue;
            }

            throw new BusinessException(
                    "SCHEDULE_ROOM_TIME_CONFLICT",
                    "Room is already occupied at this time by schedule '" + existing.getName() + "'"
            );
        }
    }

    private Set<DayOfWeek> resolveEffectiveDays(Schedule schedule) {
        Set<DayOfWeek> days = schedule.getDaysOfWeek();
        if (days == null || days.isEmpty()) {
            return Set.of(schedule.getStartDate().getDayOfWeek());
        }
        return days;
    }

    private LocalDate resolveEffectiveRangeEnd(Schedule schedule) {
        Set<DayOfWeek> days = schedule.getDaysOfWeek();
        if (days == null || days.isEmpty()) {
            return schedule.getStartDate();
        }
        return schedule.getEndDate() != null ? schedule.getEndDate() : LocalDate.MAX;
    }

    private boolean dateRangesOverlap(LocalDate startA, LocalDate endA, LocalDate startB, LocalDate endB) {
        return !endA.isBefore(startB) && !endB.isBefore(startA);
    }

    private boolean daysOverlap(Set<DayOfWeek> daysA, Set<DayOfWeek> daysB) {
        return daysA.stream().anyMatch(daysB::contains);
    }

    private boolean timeRangesOverlap(
            java.time.LocalTime startA,
            java.time.LocalTime endA,
            java.time.LocalTime startB,
            java.time.LocalTime endB
    ) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }

    private void publishRoomAtCapacityNotification(Room room, String scheduleName, int maxStudents) {
        try {
            String tenantId = TenantContext.getTenantId();
            String userId = TenantContext.getUserId();
            UUID creatorId = null;
            try {
                if (userId != null) creatorId = UUID.fromString(userId);
            } catch (IllegalArgumentException ignored) {}

            AssignmentNotificationEvent event = new AssignmentNotificationEvent(
                    "ROOM_AT_CAPACITY",
                    tenantId,
                    userId,
                    creatorId,
                    null,
                    null,
                    "SCHEDULE",
                    room.getId(),
                    scheduleName,
                    "Аудитория заполнена",
                    "Расписание '" + scheduleName + "': аудитория '" + room.getName()
                            + "' заполнена до предела (" + maxStudents + "/" + room.getCapacity() + " мест)"
            );

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ASSIGNMENT_KEY,
                    event
            );
            log.info("Published room-at-capacity notification for schedule '{}', room '{}'", scheduleName, room.getName());
        } catch (Exception e) {
            log.warn("Failed to publish room-at-capacity notification: {}", e.getMessage());
        }
    }

    private void validateScheduleRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            return;
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(
                    "INVALID_SCHEDULE_RANGE",
                    "Schedule endDate must be greater than or equal to startDate"
            );
        }
    }

    private List<LocalDate> resolveLessonDates(Schedule schedule) {
        Set<DayOfWeek> daysOfWeek = schedule.getDaysOfWeek();
        LocalDate startDate = schedule.getStartDate();
        LocalDate endDate = schedule.getEndDate();

        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return List.of(startDate);
        }

        if (endDate == null) {
            LocalDate firstLessonDate = findFirstMatchingDate(startDate, daysOfWeek);
            return List.of(firstLessonDate);
        }

        List<LocalDate> lessonDates = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            if (daysOfWeek.contains(cursor.getDayOfWeek())) {
                lessonDates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }

        if (lessonDates.isEmpty()) {
            throw new BusinessException(
                    "INVALID_SCHEDULE_DAYS",
                    "No lesson dates match the provided daysOfWeek within the selected date range"
            );
        }

        return lessonDates;
    }

    private LocalDate findFirstMatchingDate(LocalDate startDate, Set<DayOfWeek> daysOfWeek) {
        LocalDate cursor = startDate;
        for (int i = 0; i < 7; i++) {
            if (daysOfWeek.contains(cursor.getDayOfWeek())) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }

        throw new BusinessException(
                "INVALID_SCHEDULE_DAYS",
                "No lesson dates match the provided daysOfWeek"
        );
    }

    private void enforceTeacherOwnSchedule(Schedule schedule) {
        if (!isTeacherUser()) {
            return;
        }

        UUID currentTeacherId = resolveCurrentTeacherStaffId();
        if (!currentTeacherId.equals(schedule.getTeacherId())) {
            throw new BusinessException(
                    "TEACHER_SCOPE_DENIED",
                    "Teacher can access only own schedules",
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
}
