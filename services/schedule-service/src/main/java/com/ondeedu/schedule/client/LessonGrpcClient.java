package com.ondeedu.schedule.client;

import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.common.PageRequest;
import com.ondeedu.grpc.lesson.CreateLessonRequest;
import com.ondeedu.grpc.lesson.DeleteLessonRequest;
import com.ondeedu.grpc.lesson.Lesson;
import com.ondeedu.grpc.lesson.LessonResponse;
import com.ondeedu.grpc.lesson.LessonServiceGrpc;
import com.ondeedu.grpc.lesson.ListLessonsByGroupRequest;
import com.ondeedu.grpc.lesson.ListLessonsResponse;
import com.ondeedu.grpc.lesson.UpdateLessonRequest;
import com.ondeedu.schedule.entity.Schedule;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class LessonGrpcClient {

    private static final int LESSON_PAGE_SIZE = 500;

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

    public void restoreGroupLesson(UUID scheduleId, ManagedLesson lesson) {
        CreateLessonRequest.Builder builder = CreateLessonRequest.newBuilder()
                .setLessonDate(lesson.lessonDate().toString())
                .setStartTime(lesson.startTime().toString())
                .setEndTime(lesson.endTime().toString())
                .setGroupId(StringValue.of(scheduleId.toString()))
                .setLessonType("GROUP");

        if (lesson.teacherId() != null) {
            builder.setTeacherId(StringValue.of(lesson.teacherId().toString()));
        }
        if (lesson.roomId() != null) {
            builder.setRoomId(StringValue.of(lesson.roomId().toString()));
        }
        if (lesson.capacity() != null) {
            builder.setCapacity(Int32Value.of(lesson.capacity()));
        }
        if (lesson.topic() != null && !lesson.topic().isBlank()) {
            builder.setTopic(StringValue.of(lesson.topic()));
        }
        if (lesson.homework() != null && !lesson.homework().isBlank()) {
            builder.setHomework(StringValue.of(lesson.homework()));
        }
        if (lesson.notes() != null && !lesson.notes().isBlank()) {
            builder.setNotes(StringValue.of(lesson.notes()));
        }

        executeCreate(builder.build(), scheduleId, lesson.lessonDate());
    }

    public void updateGroupLesson(UUID lessonId, Schedule schedule, LocalDate lessonDate) {
        UpdateLessonRequest.Builder builder = UpdateLessonRequest.newBuilder()
                .setLessonId(lessonId.toString())
                .setLessonDate(StringValue.of(lessonDate.toString()))
                .setStartTime(StringValue.of(schedule.getStartTime().toString()))
                .setEndTime(StringValue.of(schedule.getEndTime().toString()))
                .setGroupId(StringValue.of(schedule.getId().toString()));

        if (schedule.getTeacherId() != null) {
            builder.setTeacherId(StringValue.of(schedule.getTeacherId().toString()));
        }
        if (schedule.getRoomId() != null) {
            builder.setRoomId(StringValue.of(schedule.getRoomId().toString()));
        }
        if (schedule.getMaxStudents() != null) {
            builder.setCapacity(Int32Value.of(schedule.getMaxStudents()));
        }

        executeUpdate(builder.build(), lessonId);
    }

    public void restoreLesson(UUID lessonId, ManagedLesson lesson, UUID scheduleId) {
        UpdateLessonRequest.Builder builder = UpdateLessonRequest.newBuilder()
                .setLessonId(lessonId.toString())
                .setLessonDate(StringValue.of(lesson.lessonDate().toString()))
                .setStartTime(StringValue.of(lesson.startTime().toString()))
                .setEndTime(StringValue.of(lesson.endTime().toString()))
                .setGroupId(StringValue.of(scheduleId.toString()));

        if (lesson.teacherId() != null) {
            builder.setTeacherId(StringValue.of(lesson.teacherId().toString()));
        }
        if (lesson.roomId() != null) {
            builder.setRoomId(StringValue.of(lesson.roomId().toString()));
        }
        if (lesson.capacity() != null) {
            builder.setCapacity(Int32Value.of(lesson.capacity()));
        }
        if (lesson.status() != null) {
            builder.setStatus(StringValue.of(lesson.status()));
        }
        if (lesson.topic() != null && !lesson.topic().isBlank()) {
            builder.setTopic(StringValue.of(lesson.topic()));
        }
        if (lesson.homework() != null && !lesson.homework().isBlank()) {
            builder.setHomework(StringValue.of(lesson.homework()));
        }
        if (lesson.notes() != null && !lesson.notes().isBlank()) {
            builder.setNotes(StringValue.of(lesson.notes()));
        }

        executeUpdate(builder.build(), lessonId);
    }

    public List<ManagedLesson> listGroupLessons(UUID scheduleId) {
        List<ManagedLesson> lessons = new ArrayList<>();
        int page = 0;

        while (true) {
            ListLessonsByGroupRequest request = ListLessonsByGroupRequest.newBuilder()
                    .setGroupId(scheduleId.toString())
                    .setPage(PageRequest.newBuilder().setPage(page).setSize(LESSON_PAGE_SIZE).build())
                    .build();

            ListLessonsResponse response = executeListByGroup(request, scheduleId);
            if (!response.getSuccess()) {
                throw new BusinessException(
                        "SCHEDULE_LESSON_SYNC_FAILED",
                        "Failed to fetch lessons for schedule synchronization"
                );
            }

            response.getLessonsList().stream()
                    .map(this::toManagedLesson)
                    .forEach(lessons::add);

            if (!response.hasPageInfo() || !response.getPageInfo().getHasNext()) {
                return lessons;
            }
            page++;
        }
    }

    public void deleteLesson(UUID lessonId) {
        DeleteLessonRequest request = DeleteLessonRequest.newBuilder()
                .setLessonId(lessonId.toString())
                .build();

        try {
            ApiResponse response = lessonStub.deleteLesson(request);
            if (!response.getSuccess()) {
                throw new BusinessException(
                        "SCHEDULE_LESSON_SYNC_FAILED",
                        response.getMessage().isBlank() ? "Failed to delete lesson for schedule synchronization" : response.getMessage()
                );
            }
        } catch (StatusRuntimeException e) {
            log.error("gRPC deleteLesson failed for lesson {}: {}", lessonId, e.getStatus());
            throw new BusinessException(
                    "SCHEDULE_LESSON_SYNC_FAILED",
                    "Lesson service is unavailable for schedule lesson synchronization"
            );
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

    private void executeUpdate(UpdateLessonRequest request, UUID lessonId) {
        try {
            LessonResponse response = lessonStub.updateLesson(request);
            if (!response.getSuccess() || !response.hasLesson()) {
                throw new BusinessException(
                        "SCHEDULE_LESSON_SYNC_FAILED",
                        response.hasMessage() ? response.getMessage().getValue() : "Failed to update lesson for schedule"
                );
            }
        } catch (StatusRuntimeException e) {
            log.error("gRPC updateLesson failed for lesson {}: {}", lessonId, e.getStatus());
            throw new BusinessException(
                    "SCHEDULE_LESSON_SYNC_FAILED",
                    "Lesson service is unavailable for schedule lesson synchronization"
            );
        }
    }

    private ListLessonsResponse executeListByGroup(ListLessonsByGroupRequest request, UUID scheduleId) {
        try {
            return lessonStub.listLessonsByGroup(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC listLessonsByGroup failed for schedule {}: {}", scheduleId, e.getStatus());
            throw new BusinessException(
                    "SCHEDULE_LESSON_SYNC_FAILED",
                    "Lesson service is unavailable for schedule lesson synchronization"
            );
        }
    }

    private ManagedLesson toManagedLesson(Lesson lesson) {
        return new ManagedLesson(
                UUID.fromString(lesson.getId()),
                LocalDate.parse(lesson.getLessonDate()),
                LocalTime.parse(lesson.getStartTime()),
                LocalTime.parse(lesson.getEndTime()),
                lesson.hasTeacherId() ? UUID.fromString(lesson.getTeacherId().getValue()) : null,
                lesson.hasRoomId() ? UUID.fromString(lesson.getRoomId().getValue()) : null,
                lesson.hasCapacity() ? lesson.getCapacity().getValue() : null,
                lesson.getStatus(),
                lesson.hasTopic() ? lesson.getTopic().getValue() : null,
                lesson.hasHomework() ? lesson.getHomework().getValue() : null,
                lesson.hasNotes() ? lesson.getNotes().getValue() : null
        );
    }

    public record ManagedLesson(
            UUID id,
            LocalDate lessonDate,
            LocalTime startTime,
            LocalTime endTime,
            UUID teacherId,
            UUID roomId,
            Integer capacity,
            String status,
            String topic,
            String homework,
            String notes
    ) {
        public boolean isPlanned() {
            return "PLANNED".equals(status);
        }
    }
}
