package com.ondeedu.student.service;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.event.StudentCreatedEvent;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.student.dto.CreateStudentRequest;
import com.ondeedu.student.dto.StudentDto;
import com.ondeedu.student.dto.StudentStatsDto;
import com.ondeedu.student.dto.UpdateStudentRequest;
import com.ondeedu.student.entity.EnrollmentStatus;
import com.ondeedu.student.entity.Student;
import com.ondeedu.student.entity.StudentGroup;
import com.ondeedu.student.entity.StudentStatus;
import com.ondeedu.student.mapper.StudentMapper;
import com.ondeedu.student.repository.StudentGroupRepository;
import com.ondeedu.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final StudentMapper studentMapper;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    @CacheEvict(value = "students", allEntries = true)
    public StudentDto createStudent(CreateStudentRequest request) {
        // Check for duplicates
        if (request.getPhone() != null && studentRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException("DUPLICATE_PHONE", "Student with this phone already exists");
        }
        if (request.getEmail() != null && studentRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("DUPLICATE_EMAIL", "Student with this email already exists");
        }

        Student student = studentMapper.toEntity(request);
        student = studentRepository.save(student);

        log.info("Created student: {} {}", student.getFirstName(), student.getLastName());

        // Publish event
        StudentCreatedEvent event = new StudentCreatedEvent(
            TenantContext.getTenantId(),
            TenantContext.getUserId(),
            student.getId(),
            student.getFirstName(),
            student.getLastName(),
            student.getEmail(),
            student.getPhone()
        );
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.STUDENT_EXCHANGE,
            RabbitMQConfig.STUDENT_CREATED_KEY,
            event
        );

        return studentMapper.toDto(student);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "students", key = "#id")
    public StudentDto getStudent(UUID id) {
        Student student = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Student", "id", id));
        return studentMapper.toDto(student);
    }

    @Transactional
    @CacheEvict(value = "students", key = "#id")
    public StudentDto updateStudent(UUID id, UpdateStudentRequest request) {
        Student student = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Student", "id", id));

        studentMapper.updateEntity(student, request);
        student = studentRepository.save(student);

        log.info("Updated student: {}", id);
        return studentMapper.toDto(student);
    }

    @Transactional
    @CacheEvict(value = "students", key = "#id")
    public void deleteStudent(UUID id) {
        if (!studentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Student", "id", id);
        }
        studentRepository.deleteById(id);
        log.info("Deleted student: {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentDto> listStudents(StudentStatus status, Pageable pageable) {
        Page<Student> page;
        if (status != null) {
            page = studentRepository.findByStatus(status, pageable);
        } else {
            page = studentRepository.findAll(pageable);
        }
        return PageResponse.from(page, studentMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentDto> searchStudents(String query, Pageable pageable) {
        Page<Student> page = studentRepository.search(query, pageable);
        return PageResponse.from(page, studentMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentDto> getStudentsByGroup(UUID groupId, Pageable pageable) {
        Page<Student> page = studentRepository.findByGroupId(groupId, pageable);
        return PageResponse.from(page, studentMapper::toDto);
    }

    @Transactional
    public void addStudentToGroup(UUID studentId, UUID groupId) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student", "id", studentId);
        }

        if (studentGroupRepository.existsByStudentIdAndGroupIdAndStatus(
                studentId, groupId, EnrollmentStatus.ACTIVE)) {
            throw new BusinessException("ALREADY_ENROLLED", "Student is already enrolled in this group");
        }

        StudentGroup enrollment = StudentGroup.builder()
            .studentId(studentId)
            .groupId(groupId)
            .status(EnrollmentStatus.ACTIVE)
            .enrolledAt(Instant.now())
            .build();

        studentGroupRepository.save(enrollment);
        log.info("Added student {} to group {}", studentId, groupId);
    }

    @Transactional
    public void removeStudentFromGroup(UUID studentId, UUID groupId) {
        StudentGroup enrollment = studentGroupRepository
            .findByStudentIdAndGroupId(studentId, groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found"));

        enrollment.setStatus(EnrollmentStatus.DROPPED);
        enrollment.setCompletedAt(Instant.now());
        studentGroupRepository.save(enrollment);

        log.info("Removed student {} from group {}", studentId, groupId);
    }

    @Transactional(readOnly = true)
    public StudentStatsDto getStats() {
        Instant monthStart = Instant.now().minus(30, ChronoUnit.DAYS);

        return StudentStatsDto.builder()
            .totalStudents(studentRepository.count())
            .activeStudents(studentRepository.countByStatus(StudentStatus.ACTIVE))
            .newThisMonth(studentRepository.countNewStudentsSince(monthStart))
            .graduated(studentRepository.countByStatus(StudentStatus.GRADUATED))
            .dropped(studentRepository.countByStatus(StudentStatus.DROPPED))
            .build();
    }
}
