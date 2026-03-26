package com.ondeedu.lesson.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    @CacheEvict(value = "lessons", allEntries = true)
    public LessonDto createLesson(CreateLessonRequest request) {
        Lesson lesson = lessonMapper.toEntity(request);
        lesson = lessonRepository.save(lesson);
        log.info("Created lesson: {} on {}", lesson.getId(), lesson.getLessonDate());
        return lessonMapper.toDto(lesson);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto getLesson(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
        return lessonMapper.toDto(lesson);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto updateLesson(UUID id, UpdateLessonRequest request) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));

        lessonMapper.updateEntity(lesson, request);
        lesson = lessonRepository.save(lesson);

        log.info("Updated lesson: {}", id);
        return lessonMapper.toDto(lesson);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public void deleteLesson(UUID id) {
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

        lesson.setStatus(LessonStatus.COMPLETED);
        if (topic != null) {
            lesson.setTopic(topic);
        }
        if (homework != null) {
            lesson.setHomework(homework);
        }
        lesson = lessonRepository.save(lesson);

        log.info("Completed lesson: {}", id);
        return lessonMapper.toDto(lesson);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto cancelLesson(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));

        lesson.setStatus(LessonStatus.CANCELLED);
        lesson = lessonRepository.save(lesson);

        log.info("Cancelled lesson: {}", id);
        return lessonMapper.toDto(lesson);
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonDto> listLessons(LessonType type, LessonStatus status, LocalDate date,
                                                LocalDate from, LocalDate to, Pageable pageable) {
        Page<Lesson> page;

        boolean hasDateRange = from != null && to != null;

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
            page = lessonRepository.findByLessonDate(date, pageable);
        } else {
            page = lessonRepository.findAll(pageable);
        }

        return PageResponse.from(page, lessonMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonDto> listByGroup(UUID groupId, LocalDate from, LocalDate to, Pageable pageable) {
        Page<Lesson> page;

        if (from != null && to != null) {
            page = lessonRepository.findByGroupIdAndLessonDateBetween(groupId, from, to, pageable);
        } else {
            page = lessonRepository.findByGroupId(groupId, pageable);
        }

        return PageResponse.from(page, lessonMapper::toDto);
    }

    @Transactional
    @CacheEvict(value = "lessons", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public LessonDto markTeacherAbsent(UUID id) {
        Lesson lesson = lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", id));
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
        Page<Lesson> page = lessonRepository.findByTeacherId(teacherId, pageable);
        return PageResponse.from(page, lessonMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<LessonDto> listCalendar(LocalDate from, LocalDate to) {
        return lessonRepository.findByLessonDateBetweenOrderByLessonDateAscStartTimeAsc(from, to)
                .stream()
                .map(lessonMapper::toDto)
                .collect(Collectors.toList());
    }
}
