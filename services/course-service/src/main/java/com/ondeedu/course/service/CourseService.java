package com.ondeedu.course.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.course.dto.CourseDto;
import com.ondeedu.course.dto.CreateCourseRequest;
import com.ondeedu.course.dto.UpdateCourseRequest;
import com.ondeedu.course.entity.Course;
import com.ondeedu.course.entity.CourseStatus;
import com.ondeedu.course.entity.CourseType;
import com.ondeedu.course.mapper.CourseMapper;
import com.ondeedu.course.repository.CourseRepository;
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
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;

    @Transactional
    @CacheEvict(value = "courses", allEntries = true)
    public CourseDto createCourse(CreateCourseRequest request) {
        Course course = courseMapper.toEntity(request);
        course = courseRepository.save(course);
        log.info("Created course: {} ({})", course.getName(), course.getId());
        return courseMapper.toDto(course);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "courses", key = "#id")
    public CourseDto getCourse(UUID id) {
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        return courseMapper.toDto(course);
    }

    @Transactional
    @CacheEvict(value = "courses", key = "#id")
    public CourseDto updateCourse(UUID id, UpdateCourseRequest request) {
        Course course = courseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        courseMapper.updateEntity(course, request);
        course = courseRepository.save(course);
        log.info("Updated course: {}", id);
        return courseMapper.toDto(course);
    }

    @Transactional
    @CacheEvict(value = "courses", key = "#id")
    public void deleteCourse(UUID id) {
        if (!courseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Course", "id", id);
        }
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
        return PageResponse.from(page, courseMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDto> searchCourses(String query, Pageable pageable) {
        Page<Course> page = courseRepository.search(query, pageable);
        return PageResponse.from(page, courseMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseDto> getCoursesByTeacher(UUID teacherId, Pageable pageable) {
        Page<Course> page = courseRepository.findByTeacherId(teacherId, pageable);
        return PageResponse.from(page, courseMapper::toDto);
    }
}
