package com.ondeedu.lesson.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.lesson.dto.CompleteLessonRequest;
import com.ondeedu.lesson.dto.CreateLessonRequest;
import com.ondeedu.lesson.dto.LessonDto;
import com.ondeedu.lesson.dto.RescheduleLessonRequest;
import com.ondeedu.lesson.dto.UpdateLessonRequest;
import com.ondeedu.lesson.entity.LessonStatus;
import com.ondeedu.lesson.entity.LessonType;
import com.ondeedu.lesson.service.LessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lessons")
@RequiredArgsConstructor
@Tag(name = "Lessons", description = "Lesson management API")
public class LessonController {

    private final LessonService lessonService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_CREATE')")
    @Operation(summary = "Create a new lesson")
    public ApiResponse<LessonDto> createLesson(@Valid @RequestBody CreateLessonRequest request) {
        LessonDto lesson = lessonService.createLesson(request);
        return ApiResponse.success(lesson, "Lesson created successfully");
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Get all lessons in date range for calendar view")
    public ApiResponse<List<LessonDto>> listCalendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(lessonService.listCalendar(from, to));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Get lesson by ID")
    public ApiResponse<LessonDto> getLesson(@PathVariable UUID id) {
        return ApiResponse.success(lessonService.getLesson(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_EDIT')")
    @Operation(summary = "Update lesson")
    public ApiResponse<LessonDto> updateLesson(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLessonRequest request) {
        LessonDto lesson = lessonService.updateLesson(id, request);
        return ApiResponse.success(lesson, "Lesson updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_DELETE')")
    @Operation(summary = "Delete lesson")
    public ApiResponse<Void> deleteLesson(@PathVariable UUID id) {
        lessonService.deleteLesson(id);
        return ApiResponse.success("Lesson deleted successfully");
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_EDIT')")
    @Operation(summary = "Mark lesson as completed")
    public ApiResponse<LessonDto> completeLesson(
            @PathVariable UUID id,
            @RequestBody CompleteLessonRequest request) {
        LessonDto lesson = lessonService.completeLesson(id, request.getTopic(), request.getHomework());
        return ApiResponse.success(lesson, "Lesson completed successfully");
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_EDIT')")
    @Operation(summary = "Cancel a lesson")
    public ApiResponse<LessonDto> cancelLesson(@PathVariable UUID id) {
        LessonDto lesson = lessonService.cancelLesson(id);
        return ApiResponse.success(lesson, "Lesson cancelled successfully");
    }

    @PostMapping("/{id}/teacher-absent")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_EDIT')")
    @Operation(summary = "Mark lesson as teacher did not show up")
    public ApiResponse<LessonDto> markTeacherAbsent(@PathVariable UUID id) {
        LessonDto lesson = lessonService.markTeacherAbsent(id);
        return ApiResponse.success(lesson, "Lesson marked as teacher absent");
    }

    @PostMapping("/{id}/teacher-sick")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_EDIT')")
    @Operation(summary = "Mark lesson as teacher sick")
    public ApiResponse<LessonDto> markTeacherSick(@PathVariable UUID id) {
        LessonDto lesson = lessonService.markTeacherSick(id);
        return ApiResponse.success(lesson, "Lesson marked as teacher sick");
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_EDIT')")
    @Operation(summary = "Reschedule a lesson to a new date and time")
    public ApiResponse<LessonDto> rescheduleLesson(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleLessonRequest request) {
        LessonDto lesson = lessonService.rescheduleLesson(id, request);
        return ApiResponse.success(lesson, "Lesson rescheduled successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "List lessons with optional filters")
    public ApiResponse<PageResponse<LessonDto>> listLessons(
            @RequestParam(required = false) LessonType type,
            @RequestParam(required = false) LessonStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "lessonDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(lessonService.listLessons(type, status, date, from, to, pageable));
    }

    @GetMapping("/group/{groupId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Get lessons by group with optional date range")
    public ApiResponse<PageResponse<LessonDto>> listByGroup(
            @PathVariable UUID groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "lessonDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(lessonService.listByGroup(groupId, from, to, pageable));
    }

    @GetMapping("/teacher/{teacherId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LESSONS_VIEW')")
    @Operation(summary = "Get lessons by teacher")
    public ApiResponse<PageResponse<LessonDto>> listByTeacher(
            @PathVariable UUID teacherId,
            @PageableDefault(size = 20, sort = "lessonDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(lessonService.listByTeacher(teacherId, pageable));
    }
}
