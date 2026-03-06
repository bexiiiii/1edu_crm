package com.ondeedu.course.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.course.dto.CourseDto;
import com.ondeedu.course.dto.CreateCourseRequest;
import com.ondeedu.course.dto.UpdateCourseRequest;
import com.ondeedu.course.entity.CourseStatus;
import com.ondeedu.course.entity.CourseType;
import com.ondeedu.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "Courses", description = "Course management API")
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_CREATE')")
    @Operation(summary = "Create a new course")
    public ApiResponse<CourseDto> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        return ApiResponse.success(courseService.createCourse(request), "Course created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_VIEW')")
    @Operation(summary = "Get course by ID")
    public ApiResponse<CourseDto> getCourse(@PathVariable UUID id) {
        return ApiResponse.success(courseService.getCourse(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_EDIT')")
    @Operation(summary = "Update course")
    public ApiResponse<CourseDto> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest request) {
        return ApiResponse.success(courseService.updateCourse(id, request), "Course updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_DELETE')")
    @Operation(summary = "Delete course")
    public ApiResponse<Void> deleteCourse(@PathVariable UUID id) {
        courseService.deleteCourse(id);
        return ApiResponse.success("Course deleted successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_VIEW')")
    @Operation(summary = "List courses with pagination and filtering")
    public ApiResponse<PageResponse<CourseDto>> listCourses(
            @RequestParam(required = false) CourseStatus status,
            @RequestParam(required = false) CourseType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(courseService.listCourses(status, type, pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_VIEW')")
    @Operation(summary = "Search courses by name or description")
    public ApiResponse<PageResponse<CourseDto>> searchCourses(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(courseService.searchCourses(query, pageable));
    }

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('GROUPS_VIEW')")
    @Operation(summary = "Get courses by teacher")
    public ApiResponse<PageResponse<CourseDto>> getCoursesByTeacher(
            @PathVariable UUID teacherId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(courseService.getCoursesByTeacher(teacherId, pageable));
    }
}
