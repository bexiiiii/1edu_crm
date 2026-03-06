package com.ondeedu.schedule.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
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

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleMapper scheduleMapper;

    @Transactional
    @CacheEvict(value = "schedules", allEntries = true)
    public ScheduleDto createSchedule(CreateScheduleRequest request) {
        Schedule schedule = scheduleMapper.toEntity(request);
        schedule = scheduleRepository.save(schedule);
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
    @CacheEvict(value = "schedules", key = "#id")
    public ScheduleDto updateSchedule(UUID id, UpdateScheduleRequest request) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", "id", id));
        scheduleMapper.updateEntity(schedule, request);
        schedule = scheduleRepository.save(schedule);
        log.info("Updated schedule: {}", id);
        return scheduleMapper.toDto(schedule);
    }

    @Transactional
    @CacheEvict(value = "schedules", key = "#id")
    public void deleteSchedule(UUID id) {
        if (!scheduleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Schedule", "id", id);
        }
        scheduleRepository.deleteById(id);
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
}
