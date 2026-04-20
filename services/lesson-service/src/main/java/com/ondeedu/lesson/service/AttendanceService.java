package com.ondeedu.lesson.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.event.AssignmentNotificationEvent;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.lesson.dto.AttendanceDto;
import com.ondeedu.lesson.dto.BulkMarkAttendanceRequest;
import com.ondeedu.lesson.dto.MarkAttendanceRequest;
import com.ondeedu.lesson.entity.Attendance;
import com.ondeedu.lesson.entity.AttendanceStatus;
import com.ondeedu.lesson.entity.Lesson;
import com.ondeedu.lesson.mapper.AttendanceMapper;
import com.ondeedu.lesson.repository.AttendanceRepository;
import com.ondeedu.lesson.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final LessonRepository lessonRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceMapper attendanceMapper;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public AttendanceDto markAttendance(UUID lessonId, MarkAttendanceRequest request) {
        Lesson lesson = getLessonOrThrow(lessonId);
        enforceTeacherOwnLesson(lesson);
        validateAttendanceEditWindow(lesson);
        validateStudentIsActive(request.getStudentId());

        Optional<Attendance> existing = attendanceRepository.findByLessonIdAndStudentId(lessonId, request.getStudentId());
        boolean becameAbsent = request.getStatus() == AttendanceStatus.ABSENT
                && existing.map(Attendance::getStatus).orElse(null) != AttendanceStatus.ABSENT;

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

        if (becameAbsent) {
            maybeNotifyThreeAbsencesInWeek(lesson, request.getStudentId());
        }

        log.info("Marked attendance for student {} in lesson {}: {} [tenant={}]",
                request.getStudentId(), lessonId, request.getStatus(), TenantContext.getTenantId());
        return attendanceMapper.toDto(attendance);
    }

    @Transactional
    public List<AttendanceDto> bulkMarkAttendance(UUID lessonId, BulkMarkAttendanceRequest request) {
        Lesson lesson = getLessonOrThrow(lessonId);
        enforceTeacherOwnLesson(lesson);
        validateAttendanceEditWindow(lesson);

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
        Lesson lesson = getLessonOrThrow(lessonId);
        enforceTeacherOwnLesson(lesson);
        List<Attendance> attendances = attendanceRepository.findByLessonId(lessonId);
        Boolean autoMarkAttendanceSetting = attendanceRepository.findAutoMarkAttendance();
        boolean autoMarkAttendance = Boolean.TRUE.equals(autoMarkAttendanceSetting);

        if (lesson.getGroupId() == null) {
            return attendances.stream()
                    .map(attendanceMapper::toDto)
                    .toList();
        }

        LinkedHashSet<UUID> candidateStudentIds = new LinkedHashSet<>(
                attendanceRepository.findActiveStudentIdsByGroupAndDate(lesson.getGroupId(), lesson.getLessonDate())
        );

        UUID courseId = lesson.getServiceId();
        if (courseId == null) {
            courseId = attendanceRepository.findCourseIdByGroupId(lesson.getGroupId());
        }

        if (courseId != null) {
            candidateStudentIds.addAll(
                    attendanceRepository.findActiveStudentIdsByCourseAndDate(courseId, lesson.getLessonDate())
            );
        }

        Map<UUID, AttendanceDto> existingByStudentId = attendances.stream()
                .map(attendanceMapper::toDto)
                .collect(Collectors.toMap(AttendanceDto::getStudentId, dto -> dto));

        List<AttendanceDto> result = new ArrayList<>();
        for (UUID studentId : candidateStudentIds) {
            AttendanceDto existing = existingByStudentId.remove(studentId);
            if (existing != null) {
                result.add(existing);
                continue;
            }

            result.add(AttendanceDto.builder()
                    .id(null)
                    .lessonId(lessonId)
                    .studentId(studentId)
                    .status(autoMarkAttendance ? AttendanceStatus.ATTENDED : AttendanceStatus.PLANNED)
                    .notes(null)
                    .createdAt(null)
                    .updatedAt(null)
                    .build());
        }

        result.addAll(existingByStudentId.values());
        result.sort(java.util.Comparator.comparing(dto -> dto.getStudentId().toString()));
        return result;
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceDto> getStudentAttendance(UUID studentId, Pageable pageable) {
        Page<Attendance> page;
        if (isTeacherUser()) {
            page = attendanceRepository.findByStudentIdAndTeacherId(studentId, resolveCurrentTeacherStaffId(), pageable);
        } else {
            page = attendanceRepository.findByStudentId(studentId, pageable);
        }
        return PageResponse.from(page, attendanceMapper::toDto);
    }

    @Transactional
    public List<AttendanceDto> markAllPresent(UUID lessonId, List<UUID> studentIds) {
        Lesson lesson = getLessonOrThrow(lessonId);
        enforceTeacherOwnLesson(lesson);
        validateAttendanceEditWindow(lesson);

        List<AttendanceDto> result = new ArrayList<>();
        for (UUID studentId : studentIds) {
            validateStudentIsActive(studentId);
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

    private Lesson getLessonOrThrow(UUID lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", "id", lessonId));
    }

    private void validateStudentIsActive(UUID studentId) {
        if (attendanceRepository.countActiveStudentById(studentId) == 0) {
            throw new BusinessException(
                    "STUDENT_NOT_ACTIVE",
                    "Inactive student cannot be used in attendance operations"
            );
        }
    }

    private void validateAttendanceEditWindow(Lesson lesson) {
        Integer configuredDays = attendanceRepository.findAttendanceWindowDays();
        int windowDays = configuredDays != null ? Math.max(configuredDays, 1) : 7;

        // windowDays=1 means only today's lessons can be edited.
        LocalDate oldestEditableDate = LocalDate.now().minusDays(windowDays - 1L);
        if (lesson.getLessonDate().isBefore(oldestEditableDate)) {
            throw new BusinessException(
                    "ATTENDANCE_EDIT_WINDOW_EXPIRED",
                    "Attendance edit window expired for this lesson"
            );
        }
    }

    private void maybeNotifyThreeAbsencesInWeek(Lesson lesson, UUID studentId) {
        LocalDate lessonDate = lesson.getLessonDate();
        LocalDate weekStart = lessonDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = lessonDate.with(DayOfWeek.SUNDAY);

        long absentCount = attendanceRepository.countAbsentByStudentAndLessonDateBetween(studentId, weekStart, weekEnd);
        if (absentCount != 3) {
            return;
        }

        try {
            AssignmentNotificationEvent event = new AssignmentNotificationEvent(
                    "STUDENT_ABSENCE_3X_WEEK",
                    TenantContext.getTenantId(),
                    TenantContext.getUserId(),
                    null,
                    null,
                    null,
                    "STUDENT",
                    studentId,
                    studentId.toString(),
                    "Ученик пропустил 3 занятия за неделю",
                    "Ученик " + studentId + " имеет 3 пропуска за неделю " + weekStart + " - " + weekEnd
            );

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ASSIGNMENT_KEY,
                    event
            );
            log.info("Published 3-absence weekly alert for student {} [tenant={}]", studentId, TenantContext.getTenantId());
        } catch (Exception e) {
            log.warn("Failed to publish weekly absence alert for student {}: {}", studentId, e.getMessage());
        }
    }

    private void enforceTeacherOwnLesson(Lesson lesson) {
        if (!isTeacherUser()) {
            return;
        }

        UUID currentTeacherId = resolveCurrentTeacherStaffId();
        if (!currentTeacherId.equals(lesson.getTeacherId())) {
            throw new BusinessException(
                    "TEACHER_SCOPE_DENIED",
                    "Teacher can access only own attendance",
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
