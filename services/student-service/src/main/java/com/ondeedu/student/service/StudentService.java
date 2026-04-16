package com.ondeedu.student.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.audit.AuditAction;
import com.ondeedu.common.audit.AuditLogPublisher;
import com.ondeedu.common.audit.TenantAuditEvent;
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
import com.ondeedu.student.search.StudentSearchService;
import com.ondeedu.student.support.StudentMetadata;
import com.ondeedu.student.repository.StudentGroupRepository;
import com.ondeedu.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final StudentMapper studentMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Optional<StudentSearchService> studentSearchService;
    private final AuditLogPublisher auditLogPublisher;

    @Transactional
    @CacheEvict(value = {"students", "student-stats"}, allEntries = true)
    public StudentDto createStudent(CreateStudentRequest request) {
        normalizeCreateRequest(request);

        // Check for duplicates
        if (request.getPhone() != null && studentRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException("DUPLICATE_PHONE", "Student with this phone already exists");
        }
        if (request.getEmail() != null && studentRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("DUPLICATE_EMAIL", "Student with this email already exists");
        }

        Student student = studentMapper.toEntity(request);
        student.setStatus(request.getStatus() != null ? request.getStatus() : StudentStatus.ACTIVE);
        writeMetadata(student, request.getAdditionalPhones(), request.getStateOrderParticipant(), request.getLoyalty());
        student = studentRepository.save(student);

        log.info("Created student: {} {}", student.getFirstName(), student.getLastName());

        publishStudentCreatedEvent(student);
        indexStudent(student);
        auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                .tenantId(TenantContext.getTenantId())
                .action(AuditAction.STUDENT_CREATED)
                .category("STUDENTS")
                .actorId(TenantContext.getUserId())
                .targetType("STUDENT")
                .targetId(student.getId().toString())
                .targetName(student.getFirstName() + " " + student.getLastName())
                .build());

        return toDto(student);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "students", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public StudentDto getStudent(UUID id) {
        Student student = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Student", "id", id));
        return toDto(student);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "students", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)"),
            @CacheEvict(value = "student-stats", allEntries = true)
    })
    public StudentDto updateStudent(UUID id, UpdateStudentRequest request) {
        Student student = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Student", "id", id));

        normalizeUpdateRequest(request);

        if (request.getPhone() != null && !Objects.equals(request.getPhone(), student.getPhone())
                && studentRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException("DUPLICATE_PHONE", "Student with this phone already exists");
        }
        if (request.getEmail() != null && !Objects.equals(request.getEmail(), student.getEmail())
                && studentRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("DUPLICATE_EMAIL", "Student with this email already exists");
        }

        studentMapper.updateEntity(student, request);
        if (request.getStatus() != null) {
            student.setStatus(request.getStatus());
        }
        mergeMetadata(student, request.getAdditionalPhones(), request.getStateOrderParticipant(), request.getLoyalty());
        student = studentRepository.save(student);
        indexStudent(student);

        log.info("Updated student: {}", id);
        auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                .tenantId(TenantContext.getTenantId())
                .action(AuditAction.STUDENT_UPDATED)
                .category("STUDENTS")
                .actorId(TenantContext.getUserId())
                .targetType("STUDENT")
                .targetId(id.toString())
                .targetName(student.getFirstName() + " " + student.getLastName())
                .build());
        return toDto(student);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "students", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)"),
            @CacheEvict(value = "student-stats", allEntries = true)
    })
    public void deleteStudent(UUID id) {
        if (!studentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Student", "id", id);
        }
        studentRepository.deleteById(id);
        deleteStudentFromIndex(id);
        log.info("Deleted student: {}", id);
        auditLogPublisher.publishTenant(TenantAuditEvent.builder()
                .tenantId(TenantContext.getTenantId())
                .action(AuditAction.STUDENT_DELETED)
                .category("STUDENTS")
                .actorId(TenantContext.getUserId())
                .targetType("STUDENT")
                .targetId(id.toString())
                .build());
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentDto> listStudents(StudentStatus status, Pageable pageable) {
        Page<Student> page;
        if (status != null) {
            page = studentRepository.findByStatus(status, pageable);
        } else {
            page = studentRepository.findAll(pageable);
        }
        return PageResponse.from(page, this::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentDto> searchStudents(String query, Pageable pageable) {
        String tenantId = TenantContext.getTenantId();

        if (StringUtils.hasText(tenantId) && studentSearchService.isPresent()) {
            try {
                PageResponse<StudentDto> indexedResults =
                        studentSearchService.get().searchStudents(tenantId, query, pageable);
                if (indexedResults.getTotalElements() > 0) {
                    return indexedResults;
                }
            } catch (Exception e) {
                log.warn("Elasticsearch student search failed, falling back to PostgreSQL: {}", e.getMessage());
            }
        }

        Page<Student> page = studentRepository.search(query, pageable);
        scheduleIndexBackfill(page.getContent(), tenantId);
        return PageResponse.from(page, this::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentDto> getStudentsByGroup(UUID groupId, Pageable pageable) {
        Page<Student> page = studentRepository.findByGroupId(groupId, pageable);
        return PageResponse.from(page, this::toDto);
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
    @Cacheable(value = "student-stats", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('summary')")
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

    private StudentDto toDto(Student student) {
        StudentDto dto = studentMapper.toDto(student);
        StudentMetadata metadata = readMetadata(student.getMetadata());
        dto.setAdditionalPhones(metadata.getAdditionalPhones());
        dto.setStateOrderParticipant(metadata.getStateOrderParticipant());
        dto.setLoyalty(metadata.getLoyalty());
        return dto;
    }

    private void publishStudentCreatedEvent(Student student) {
        try {
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
        } catch (Exception e) {
            log.warn("Student {} created but event publish failed: {}", student.getId(), e.getMessage());
        }
    }

    private void indexStudents(List<Student> students) {
        String tenantId = TenantContext.getTenantId();
        studentSearchService.ifPresent(service -> students.forEach(student -> safeIndexStudent(service, student, tenantId)));
    }

    private void indexStudent(Student student) {
        String tenantId = TenantContext.getTenantId();
        studentSearchService.ifPresent(service -> safeIndexStudent(service, student, tenantId));
    }

    private void safeIndexStudent(StudentSearchService service, Student student, String tenantId) {
        try {
            service.indexStudent(student, tenantId, readMetadata(student.getMetadata()));
        } catch (Exception e) {
            log.warn("Failed to index student {} in Elasticsearch: {}", student.getId(), e.getMessage());
        }
    }

    private void scheduleIndexBackfill(List<Student> students, String tenantId) {
        if (!StringUtils.hasText(tenantId) || students == null || students.isEmpty()) {
            return;
        }
        studentSearchService.ifPresent(service -> CompletableFuture.runAsync(
                () -> students.forEach(student -> safeIndexStudent(service, student, tenantId))
        ));
    }

    private void deleteStudentFromIndex(UUID studentId) {
        studentSearchService.ifPresent(service -> {
            try {
                service.deleteStudent(studentId);
            } catch (Exception e) {
                log.warn("Failed to delete student {} from Elasticsearch: {}", studentId, e.getMessage());
            }
        });
    }

    private void normalizeCreateRequest(CreateStudentRequest request) {
        applyFullName(request);
        request.setStatus(request.getStatus() != null ? request.getStatus() : StudentStatus.ACTIVE);
        request.setPhone(normalizePhone(request.getPhone()));
        request.setParentPhone(normalizePhone(request.getParentPhone()));
        request.setStudentPhone(normalizePhone(request.getStudentPhone()));
        request.setAdditionalPhones(normalizePhones(request.getAdditionalPhones()));
        request.setCustomer(trimToNull(request.getCustomer()));
        request.setStudentPhoto(trimToNull(request.getStudentPhoto()));
        request.setAddress(trimToNull(request.getAddress()));
        request.setCity(trimToNull(request.getCity()));
        request.setSchool(trimToNull(request.getSchool()));
        request.setGrade(trimToNull(request.getGrade()));
        request.setAdditionalInfo(trimToNull(request.getAdditionalInfo()));
        request.setContract(trimToNull(request.getContract()));
        request.setDiscount(trimToNull(request.getDiscount()));
        request.setComment(trimToNull(request.getComment()));
        request.setLoyalty(trimToNull(request.getLoyalty()));
        request.setNotes(trimToNull(request.getNotes()));

        if (!StringUtils.hasText(request.getFirstName()) || !StringUtils.hasText(request.getLastName())) {
            throw new BusinessException(
                    "VALIDATION_ERROR",
                    "Student fullName or firstName/lastName is required"
            );
        }
    }

    private void normalizeUpdateRequest(UpdateStudentRequest request) {
        applyFullName(request);
        request.setPhone(normalizePhone(request.getPhone()));
        request.setParentPhone(normalizePhone(request.getParentPhone()));
        request.setStudentPhone(normalizePhone(request.getStudentPhone()));
        request.setAdditionalPhones(normalizePhones(request.getAdditionalPhones()));
        request.setCustomer(trimToNull(request.getCustomer()));
        request.setStudentPhoto(trimToNull(request.getStudentPhoto()));
        request.setAddress(trimToNull(request.getAddress()));
        request.setCity(trimToNull(request.getCity()));
        request.setSchool(trimToNull(request.getSchool()));
        request.setGrade(trimToNull(request.getGrade()));
        request.setAdditionalInfo(trimToNull(request.getAdditionalInfo()));
        request.setContract(trimToNull(request.getContract()));
        request.setDiscount(trimToNull(request.getDiscount()));
        request.setComment(trimToNull(request.getComment()));
        request.setLoyalty(trimToNull(request.getLoyalty()));
        request.setNotes(trimToNull(request.getNotes()));
    }

    private void applyFullName(CreateStudentRequest request) {
        StudentName name = resolveName(request.getFullName(), request.getFirstName(), request.getMiddleName(), request.getLastName());
        request.setFirstName(name.firstName());
        request.setMiddleName(name.middleName());
        request.setLastName(name.lastName());
    }

    private void applyFullName(UpdateStudentRequest request) {
        StudentName name = resolveName(request.getFullName(), request.getFirstName(), request.getMiddleName(), request.getLastName());
        request.setFirstName(name.firstName());
        request.setMiddleName(name.middleName());
        request.setLastName(name.lastName());
    }

    private StudentName resolveName(String fullName, String firstName, String middleName, String lastName) {
        if (!StringUtils.hasText(fullName)) {
            return new StudentName(trimToNull(firstName), trimToNull(middleName), trimToNull(lastName));
        }

        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 0) {
            return new StudentName(trimToNull(firstName), trimToNull(middleName), trimToNull(lastName));
        }

        String resolvedFirstName = parts[0];
        String resolvedMiddleName = null;
        String resolvedLastName = parts.length > 1 ? parts[parts.length - 1] : null;

        if (parts.length > 2) {
            resolvedMiddleName = String.join(" ", List.of(parts).subList(1, parts.length - 1));
        }

        return new StudentName(
                trimToNull(resolvedFirstName),
                trimToNull(resolvedMiddleName),
                trimToNull(resolvedLastName)
        );
    }

    private void mergeMetadata(Student student, List<String> additionalPhones, Boolean stateOrderParticipant, String loyalty) {
        StudentMetadata metadata = readMetadata(student.getMetadata());
        if (additionalPhones != null) {
            metadata.setAdditionalPhones(additionalPhones);
        }
        if (stateOrderParticipant != null) {
            metadata.setStateOrderParticipant(stateOrderParticipant);
        }
        if (loyalty != null) {
            metadata.setLoyalty(loyalty);
        }
        student.setMetadata(writeMetadataValue(metadata));
    }

    private void writeMetadata(Student student, List<String> additionalPhones, Boolean stateOrderParticipant, String loyalty) {
        StudentMetadata metadata = StudentMetadata.builder()
                .additionalPhones(additionalPhones != null ? additionalPhones : List.of())
                .stateOrderParticipant(stateOrderParticipant)
                .loyalty(loyalty)
                .build();
        student.setMetadata(writeMetadataValue(metadata));
    }

    private StudentMetadata readMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return StudentMetadata.builder().additionalPhones(List.of()).build();
        }

        try {
            StudentMetadata metadata = objectMapper.readValue(metadataJson, StudentMetadata.class);
            if (metadata.getAdditionalPhones() == null) {
                metadata.setAdditionalPhones(List.of());
            }
            metadata.setLoyalty(trimToNull(metadata.getLoyalty()));
            return metadata;
        } catch (Exception e) {
            log.warn("Failed to parse student metadata: {}", e.getMessage());
            return StudentMetadata.builder().additionalPhones(List.of()).build();
        }
    }

    private String writeMetadataValue(StudentMetadata metadata) {
        metadata.setAdditionalPhones(metadata.getAdditionalPhones() != null ? metadata.getAdditionalPhones() : List.of());
        metadata.setLoyalty(trimToNull(metadata.getLoyalty()));

        if (metadata.getAdditionalPhones().isEmpty()
                && metadata.getStateOrderParticipant() == null
                && !StringUtils.hasText(metadata.getLoyalty())) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new BusinessException("STUDENT_METADATA_SERIALIZATION_FAILED", "Failed to store student metadata");
        }
    }

    private List<String> normalizePhones(List<String> phones) {
        if (phones == null) {
            return null;
        }

        return phones.stream()
                .map(this::normalizePhone)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    private String normalizePhone(String phone) {
        return trimToNull(phone);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record StudentName(String firstName, String middleName, String lastName) {
    }
}
