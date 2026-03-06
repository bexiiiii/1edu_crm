package com.ondeedu.task.grpc;

import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.task.*;
import com.ondeedu.task.dto.CreateTaskRequest;
import com.ondeedu.task.dto.TaskDto;
import com.ondeedu.task.dto.UpdateTaskRequest;
import com.ondeedu.task.entity.TaskPriority;
import com.ondeedu.task.entity.TaskStatus;
import com.ondeedu.task.service.TaskService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class TaskGrpcService extends TaskServiceGrpc.TaskServiceImplBase {

    private final TaskService taskService;

    @Override
    public void createTask(com.ondeedu.grpc.task.CreateTaskRequest request,
                           StreamObserver<TaskResponse> responseObserver) {
        try {
            CreateTaskRequest dto = CreateTaskRequest.builder()
                .title(request.getTitle())
                .description(request.hasDescription() ? request.getDescription().getValue() : null)
                .priority(request.hasPriority() ? TaskPriority.valueOf(request.getPriority().getValue()) : null)
                .assignedTo(request.hasAssignedTo() ? UUID.fromString(request.getAssignedTo().getValue()) : null)
                .dueDate(request.hasDueDate() ?
                    GrpcUtils.fromTimestamp(request.getDueDate()).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : null)
                .notes(request.hasNotes() ? request.getNotes().getValue() : null)
                .build();

            TaskDto task = taskService.createTask(dto);

            responseObserver.onNext(buildTaskResponse(true, "Task created successfully", task));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error creating task via gRPC", e);
            responseObserver.onNext(TaskResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getTask(GetTaskRequest request,
                        StreamObserver<TaskResponse> responseObserver) {
        try {
            TaskDto task = taskService.getTask(UUID.fromString(request.getTaskId()));
            responseObserver.onNext(buildTaskResponse(true, null, task));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error getting task via gRPC", e);
            responseObserver.onNext(TaskResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateTask(com.ondeedu.grpc.task.UpdateTaskRequest request,
                           StreamObserver<TaskResponse> responseObserver) {
        try {
            UpdateTaskRequest dto = UpdateTaskRequest.builder()
                .title(request.hasTitle() ? request.getTitle().getValue() : null)
                .description(request.hasDescription() ? request.getDescription().getValue() : null)
                .status(request.hasStatus() ? TaskStatus.valueOf(request.getStatus().getValue()) : null)
                .priority(request.hasPriority() ? TaskPriority.valueOf(request.getPriority().getValue()) : null)
                .assignedTo(request.hasAssignedTo() ? UUID.fromString(request.getAssignedTo().getValue()) : null)
                .dueDate(request.hasDueDate() ?
                    GrpcUtils.fromTimestamp(request.getDueDate()).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : null)
                .notes(request.hasNotes() ? request.getNotes().getValue() : null)
                .build();

            TaskDto task = taskService.updateTask(UUID.fromString(request.getTaskId()), dto);

            responseObserver.onNext(buildTaskResponse(true, "Task updated successfully", task));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error updating task via gRPC", e);
            responseObserver.onNext(TaskResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteTask(DeleteTaskRequest request,
                           StreamObserver<ApiResponse> responseObserver) {
        try {
            taskService.deleteTask(UUID.fromString(request.getTaskId()));
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Task deleted successfully")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error deleting task via gRPC", e);
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listTasks(ListTasksRequest request,
                          StreamObserver<ListTasksResponse> responseObserver) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            TaskStatus status = request.hasStatus() ?
                TaskStatus.valueOf(request.getStatus().getValue()) : null;

            var page = taskService.listTasks(status, pageable);

            ListTasksResponse.Builder responseBuilder = ListTasksResponse.newBuilder()
                .setSuccess(true);

            page.getContent().forEach(dto -> responseBuilder.addTasks(toGrpcTask(dto)));

            responseBuilder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage())
                .setSize(page.getSize())
                .setTotalElements(page.getTotalElements())
                .setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext())
                .setHasPrevious(page.isHasPrevious())
                .build());

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error listing tasks via gRPC", e);
            responseObserver.onNext(ListTasksResponse.newBuilder()
                .setSuccess(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listTasksByAssignee(ListTasksByAssigneeRequest request,
                                    StreamObserver<ListTasksResponse> responseObserver) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            UUID assignedTo = UUID.fromString(request.getAssignedTo());
            TaskStatus status = request.hasStatus() ?
                TaskStatus.valueOf(request.getStatus().getValue()) : null;

            var page = taskService.listByAssignee(assignedTo, status, pageable);

            ListTasksResponse.Builder responseBuilder = ListTasksResponse.newBuilder()
                .setSuccess(true);

            page.getContent().forEach(dto -> responseBuilder.addTasks(toGrpcTask(dto)));

            responseBuilder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage())
                .setSize(page.getSize())
                .setTotalElements(page.getTotalElements())
                .setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext())
                .setHasPrevious(page.isHasPrevious())
                .build());

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error listing tasks by assignee via gRPC", e);
            responseObserver.onNext(ListTasksResponse.newBuilder()
                .setSuccess(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listOverdueTasks(ListOverdueTasksRequest request,
                                 StreamObserver<ListTasksResponse> responseObserver) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            var page = taskService.listOverdue(pageable);

            ListTasksResponse.Builder responseBuilder = ListTasksResponse.newBuilder()
                .setSuccess(true);

            page.getContent().forEach(dto -> responseBuilder.addTasks(toGrpcTask(dto)));

            responseBuilder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage())
                .setSize(page.getSize())
                .setTotalElements(page.getTotalElements())
                .setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext())
                .setHasPrevious(page.isHasPrevious())
                .build());

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error listing overdue tasks via gRPC", e);
            responseObserver.onNext(ListTasksResponse.newBuilder()
                .setSuccess(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    private TaskResponse buildTaskResponse(boolean success, String message, TaskDto dto) {
        TaskResponse.Builder builder = TaskResponse.newBuilder()
            .setSuccess(success);
        if (message != null) {
            builder.setMessage(StringValue.of(message));
        }
        if (dto != null) {
            builder.setTask(toGrpcTask(dto));
        }
        return builder.build();
    }

    private Task toGrpcTask(TaskDto dto) {
        Task.Builder builder = Task.newBuilder()
            .setId(dto.getId().toString())
            .setTitle(dto.getTitle())
            .setStatus(dto.getStatus().name())
            .setPriority(dto.getPriority().name());

        if (dto.getDescription() != null) builder.setDescription(StringValue.of(dto.getDescription()));
        if (dto.getAssignedTo() != null) builder.setAssignedTo(StringValue.of(dto.getAssignedTo().toString()));
        if (dto.getDueDate() != null) builder.setDueDate(GrpcUtils.toTimestamp(
            dto.getDueDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        if (dto.getNotes() != null) builder.setNotes(StringValue.of(dto.getNotes()));
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }
}
