package com.ondeedu.course.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.course.client.PaymentGrpcClient;
import com.ondeedu.course.dto.CourseDto;
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

    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public CourseDto createCourse(CreateCourseRequest request) {
        Course course = courseMapper.toEntity(request);
        course = courseRepository.save(course);
        syncCourseStudents(course, List.of(), normalizeStudentIds(request.getStudentIds()));
        log.info("Created course: {} ({})", course.getName(), course.getId());
        return toDto(course);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "courses", key = "#id")
    public CourseDto getCourse(UUID id) {
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        return toDto(course);
    }

    @Transactional
    @CacheEvict(value = "courses", key = "#id")
    public CourseDto updateCourse(UUID id, UpdateCourseRequest request) {
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        List<UUID> existingStudentIds = getStudentIds(course.getId());
        courseMapper.updateEntity(course, request);
        course = courseRepository.save(course);
        List<UUID> targetStudentIds = request.getStudentIds() != null
                ? normalizeStudentIds(request.getStudentIds())
                : existingStudentIds;
        syncCourseStudents(course, existingStudentIds, targetStudentIds);
        log.info("Updated course: {}", id);
        return toDto(course);
    }

    @Transactional
    @CacheEvict(value = "courses", key = "#id")
    public void deleteCourse(UUID id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        cancelCourseSubscriptions(course, getStudentIds(id));
        courseStudentRepository.deleteByCourseId(id);
        courseRepository.deleteById(id);
        log.info("Deleted course: {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDto> listCourses(CourseStatus status, CourseType type, Pageable pageable) {
        Page<Course> page;
        if (status != null && type != null) {
            page = courseRepository.findByStatusAndType(status, type, pageable);
        } else if (status != null) {
            page = courseRepository.findByStatus(status, pageable);
        } else if (type != null) {
            page = courseRepository.findByType(type, pageable);
        } else {
            page = courseRepository.findAll(pageable);
        }
        return mapPage(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDto> searchCourses(String query, Pageable pageable) {
        Page<Course> page = courseRepository.search(query, pageable);
        return mapPage(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDto> getCoursesByTeacher(UUID teacherId, Pageable pageable) {
        Page<Course> page = courseRepository.findByTeacherId(teacherId, pageable);
        return mapPage(page);
    }

    private PageResponse<CourseDto> mapPage(Page<Course> page) {
        Map<UUID, List<UUID>> studentIdsByCourseId = loadStudentIds(page.getContent());
        return PageResponse.from(page,
                course -> toDto(course, studentIdsByCourseId.getOrDefault(course.getId(), List.of())));
    }

    private CourseDto toDto(Course course) {
        Map<UUID, List<UUID>> studentIdsByCourseId = loadStudentIds(List.of(course));
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

        syncCourseSubscriptions(course, targetStudentIds, removedStudentIds);

        if (!removedStudentIds.isEmpty()) {
            courseStudentRepository.deleteByCourseIdAndStudentIdIn(course.getId(), removedStudentIds);
        }

        if (addedStudentIds.isEmpty()) {
            return;
        }

        List<CourseStudent> courseStudents = addedStudentIds.stream()
                .map(studentId -> CourseStudent.builder()
                        .courseId(course.getId())
                        .studentId(studentId)
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

    private List<UUID> getStudentIds(UUID courseId) {
        return courseStudentRepository.findByCourseIdOrderByCreatedAtAsc(courseId).stream()
                .map(CourseStudent::getStudentId)
                .toList();
    }

    private Map<UUID, List<UUID>> loadStudentIds(Collection<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> courseIds = courses.stream()
                .map(Course::getId)
                .toList();

        return courseStudentRepository.findByCourseIdInOrderByCourseIdAscCreatedAtAsc(courseIds).stream()
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
}
