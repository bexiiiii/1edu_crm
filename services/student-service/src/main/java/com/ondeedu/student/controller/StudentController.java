package com.ondeedu.student.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.student.dto.*;
import com.ondeedu.student.entity.StudentStatus;
import com.ondeedu.student.service.StudentService;
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
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Tag(name = "Students", description = "Student management API")
public class StudentController {

    private final StudentService studentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_CREATE')")
    @Operation(summary = "Create a new student")
    public ApiResponse<StudentDto> createStudent(@Valid @RequestBody CreateStudentRequest request) {
        StudentDto student = studentService.createStudent(request);
        return ApiResponse.success(student, "Student created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_VIEW')")
    @Operation(summary = "Get student by ID")
    public ApiResponse<StudentDto> getStudent(@PathVariable UUID id) {
        return ApiResponse.success(studentService.getStudent(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_EDIT')")
    @Operation(summary = "Update student")
    public ApiResponse<StudentDto> updateStudent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStudentRequest request) {
        StudentDto student = studentService.updateStudent(id, request);
        return ApiResponse.success(student, "Student updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_DELETE')")
    @Operation(summary = "Delete student")
    public ApiResponse<Void> deleteStudent(@PathVariable UUID id) {
        studentService.deleteStudent(id);
        return ApiResponse.success("Student deleted successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_VIEW')")
    @Operation(summary = "List students with pagination")
    public ApiResponse<PageResponse<StudentDto>> listStudents(
            @RequestParam(required = false) StudentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(studentService.listStudents(status, pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_VIEW')")
    @Operation(summary = "Search students")
    public ApiResponse<PageResponse<StudentDto>> searchStudents(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(studentService.searchStudents(query, pageable));
    }

    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_VIEW')")
    @Operation(summary = "Get students by group")
    public ApiResponse<PageResponse<StudentDto>> getStudentsByGroup(
            @PathVariable UUID groupId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.success(studentService.getStudentsByGroup(groupId, pageable));
    }

    @PostMapping("/{studentId}/groups/{groupId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_EDIT')")
    @Operation(summary = "Add student to group")
    public ApiResponse<Void> addStudentToGroup(
            @PathVariable UUID studentId,
            @PathVariable UUID groupId) {
        studentService.addStudentToGroup(studentId, groupId);
        return ApiResponse.success("Student added to group successfully");
    }

    @DeleteMapping("/{studentId}/groups/{groupId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_EDIT')")
    @Operation(summary = "Remove student from group")
    public ApiResponse<Void> removeStudentFromGroup(
            @PathVariable UUID studentId,
            @PathVariable UUID groupId) {
        studentService.removeStudentFromGroup(studentId, groupId);
        return ApiResponse.success("Student removed from group successfully");
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('STUDENTS_VIEW')")
    @Operation(summary = "Get student statistics")
    public ApiResponse<StudentStatsDto> getStats() {
        return ApiResponse.success(studentService.getStats());
    }
}