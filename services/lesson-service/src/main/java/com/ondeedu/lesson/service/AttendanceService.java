package com.ondeedu.lesson.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.lesson.dto.AttendanceDto;
import com.ondeedu.lesson.dto.BulkMarkAttendanceRequest;
import com.ondeedu.lesson.dto.MarkAttendanceRequest;
import com.ondeedu.lesson.entity.Attendance;
import com.ondeedu.lesson.entity.AttendanceStatus;
import com.ondeedu.lesson.mapper.AttendanceMapper;
import com.ondeedu.lesson.repository.AttendanceRepository;
import com.ondeedu.lesson.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final LessonRepository lessonRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceMapper attendanceMapper;

    @Transactional
    public AttendanceDto markAttendance(UUID lessonId, MarkAttendanceRequest request) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", "id", lessonId);
        }

        Optional<Attendance> existing = attendanceRepository.findByLessonIdAndStudentId(lessonId, request.getStudentId());

        Attendance attendance;
        if (existing.isPresent()) {
            attendance = existing.get();
            attendance.setStatus(request.getStatus());
            if (request.getNotes() != null) {
                attendance.setNotes(request.getNotes());
            }
        } else {
            attendance = attendanceMapper.toEntity(request);
            attendance.setLessonId(lessonId);
        }

        attendance = attendanceRepository.save(attendance);
        log.info("Marked attendance for student {} in lesson {}: {}", request.getStudentId(), lessonId, request.getStatus());
        return attendanceMapper.toDto(attendance);
    }

    @Transactional
    public List<AttendanceDto> bulkMarkAttendance(UUID lessonId, BulkMarkAttendanceRequest request) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", "id", lessonId);
        }

        List<AttendanceDto> result = new ArrayList<>();
        for (MarkAttendanceRequest mark : request.getAttendances()) {
            AttendanceDto dto = markAttendance(lessonId, mark);
            result.add(dto);
        }

        log.info("Bulk marked attendance for {} students in lesson {}", result.size(), lessonId);
        return result;
    }

    @Transactional(readOnly = true)
    public List<AttendanceDto> getLessonAttendance(UUID lessonId) {
        List<Attendance> attendances = attendanceRepository.findByLessonId(lessonId);
        return attendances.stream()
                .map(attendanceMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDto> getStudentAttendance(UUID studentId, Pageable pageable) {
        Page<Attendance> page = attendanceRepository.findByStudentId(studentId, pageable);
        return PageResponse.from(page, attendanceMapper::toDto);
    }

    @Transactional
    public List<AttendanceDto> markAllPresent(UUID lessonId, List<UUID> studentIds) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson", "id", lessonId);
        }

        List<AttendanceDto> result = new ArrayList<>();
        for (UUID studentId : studentIds) {
            Optional<Attendance> existing = attendanceRepository.findByLessonIdAndStudentId(lessonId, studentId);

            Attendance attendance;
            if (existing.isPresent()) {
                attendance = existing.get();
                attendance.setStatus(AttendanceStatus.ATTENDED);
            } else {
                attendance = Attendance.builder()
                        .lessonId(lessonId)
                        .studentId(studentId)
                        .status(AttendanceStatus.ATTENDED)
                        .build();
            }

            attendance = attendanceRepository.save(attendance);
            result.add(attendanceMapper.toDto(attendance));
        }

        log.info("Marked all {} students as present in lesson {}", result.size(), lessonId);
        return result;
    }
}
