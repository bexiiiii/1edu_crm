package com.ondeedu.course.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseStudentRepository courseStudentRepository;
    private final CourseMapper courseMapper;

    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public CourseDto createCourse(CreateCourseRequest request) {
        Course course = courseMapper.toEntity(request);
        course = courseRepository.save(course);
        syncCourseStudents(course.getId(), request.getStudentIds());
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
        courseMapper.updateEntity(course, request);
        course = courseRepository.save(course);
        if (request.getStudentIds() != null) {
            syncCourseStudents(id, request.getStudentIds());
        }
        log.info("Updated course: {}", id);
        return toDto(course);
    }

    @Transactional
    @CacheEvict(value = "courses", key = "#id")
    public void deleteCourse(UUID id) {
        if (!courseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Course", "id", id);
        }
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

    private void syncCourseStudents(UUID courseId, List<UUID> studentIds) {
        courseStudentRepository.deleteByCourseId(courseId);

        List<UUID> normalizedStudentIds = normalizeStudentIds(studentIds);
        if (normalizedStudentIds.isEmpty()) {
            return;
        }

        List<CourseStudent> courseStudents = normalizedStudentIds.stream()
                .map(studentId -> CourseStudent.builder()
                        .courseId(courseId)
                        .studentId(studentId)
                        .build())
                .toList();
        courseStudentRepository.saveAll(courseStudents);
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
