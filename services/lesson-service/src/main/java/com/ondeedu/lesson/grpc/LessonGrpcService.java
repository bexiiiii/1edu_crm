package com.ondeedu.lesson.grpc;

import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.lesson.*;
import com.ondeedu.lesson.dto.AttendanceDto;
import com.ondeedu.lesson.dto.LessonDto;
import com.ondeedu.lesson.entity.AttendanceStatus;
import com.ondeedu.lesson.entity.LessonStatus;
import com.ondeedu.lesson.entity.LessonType;
import com.ondeedu.lesson.service.AttendanceService;
import com.ondeedu.lesson.service.LessonService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class LessonGrpcService extends LessonServiceGrpc.LessonServiceImplBase {

    private final LessonService lessonService;
    private final AttendanceService attendanceService;

    @Override
    public void createLesson(CreateLessonRequest request, StreamObserver<LessonResponse> observer) {
        try {
            com.ondeedu.lesson.dto.CreateLessonRequest dto = new com.ondeedu.lesson.dto.CreateLessonRequest();
            dto.setLessonDate(LocalDate.parse(request.getLessonDate()));
            dto.setStartTime(LocalTime.parse(request.getStartTime()));
            dto.setEndTime(LocalTime.parse(request.getEndTime()));
            if (request.hasGroupId()) dto.setGroupId(UUID.fromString(request.getGroupId().getValue()));
            if (request.hasServiceId()) dto.setServiceId(UUID.fromString(request.getServiceId().getValue()));
            if (request.hasTeacherId()) dto.setTeacherId(UUID.fromString(request.getTeacherId().getValue()));
            if (request.hasRoomId()) dto.setRoomId(UUID.fromString(request.getRoomId().getValue()));
            if (!request.getLessonType().isEmpty()) dto.setLessonType(LessonType.valueOf(request.getLessonType()));
            if (request.hasCapacity()) dto.setCapacity(request.getCapacity().getValue());
            if (request.hasTopic()) dto.setTopic(request.getTopic().getValue());
            if (request.hasHomework()) dto.setHomework(request.getHomework().getValue());
            if (request.hasNotes()) dto.setNotes(request.getNotes().getValue());

            LessonDto created = lessonService.createLesson(dto);
            observer.onNext(buildResponse(true, "Lesson created", created));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC createLesson error", e);
            observer.onNext(LessonResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getLesson(GetLessonRequest request, StreamObserver<LessonResponse> observer) {
        try {
            LessonDto dto = lessonService.getLesson(UUID.fromString(request.getLessonId()));
            observer.onNext(buildResponse(true, null, dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getLesson error", e);
            observer.onNext(LessonResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void updateLesson(UpdateLessonRequest request, StreamObserver<LessonResponse> observer) {
        try {
            com.ondeedu.lesson.dto.UpdateLessonRequest dto = new com.ondeedu.lesson.dto.UpdateLessonRequest();
            if (request.hasLessonDate()) dto.setLessonDate(LocalDate.parse(request.getLessonDate().getValue()));
            if (request.hasStartTime()) dto.setStartTime(LocalTime.parse(request.getStartTime().getValue()));
            if (request.hasEndTime()) dto.setEndTime(LocalTime.parse(request.getEndTime().getValue()));
            if (request.hasStatus()) dto.setStatus(LessonStatus.valueOf(request.getStatus().getValue()));
            if (request.hasTopic()) dto.setTopic(request.getTopic().getValue());
            if (request.hasHomework()) dto.setHomework(request.getHomework().getValue());
            if (request.hasNotes()) dto.setNotes(request.getNotes().getValue());

            LessonDto updated = lessonService.updateLesson(UUID.fromString(request.getLessonId()), dto);
            observer.onNext(buildResponse(true, "Lesson updated", updated));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC updateLesson error", e);
            observer.onNext(LessonResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void deleteLesson(DeleteLessonRequest request, StreamObserver<ApiResponse> observer) {
        try {
            lessonService.deleteLesson(UUID.fromString(request.getLessonId()));
            observer.onNext(ApiResponse.newBuilder().setSuccess(true).setMessage("Lesson deleted").build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC deleteLesson error", e);
            observer.onNext(ApiResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
            observer.onCompleted();
        }
    }

    @Override
    public void listLessons(ListLessonsRequest request, StreamObserver<ListLessonsResponse> observer) {
        try {
            var pageable = GrpcUtils.toPageable(request.getPage());
            LessonType type = request.hasLessonType() ? LessonType.valueOf(request.getLessonType().getValue()) : null;
            LessonStatus status = request.hasStatus() ? LessonStatus.valueOf(request.getStatus().getValue()) : null;
            LocalDate date = request.hasDate() ? LocalDate.parse(request.getDate().getValue()) : null;

            var page = lessonService.listLessons(type, status, date, null, null, pageable);

            ListLessonsResponse.Builder builder = ListLessonsResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addLessons(toGrpcLesson(dto)));
            builder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage()).setSize(page.getSize())
                .setTotalElements(page.getTotalElements()).setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext()).setHasPrevious(page.isHasPrevious()).build());

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC listLessons error", e);
            observer.onNext(ListLessonsResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    @Override
    public void listLessonsByGroup(ListLessonsByGroupRequest request,
                                   StreamObserver<ListLessonsResponse> observer) {
        try {
            var pageable = GrpcUtils.toPageable(request.getPage());
            LocalDate from = request.hasFromDate() ? LocalDate.parse(request.getFromDate().getValue()) : null;
            LocalDate to = request.hasToDate() ? LocalDate.parse(request.getToDate().getValue()) : null;

            var page = lessonService.listByGroup(UUID.fromString(request.getGroupId()), from, to, pageable);

            ListLessonsResponse.Builder builder = ListLessonsResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addLessons(toGrpcLesson(dto)));
            builder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage()).setSize(page.getSize())
                .setTotalElements(page.getTotalElements()).setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext()).setHasPrevious(page.isHasPrevious()).build());

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC listLessonsByGroup error", e);
            observer.onNext(ListLessonsResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    @Override
    public void listLessonsByTeacher(ListLessonsByTeacherRequest request,
                                     StreamObserver<ListLessonsResponse> observer) {
        try {
            var pageable = GrpcUtils.toPageable(request.getPage());
            var page = lessonService.listByTeacher(UUID.fromString(request.getTeacherId()), pageable);

            ListLessonsResponse.Builder builder = ListLessonsResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addLessons(toGrpcLesson(dto)));
            builder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage()).setSize(page.getSize())
                .setTotalElements(page.getTotalElements()).setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext()).setHasPrevious(page.isHasPrevious()).build());

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC listLessonsByTeacher error", e);
            observer.onNext(ListLessonsResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    @Override
    public void completeLesson(CompleteLessonRequest request, StreamObserver<LessonResponse> observer) {
        try {
            String topic = request.hasTopic() ? request.getTopic().getValue() : null;
            String homework = request.hasHomework() ? request.getHomework().getValue() : null;
            LessonDto dto = lessonService.completeLesson(UUID.fromString(request.getLessonId()), topic, homework);
            observer.onNext(buildResponse(true, "Lesson completed", dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC completeLesson error", e);
            observer.onNext(LessonResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void cancelLesson(CancelLessonRequest request, StreamObserver<LessonResponse> observer) {
        try {
            LessonDto dto = lessonService.cancelLesson(UUID.fromString(request.getLessonId()));
            observer.onNext(buildResponse(true, "Lesson cancelled", dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC cancelLesson error", e);
            observer.onNext(LessonResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void rescheduleLesson(RescheduleLessonGrpcRequest request,
                                 StreamObserver<LessonResponse> observer) {
        try {
            com.ondeedu.lesson.dto.RescheduleLessonRequest dto =
                new com.ondeedu.lesson.dto.RescheduleLessonRequest();
            dto.setNewDate(LocalDate.parse(request.getNewDate()));
            dto.setNewStartTime(LocalTime.parse(request.getNewStartTime()));
            dto.setNewEndTime(LocalTime.parse(request.getNewEndTime()));

            LessonDto updated = lessonService.rescheduleLesson(UUID.fromString(request.getLessonId()), dto);
            observer.onNext(buildResponse(true, "Lesson rescheduled", updated));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC rescheduleLesson error", e);
            observer.onNext(LessonResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void markAttendance(MarkAttendanceGrpcRequest request,
                               StreamObserver<AttendanceResponse> observer) {
        try {
            com.ondeedu.lesson.dto.MarkAttendanceRequest dto =
                new com.ondeedu.lesson.dto.MarkAttendanceRequest();
            dto.setStudentId(UUID.fromString(request.getStudentId()));
            dto.setStatus(AttendanceStatus.valueOf(request.getStatus()));
            if (request.hasNotes()) dto.setNotes(request.getNotes().getValue());

            AttendanceDto result = attendanceService.markAttendance(
                UUID.fromString(request.getLessonId()), dto);

            observer.onNext(AttendanceResponse.newBuilder()
                .setSuccess(true)
                .setAttendance(toGrpcAttendance(result))
                .build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC markAttendance error", e);
            observer.onNext(AttendanceResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void bulkMarkAttendance(BulkMarkAttendanceGrpcRequest request,
                                   StreamObserver<ListAttendanceResponse> observer) {
        try {
            List<com.ondeedu.lesson.dto.MarkAttendanceRequest> entries =
                request.getAttendancesList().stream().map(e -> {
                    com.ondeedu.lesson.dto.MarkAttendanceRequest r =
                        new com.ondeedu.lesson.dto.MarkAttendanceRequest();
                    r.setStudentId(UUID.fromString(e.getStudentId()));
                    r.setStatus(AttendanceStatus.valueOf(e.getStatus()));
                    if (e.hasNotes()) r.setNotes(e.getNotes().getValue());
                    return r;
                }).toList();

            com.ondeedu.lesson.dto.BulkMarkAttendanceRequest bulk =
                new com.ondeedu.lesson.dto.BulkMarkAttendanceRequest();
            bulk.setAttendances(entries);

            List<AttendanceDto> results = attendanceService.bulkMarkAttendance(
                UUID.fromString(request.getLessonId()), bulk);

            ListAttendanceResponse.Builder builder = ListAttendanceResponse.newBuilder().setSuccess(true);
            results.forEach(dto -> builder.addAttendances(toGrpcAttendance(dto)));
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC bulkMarkAttendance error", e);
            observer.onNext(ListAttendanceResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getLessonAttendance(GetLessonAttendanceRequest request,
                                    StreamObserver<ListAttendanceResponse> observer) {
        try {
            List<AttendanceDto> results = attendanceService.getLessonAttendance(
                UUID.fromString(request.getLessonId()));
            ListAttendanceResponse.Builder builder = ListAttendanceResponse.newBuilder().setSuccess(true);
            results.forEach(dto -> builder.addAttendances(toGrpcAttendance(dto)));
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getLessonAttendance error", e);
            observer.onNext(ListAttendanceResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getStudentAttendance(GetStudentAttendanceRequest request,
                                     StreamObserver<ListAttendanceResponse> observer) {
        try {
            var pageable = GrpcUtils.toPageable(request.getPage());
            var page = attendanceService.getStudentAttendance(
                UUID.fromString(request.getStudentId()), pageable);
            ListAttendanceResponse.Builder builder = ListAttendanceResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addAttendances(toGrpcAttendance(dto)));
            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getStudentAttendance error", e);
            observer.onNext(ListAttendanceResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    private LessonResponse buildResponse(boolean success, String message, LessonDto dto) {
        LessonResponse.Builder builder = LessonResponse.newBuilder().setSuccess(success);
        if (message != null) builder.setMessage(StringValue.of(message));
        if (dto != null) builder.setLesson(toGrpcLesson(dto));
        return builder.build();
    }

    private Lesson toGrpcLesson(LessonDto dto) {
        Lesson.Builder builder = Lesson.newBuilder()
            .setId(dto.getId().toString())
            .setLessonDate(dto.getLessonDate().toString())
            .setStartTime(dto.getStartTime().toString())
            .setEndTime(dto.getEndTime().toString())
            .setLessonType(dto.getLessonType().name())
            .setStatus(dto.getStatus().name());

        if (dto.getGroupId() != null) builder.setGroupId(StringValue.of(dto.getGroupId().toString()));
        if (dto.getServiceId() != null) builder.setServiceId(StringValue.of(dto.getServiceId().toString()));
        if (dto.getTeacherId() != null) builder.setTeacherId(StringValue.of(dto.getTeacherId().toString()));
        if (dto.getSubstituteTeacherId() != null)
            builder.setSubstituteTeacherId(StringValue.of(dto.getSubstituteTeacherId().toString()));
        if (dto.getRoomId() != null) builder.setRoomId(StringValue.of(dto.getRoomId().toString()));
        if (dto.getCapacity() != null)
            builder.setCapacity(com.google.protobuf.Int32Value.of(dto.getCapacity()));
        if (dto.getTopic() != null) builder.setTopic(StringValue.of(dto.getTopic()));
        if (dto.getHomework() != null) builder.setHomework(StringValue.of(dto.getHomework()));
        if (dto.getNotes() != null) builder.setNotes(StringValue.of(dto.getNotes()));
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }

    private com.ondeedu.grpc.lesson.Attendance toGrpcAttendance(AttendanceDto dto) {
        com.ondeedu.grpc.lesson.Attendance.Builder builder =
            com.ondeedu.grpc.lesson.Attendance.newBuilder()
                .setId(dto.getId().toString())
                .setLessonId(dto.getLessonId().toString())
                .setStudentId(dto.getStudentId().toString())
                .setStatus(dto.getStatus().name());
        if (dto.getNotes() != null) builder.setNotes(StringValue.of(dto.getNotes()));
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));
        return builder.build();
    }
}
