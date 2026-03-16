package com.ondeedu.task.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.task.dto.CreateTaskRequest;
import com.ondeedu.task.dto.TaskDto;
import com.ondeedu.task.dto.UpdateTaskRequest;
import com.ondeedu.task.entity.Task;
import com.ondeedu.task.entity.TaskStatus;
import com.ondeedu.task.mapper.TaskMapper;
import com.ondeedu.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskMapper taskMapper;
    private final TaskAssignmentNotificationService taskAssignmentNotificationService;

    @Transactional
    public TaskDto createTask(CreateTaskRequest request) {
        Task task = taskMapper.toEntity(request);
        task = taskRepository.save(task);
        taskAssignmentNotificationService.notifyIfAssigned(null, task);
        log.info("Created task: {}", task.getTitle());
        return taskMapper.toDto(task);
    }

    @Transactional(readOnly = true)
    public TaskDto getTask(UUID id) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task", "id", id));
        return taskMapper.toDto(task);
    }

    @Transactional
    public TaskDto updateTask(UUID id, UpdateTaskRequest request) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Task", "id", id));
        UUID previousAssignedTo = task.getAssignedTo();
        taskMapper.updateEntity(task, request);
        task = taskRepository.save(task);
        taskAssignmentNotificationService.notifyIfAssigned(previousAssignedTo, task);
        log.info("Updated task: {}", id);
        return taskMapper.toDto(task);
    }

    @Transactional
    public void deleteTask(UUID id) {
        if (!taskRepository.existsById(id)) {
            throw new ResourceNotFoundException("Task", "id", id);
        }
        taskRepository.deleteById(id);
        log.info("Deleted task: {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDto> listTasks(TaskStatus status, Pageable pageable) {
        Page<Task> page;
        if (status != null) {
            page = taskRepository.findByStatus(status, pageable);
        } else {
            page = taskRepository.findAll(pageable);
        }
        return PageResponse.from(page, taskMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDto> listByAssignee(UUID assignedTo, TaskStatus status, Pageable pageable) {
        Page<Task> page;
        if (status != null) {
            page = taskRepository.findByAssignedToAndStatus(assignedTo, status, pageable);
        } else {
            page = taskRepository.findByAssignedTo(assignedTo, pageable);
        }
        return PageResponse.from(page, taskMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDto> listOverdue(Pageable pageable) {
        Page<Task> page = taskRepository.findOverdue(LocalDate.now(), pageable);
        return PageResponse.from(page, taskMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskDto> searchTasks(String query, Pageable pageable) {
        Page<Task> page = taskRepository.search(query, pageable);
        return PageResponse.from(page, taskMapper::toDto);
    }
}
