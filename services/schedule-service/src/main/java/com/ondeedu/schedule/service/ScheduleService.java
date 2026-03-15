package com.ondeedu.schedule.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.schedule.client.LessonGrpcClient;
import com.ondeedu.schedule.client.LessonGrpcClient.ManagedLesson;
import com.ondeedu.schedule.dto.CreateScheduleRequest;
import com.ondeedu.schedule.dto.ScheduleDto;
import com.ondeedu.schedule.dto.UpdateScheduleRequest;
import com.ondeedu.schedule.entity.Schedule;
import com.ondeedu.schedule.entity.ScheduleStatus;
import com.ondeedu.schedule.mapper.ScheduleMapper;
import com.ondeedu.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleMapper scheduleMapper;
    private final LessonGrpcClient lessonGrpcClient;

    @Transactional
    @CacheEvict(value = "schedules", allEntries = true)
    public ScheduleDto createSchedule(CreateScheduleRequest request) {
        validateScheduleRange(request.getStartDate(), request.getEndDate());
        Schedule schedule = scheduleMapper.toEntity(request);
        schedule = scheduleRepository.saveAndFlush(schedule);

        synchronizeLessonsOnCreate(schedule);

        log.info("Created schedule: {} ({})", schedule.getName(), schedule.getId());
        return scheduleMapper.toDto(schedule);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "schedules", key = "#id")
    public ScheduleDto getSchedule(UUID id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", "id", id));
        return scheduleMapper.toDto(schedule);
    }

    @Transactional
    @CacheEvict(value = "schedules", allEntries = true)
    public ScheduleDto updateSchedule(UUID id, UpdateScheduleRequest request) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", "id", id));
        List<ManagedLesson> existingLessons = lessonGrpcClient.listGroupLessons(id);

        scheduleMapper.updateEntity(schedule, request);
        validateScheduleRange(schedule.getStartDate(), schedule.getEndDate());
        schedule = scheduleRepository.saveAndFlush(schedule);

        synchronizeLessonsOnUpdate(schedule, existingLessons);

        log.info("Updated schedule: {}", id);
        return scheduleMapper.toDto(schedule);
    }

    @Transactional
    @CacheEvict(value = "schedules", allEntries = true)
    public void deleteSchedule(UUID id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", "id", id));
        List<ManagedLesson> existingLessons = lessonGrpcClient.listGroupLessons(id);

        synchronizeLessonsOnDelete(id, existingLessons);

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
        if (status != null) {
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
        Page<Schedule> page = scheduleRepository.findByRoomId(roomId, pageable);
        return PageResponse.from(page, scheduleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<ScheduleDto> searchSchedules(String query, Pageable pageable) {
        Page<Schedule> page = scheduleRepository.search(query, pageable);
        return PageResponse.from(page, scheduleMapper::toDto);
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
}
