package com.ondeedu.course.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.course.client.PaymentGrpcClient;
import com.ondeedu.course.dto.CourseDto;
import com.ondeedu.course.dto.CourseEnrollmentDto;
import com.ondeedu.course.dto.CreateCourseRequest;
import com.ondeedu.course.dto.UpdateCourseRequest;
import com.ondeedu.course.entity.Course;
import com.ondeedu.course.entity.CourseStudent;
import com.ondeedu.course.entity.CourseStatus;
import com.ondeedu.course.entity.CourseType;
import com.ondeedu.course.mapper.CourseMapper;
import com.ondeedu.course.repository.CourseRepository;
import com.ondeedu.course.repository.CourseStudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseStudentRepository courseStudentRepository;
    private final CourseMapper courseMapper;
    private final PaymentGrpcClient paymentGrpcClient;
    private final CourseConstraintsService courseConstraintsService;

    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public CourseDto createCourse(CreateCourseRequest request) {
        Course course = courseMapper.toEntity(request);
        course.setBranchId(resolveCurrentBranchId());
        courseConstraintsService.validateTeacherIsActive(course.getTeacherId());
        courseConstraintsService.validateEnrollmentLimitAgainstTenantSettings(course.getEnrollmentLimit());

        List<UUID> targetStudentIds = normalizeStudentIds(request.getStudentIds());
        validateEnrollmentLimit(course, targetStudentIds);
        courseConstraintsService.validateStudentsAreActive(targetStudentIds);

        course = courseRepository.save(course);
        syncCourseStudents(course, List.of(), targetStudentIds);
        log.info("Created course: {} ({})", course.getName(), course.getId());
        return toDto(course);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "courses", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id) + '::branch=' + T(com.ondeedu.common.tenant.TenantContext).getBranchId()")
    public CourseDto getCourse(UUID id) {
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        return toDto(course);
    }

    @Transactional
    @CacheEvict(value = "courses", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public CourseDto updateCourse(UUID id, UpdateCourseRequest request) {
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        List<UUID> existingStudentIds = getActiveStudentIds(course.getId());
        courseMapper.updateEntity(course, request);

        courseConstraintsService.validateTeacherIsActive(course.getTeacherId());
        courseConstraintsService.validateEnrollmentLimitAgainstTenantSettings(course.getEnrollmentLimit());

        course = courseRepository.save(course);
        List<UUID> targetStudentIds = request.getStudentIds() != null
                ? normalizeStudentIds(request.getStudentIds())
                : existingStudentIds;

        validateEnrollmentLimit(course, targetStudentIds);
        syncCourseStudents(course, existingStudentIds, targetStudentIds);
        log.info("Updated course: {}", id);
        return toDto(course);
    }

    @Transactional
    @CacheEvict(value = "courses", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id)")
    public void deleteCourse(UUID id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        cancelCourseSubscriptions(course, getActiveStudentIds(id));
        courseStudentRepository.deleteByCourseId(id);
        courseRepository.deleteById(id);
        log.info("Deleted course: {}", id);
    }

    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public CourseDto addStudentToCourse(UUID courseId, UUID studentId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

        boolean alreadyEnrolled = courseStudentRepository
                .findByCourseIdAndStudentIdAndRemovedAtIsNull(courseId, studentId)
                .isPresent();
        if (alreadyEnrolled) {
            throw new BusinessException("STUDENT_ALREADY_ENROLLED", "Student is already enrolled in this course");
        }

        List<UUID> existingStudentIds = getActiveStudentIds(courseId);
        List<UUID> targetStudentIds = new java.util.ArrayList<>(existingStudentIds);
        targetStudentIds.add(studentId);

        validateEnrollmentLimit(course, targetStudentIds);
        courseConstraintsService.validateStudentsAreActive(List.of(studentId));

        CourseStudent enrollment = CourseStudent.builder()
                .courseId(courseId)
                .studentId(studentId)
                .enrolledAt(Instant.now())
                .build();
        courseStudentRepository.save(enrollment);

        paymentGrpcClient.ensureCourseSubscription(studentId, courseId, course.getName(), course.getBasePrice());
        log.info("Added student {} to course {}", studentId, courseId);
        return toDto(course, targetStudentIds);
    }

    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public CourseDto removeStudentFromCourse(UUID courseId, UUID studentId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

        CourseStudent enrollment = courseStudentRepository
                .findByCourseIdAndStudentIdAndRemovedAtIsNull(courseId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", "studentId", studentId));

        enrollment.setRemovedAt(Instant.now());
        courseStudentRepository.save(enrollment);

        paymentGrpcClient.cancelCourseSubscription(studentId, courseId);
        log.info("Removed student {} from course {}", studentId, courseId);

        List<UUID> remaining = getActiveStudentIds(courseId);
        return toDto(course, remaining);
    }

    @Transactional(readOnly = true)
    public List<CourseEnrollmentDto> getStudentEnrollments(UUID studentId) {
        List<CourseStudent> enrollments = courseStudentRepository.findByStudentIdOrderByEnrolledAtDesc(studentId);
        if (enrollments.isEmpty()) {
            return List.of();
        }

        List<UUID> courseIds = enrollments.stream()
                .map(CourseStudent::getCourseId)
                .distinct()
                .toList();

        Map<UUID, Course> courseById = courseRepository.findAllById(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        return enrollments.stream()
                .map(cs -> {
                    Course course = courseById.get(cs.getCourseId());
                    return CourseEnrollmentDto.builder()
                            .courseId(cs.getCourseId())
                            .courseName(course != null ? course.getName() : null)
                            .courseType(course != null ? course.getType() : null)
                            .courseStatus(course != null ? course.getStatus() : null)
                            .color(course != null ? course.getColor() : null)
                            .basePrice(course != null ? course.getBasePrice() : null)
                            .enrolledAt(cs.getEnrolledAt())
                            .removedAt(cs.getRemovedAt())
                            .enrollmentStatus(cs.getRemovedAt() == null ? "ACTIVE" : "REMOVED")
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDto> listCourses(CourseStatus status, CourseType type, Pageable pageable) {
        UUID branchId = resolveCurrentBranchId();
        Page<Course> page;
        if (status != null && type != null) {
            page = courseRepository.findByStatusAndTypeAndBranch(status, type, branchId, pageable);
        } else if (status != null) {
            page = courseRepository.findByStatusAndBranch(status, branchId, pageable);
        } else if (type != null) {
            page = courseRepository.findByTypeAndBranch(type, branchId, pageable);
        } else {
            page = courseRepository.findAllByBranch(branchId, pageable);
        }
        return mapPage(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDto> searchCourses(String query, Pageable pageable) {
        UUID branchId = resolveCurrentBranchId();
        Page<Course> page = courseRepository.searchByBranch(query, branchId, pageable);
        return mapPage(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDto> getCoursesByTeacher(UUID teacherId, Pageable pageable) {
        UUID branchId = resolveCurrentBranchId();
        Page<Course> page = courseRepository.findByTeacherIdAndBranch(teacherId, branchId, pageable);
        return mapPage(page);
    }

    private PageResponse<CourseDto> mapPage(Page<Course> page) {
        Map<UUID, List<UUID>> studentIdsByCourseId = loadActiveStudentIds(page.getContent());
        return PageResponse.from(page,
                course -> toDto(course, studentIdsByCourseId.getOrDefault(course.getId(), List.of())));
    }

    private CourseDto toDto(Course course) {
        Map<UUID, List<UUID>> studentIdsByCourseId = loadActiveStudentIds(List.of(course));
        return toDto(course, studentIdsByCourseId.getOrDefault(course.getId(), List.of()));
    }

    private CourseDto toDto(Course course, List<UUID> studentIds) {
        CourseDto dto = courseMapper.toDto(course);
        dto.setStudentIds(studentIds);
        return dto;
    }

    private void syncCourseStudents(Course course, List<UUID> existingStudentIds, List<UUID> targetStudentIds) {
        Set<UUID> existingStudentIdSet = new LinkedHashSet<>(existingStudentIds);
        Set<UUID> targetStudentIdSet = new LinkedHashSet<>(targetStudentIds);

        List<UUID> addedStudentIds = targetStudentIdSet.stream()
                .filter(studentId -> !existingStudentIdSet.contains(studentId))
                .toList();
        List<UUID> removedStudentIds = existingStudentIdSet.stream()
                .filter(studentId -> !targetStudentIdSet.contains(studentId))
                .toList();

        courseConstraintsService.validateStudentsAreActive(addedStudentIds);
        syncCourseSubscriptions(course, targetStudentIds, removedStudentIds);

        if (!removedStudentIds.isEmpty()) {
            courseStudentRepository.softDeleteByCourseIdAndStudentIdIn(
                    course.getId(), removedStudentIds, Instant.now());
        }

        if (addedStudentIds.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<CourseStudent> courseStudents = addedStudentIds.stream()
                .map(studentId -> CourseStudent.builder()
                        .courseId(course.getId())
                        .studentId(studentId)
                        .enrolledAt(now)
                        .build())
                .toList();
        courseStudentRepository.saveAll(courseStudents);
    }

    private void syncCourseSubscriptions(Course course, List<UUID> targetStudentIds, List<UUID> removedStudentIds) {
        targetStudentIds.forEach(studentId ->
                paymentGrpcClient.ensureCourseSubscription(studentId, course.getId(), course.getName(), course.getBasePrice())
        );
        removedStudentIds.forEach(studentId ->
                paymentGrpcClient.cancelCourseSubscription(studentId, course.getId())
        );
    }

    private void cancelCourseSubscriptions(Course course, List<UUID> studentIds) {
        studentIds.forEach(studentId -> paymentGrpcClient.cancelCourseSubscription(studentId, course.getId()));
    }

    private List<UUID> getActiveStudentIds(UUID courseId) {
        return courseStudentRepository.findByCourseIdAndRemovedAtIsNullOrderByEnrolledAtAsc(courseId).stream()
                .map(CourseStudent::getStudentId)
                .toList();
    }

    private Map<UUID, List<UUID>> loadActiveStudentIds(Collection<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> courseIds = courses.stream()
                .map(Course::getId)
                .toList();

        return courseStudentRepository.findByCourseIdInAndRemovedAtIsNullOrderByCourseIdAscEnrolledAtAsc(courseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        CourseStudent::getCourseId,
                        Collectors.mapping(CourseStudent::getStudentId, Collectors.toList())
                ));
    }

    private List<UUID> normalizeStudentIds(List<UUID> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }

        return studentIds.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf
                ));
    }

    private void validateEnrollmentLimit(Course course, List<UUID> targetStudentIds) {
        Integer enrollmentLimit = course.getEnrollmentLimit();
        if (enrollmentLimit == null) {
            return;
        }

        int targetSize = targetStudentIds != null ? targetStudentIds.size() : 0;
        if (targetSize > enrollmentLimit) {
            throw new BusinessException(
                    "COURSE_ENROLLMENT_LIMIT_EXCEEDED",
                    "Student count exceeds course enrollment limit: " + enrollmentLimit
            );
        }
    }

    private UUID resolveCurrentBranchId() {
        String rawBranchId = TenantContext.getBranchId();
        if (!StringUtils.hasText(rawBranchId)) {
            return null;
        }
        try {
            return UUID.fromString(rawBranchId.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("BRANCH_ID_INVALID", "Invalid branch_id in tenant context");
        }
    }
}
