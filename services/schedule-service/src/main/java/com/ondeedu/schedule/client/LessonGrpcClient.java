package com.ondeedu.schedule.client;

import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.lesson.CreateLessonRequest;
import com.ondeedu.grpc.lesson.DeleteLessonRequest;
import com.ondeedu.grpc.lesson.LessonResponse;
import com.ondeedu.grpc.lesson.LessonServiceGrpc;
import com.ondeedu.schedule.entity.Schedule;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
public class LessonGrpcClient {

    @GrpcClient("lesson-service")
    private LessonServiceGrpc.LessonServiceBlockingStub lessonStub;

    public UUID createGroupLesson(Schedule schedule, LocalDate lessonDate) {
        CreateLessonRequest.Builder builder = CreateLessonRequest.newBuilder()
                .setLessonDate(lessonDate.toString())
                .setStartTime(schedule.getStartTime().toString())
                .setEndTime(schedule.getEndTime().toString())
                .setGroupId(StringValue.of(schedule.getId().toString()))
                .setLessonType("GROUP");

        if (schedule.getTeacherId() != null) {
            builder.setTeacherId(StringValue.of(schedule.getTeacherId().toString()));
        }
        if (schedule.getRoomId() != null) {
            builder.setRoomId(StringValue.of(schedule.getRoomId().toString()));
        }
        if (schedule.getMaxStudents() != null) {
            builder.setCapacity(Int32Value.of(schedule.getMaxStudents()));
        }

        LessonResponse response = executeCreate(builder.build(), schedule.getId(), lessonDate);
        if (!response.getSuccess() || !response.hasLesson()) {
            throw new BusinessException(
                    "SCHEDULE_LESSON_SYNC_FAILED",
                    response.hasMessage() ? response.getMessage().getValue() : "Failed to create lesson for schedule"
            );
        }

        return UUID.fromString(response.getLesson().getId());
    }

    public void deleteLesson(UUID lessonId) {
        DeleteLessonRequest request = DeleteLessonRequest.newBuilder()
                .setLessonId(lessonId.toString())
                .build();

        try {
            ApiResponse response = lessonStub.deleteLesson(request);
            if (!response.getSuccess()) {
                log.warn("Failed to delete lesson {} during compensation: {}", lessonId, response.getMessage());
            }
        } catch (StatusRuntimeException e) {
            log.warn("gRPC deleteLesson failed during compensation for lesson {}: {}", lessonId, e.getStatus());
        }
    }

    private LessonResponse executeCreate(CreateLessonRequest request, UUID scheduleId, LocalDate lessonDate) {
        try {
            return lessonStub.createLesson(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC createLesson failed for schedule {} date {}: {}", scheduleId, lessonDate, e.getStatus());
            throw new BusinessException(
                    "SCHEDULE_LESSON_SYNC_FAILED",
                    "Lesson service is unavailable for schedule lesson generation"
            );
        }
    }
}
