package com.ondeedu.task.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.task.dto.*;
import com.ondeedu.task.entity.TaskStatus;
import com.ondeedu.task.service.TaskService;
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
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task management API")
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('TASKS_CREATE')")
    @Operation(summary = "Create a new task")
    public ApiResponse<TaskDto> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskDto task = taskService.createTask(request);
        return ApiResponse.success(task, "Task created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('TASKS_VIEW')")
    @Operation(summary = "Get task by ID")
    public ApiResponse<TaskDto> getTask(@PathVariable UUID id) {
        return ApiResponse.success(taskService.getTask(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('TASKS_EDIT')")
    @Operation(summary = "Update task")
    public ApiResponse<TaskDto> updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request) {
        TaskDto task = taskService.updateTask(id, request);
        return ApiResponse.success(task, "Task updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('TASKS_DELETE')")
    @Operation(summary = "Delete task")
    public ApiResponse<Void> deleteTask(@PathVariable UUID id) {
        taskService.deleteTask(id);
        return ApiResponse.success("Task deleted successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('TASKS_VIEW')")
    @Operation(summary = "List tasks with optional status filter")
    public ApiResponse<PageResponse<TaskDto>> listTasks(
            @RequestParam(required = false) TaskStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(taskService.listTasks(status, pageable));
    }

    @GetMapping("/assignee/{assignedTo}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('TASKS_VIEW')")
    @Operation(summary = "List tasks by assignee (staff member)")
    public ApiResponse<PageResponse<TaskDto>> listByAssignee(
            @PathVariable UUID assignedTo,
            @RequestParam(required = false) TaskStatus status,
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(taskService.listByAssignee(assignedTo, status, pageable));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('TASKS_VIEW')")
    @Operation(summary = "List overdue tasks")
    public ApiResponse<PageResponse<TaskDto>> listOverdue(
            @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ApiResponse.success(taskService.listOverdue(pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('TASKS_VIEW')")
    @Operation(summary = "Search tasks")
    public ApiResponse<PageResponse<TaskDto>> searchTasks(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(taskService.searchTasks(query, pageable));
    }
}
