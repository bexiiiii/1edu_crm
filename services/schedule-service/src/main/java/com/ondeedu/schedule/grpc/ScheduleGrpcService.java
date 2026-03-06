package com.ondeedu.schedule.grpc;

import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.schedule.*;
import com.ondeedu.schedule.dto.RoomDto;
import com.ondeedu.schedule.dto.ScheduleDto;
import com.ondeedu.schedule.entity.RoomStatus;
import com.ondeedu.schedule.entity.ScheduleStatus;
import com.ondeedu.schedule.service.RoomService;
import com.ondeedu.schedule.service.ScheduleService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ScheduleGrpcService extends ScheduleServiceGrpc.ScheduleServiceImplBase {

    private final ScheduleService scheduleService;
    private final RoomService roomService;

    // ── Schedule RPCs ──────────────────────────────────────────────────────────

    @Override
    public void createSchedule(CreateScheduleRequest request,
                               StreamObserver<ScheduleResponse> observer) {
        try {
            com.ondeedu.schedule.dto.CreateScheduleRequest dto =
                com.ondeedu.schedule.dto.CreateScheduleRequest.builder()
                    .name(request.getName())
                    .courseId(request.hasCourseId()
                        ? UUID.fromString(request.getCourseId().getValue()) : null)
                    .teacherId(request.hasTeacherId()
                        ? UUID.fromString(request.getTeacherId().getValue()) : null)
                    .roomId(request.hasRoomId()
                        ? UUID.fromString(request.getRoomId().getValue()) : null)
                    .daysOfWeek(request.getDaysOfWeekList().stream()
                        .map(DayOfWeek::valueOf).collect(Collectors.toSet()))
                    .startTime(LocalTime.parse(request.getStartTime()))
                    .endTime(LocalTime.parse(request.getEndTime()))
                    .startDate(LocalDate.parse(request.getStartDate()))
                    .endDate(request.hasEndDate()
                        ? LocalDate.parse(request.getEndDate().getValue()) : null)
                    .maxStudents(request.hasMaxStudents()
                        ? request.getMaxStudents().getValue() : null)
                    .build();

            ScheduleDto created = scheduleService.createSchedule(dto);
            observer.onNext(buildScheduleResponse(true, "Schedule created", created));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC createSchedule error", e);
            observer.onNext(ScheduleResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getSchedule(GetScheduleRequest request, StreamObserver<ScheduleResponse> observer) {
        try {
            ScheduleDto dto = scheduleService.getSchedule(UUID.fromString(request.getScheduleId()));
            observer.onNext(buildScheduleResponse(true, null, dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getSchedule error", e);
            observer.onNext(ScheduleResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void updateSchedule(UpdateScheduleRequest request,
                               StreamObserver<ScheduleResponse> observer) {
        try {
            com.ondeedu.schedule.dto.UpdateScheduleRequest dto =
                com.ondeedu.schedule.dto.UpdateScheduleRequest.builder()
                    .name(request.hasName() ? request.getName().getValue() : null)
                    .status(request.hasStatus()
                        ? ScheduleStatus.valueOf(request.getStatus().getValue()) : null)
                    .startTime(request.hasStartTime()
                        ? LocalTime.parse(request.getStartTime().getValue()) : null)
                    .endTime(request.hasEndTime()
                        ? LocalTime.parse(request.getEndTime().getValue()) : null)
                    .maxStudents(request.hasMaxStudents()
                        ? request.getMaxStudents().getValue() : null)
                    .build();

            ScheduleDto updated = scheduleService.updateSchedule(
                UUID.fromString(request.getScheduleId()), dto);
            observer.onNext(buildScheduleResponse(true, "Schedule updated", updated));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC updateSchedule error", e);
            observer.onNext(ScheduleResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void deleteSchedule(DeleteScheduleRequest request, StreamObserver<ApiResponse> observer) {
        try {
            scheduleService.deleteSchedule(UUID.fromString(request.getScheduleId()));
            observer.onNext(ApiResponse.newBuilder().setSuccess(true).setMessage("Schedule deleted").build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC deleteSchedule error", e);
            observer.onNext(ApiResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
            observer.onCompleted();
        }
    }

    @Override
    public void listSchedules(ListSchedulesRequest request,
                              StreamObserver<ListSchedulesResponse> observer) {
        try {
            var pageable = GrpcUtils.toPageable(request.getPage());
            ScheduleStatus status = request.hasStatus()
                ? ScheduleStatus.valueOf(request.getStatus().getValue()) : null;
            UUID courseId = request.hasCourseId()
                ? UUID.fromString(request.getCourseId().getValue()) : null;
            UUID teacherId = request.hasTeacherId()
                ? UUID.fromString(request.getTeacherId().getValue()) : null;

            var page = scheduleService.listSchedules(status, courseId, teacherId, pageable);

            ListSchedulesResponse.Builder builder = ListSchedulesResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addSchedules(toGrpcSchedule(dto)));
            builder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage()).setSize(page.getSize())
                .setTotalElements(page.getTotalElements()).setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext()).setHasPrevious(page.isHasPrevious()).build());

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC listSchedules error", e);
            observer.onNext(ListSchedulesResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getSchedulesByRoom(GetSchedulesByRoomRequest request,
                                   StreamObserver<ListSchedulesResponse> observer) {
        try {
            var pageable = GrpcUtils.toPageable(request.getPage());
            var page = scheduleService.getSchedulesByRoom(UUID.fromString(request.getRoomId()), pageable);

            ListSchedulesResponse.Builder builder = ListSchedulesResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addSchedules(toGrpcSchedule(dto)));
            builder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage()).setSize(page.getSize())
                .setTotalElements(page.getTotalElements()).setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext()).setHasPrevious(page.isHasPrevious()).build());

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getSchedulesByRoom error", e);
            observer.onNext(ListSchedulesResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    // ── Room RPCs ──────────────────────────────────────────────────────────────

    @Override
    public void createRoom(CreateRoomRequest request, StreamObserver<RoomResponse> observer) {
        try {
            com.ondeedu.schedule.dto.CreateRoomRequest dto =
                com.ondeedu.schedule.dto.CreateRoomRequest.builder()
                    .name(request.getName())
                    .capacity(request.hasCapacity() ? request.getCapacity().getValue() : null)
                    .description(request.hasDescription() ? request.getDescription().getValue() : null)
                    .color(request.hasColor() ? request.getColor().getValue() : null)
                    .build();

            RoomDto created = roomService.createRoom(dto);
            observer.onNext(buildRoomResponse(true, "Room created", created));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC createRoom error", e);
            observer.onNext(RoomResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getRoom(GetRoomRequest request, StreamObserver<RoomResponse> observer) {
        try {
            RoomDto dto = roomService.getRoom(UUID.fromString(request.getRoomId()));
            observer.onNext(buildRoomResponse(true, null, dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getRoom error", e);
            observer.onNext(RoomResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void updateRoom(UpdateRoomRequest request, StreamObserver<RoomResponse> observer) {
        try {
            com.ondeedu.schedule.dto.UpdateRoomRequest dto =
                com.ondeedu.schedule.dto.UpdateRoomRequest.builder()
                    .name(request.hasName() ? request.getName().getValue() : null)
                    .capacity(request.hasCapacity() ? request.getCapacity().getValue() : null)
                    .description(request.hasDescription() ? request.getDescription().getValue() : null)
                    .color(request.hasColor() ? request.getColor().getValue() : null)
                    .status(request.hasStatus()
                        ? RoomStatus.valueOf(request.getStatus().getValue()) : null)
                    .build();

            RoomDto updated = roomService.updateRoom(UUID.fromString(request.getRoomId()), dto);
            observer.onNext(buildRoomResponse(true, "Room updated", updated));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC updateRoom error", e);
            observer.onNext(RoomResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void deleteRoom(DeleteRoomRequest request, StreamObserver<ApiResponse> observer) {
        try {
            roomService.deleteRoom(UUID.fromString(request.getRoomId()));
            observer.onNext(ApiResponse.newBuilder().setSuccess(true).setMessage("Room deleted").build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC deleteRoom error", e);
            observer.onNext(ApiResponse.newBuilder().setSuccess(false).setMessage(e.getMessage()).build());
            observer.onCompleted();
        }
    }

    @Override
    public void listRooms(ListRoomsRequest request, StreamObserver<ListRoomsResponse> observer) {
        try {
            var pageable = GrpcUtils.toPageable(request.getPage());
            RoomStatus status = request.hasStatus()
                ? RoomStatus.valueOf(request.getStatus().getValue()) : null;

            var page = roomService.listRooms(status, pageable);

            ListRoomsResponse.Builder builder = ListRoomsResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addRooms(toGrpcRoom(dto)));
            builder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage()).setSize(page.getSize())
                .setTotalElements(page.getTotalElements()).setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext()).setHasPrevious(page.isHasPrevious()).build());

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC listRooms error", e);
            observer.onNext(ListRoomsResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ScheduleResponse buildScheduleResponse(boolean success, String message, ScheduleDto dto) {
        ScheduleResponse.Builder builder = ScheduleResponse.newBuilder().setSuccess(success);
        if (message != null) builder.setMessage(StringValue.of(message));
        if (dto != null) builder.setSchedule(toGrpcSchedule(dto));
        return builder.build();
    }

    private Schedule toGrpcSchedule(ScheduleDto dto) {
        Schedule.Builder builder = Schedule.newBuilder()
            .setId(dto.getId().toString())
            .setName(dto.getName())
            .setStartTime(dto.getStartTime().toString())
            .setEndTime(dto.getEndTime().toString())
            .setStartDate(dto.getStartDate().toString())
            .setStatus(dto.getStatus().name());

        if (dto.getCourseId() != null) builder.setCourseId(StringValue.of(dto.getCourseId().toString()));
        if (dto.getTeacherId() != null) builder.setTeacherId(StringValue.of(dto.getTeacherId().toString()));
        if (dto.getRoomId() != null) builder.setRoomId(StringValue.of(dto.getRoomId().toString()));
        if (dto.getEndDate() != null) builder.setEndDate(StringValue.of(dto.getEndDate().toString()));
        if (dto.getMaxStudents() != null) builder.setMaxStudents(Int32Value.of(dto.getMaxStudents()));
        if (dto.getDaysOfWeek() != null) {
            dto.getDaysOfWeek().forEach(d -> builder.addDaysOfWeek(d.name()));
        }
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }

    private RoomResponse buildRoomResponse(boolean success, String message, RoomDto dto) {
        RoomResponse.Builder builder = RoomResponse.newBuilder().setSuccess(success);
        if (message != null) builder.setMessage(StringValue.of(message));
        if (dto != null) builder.setRoom(toGrpcRoom(dto));
        return builder.build();
    }

    private Room toGrpcRoom(RoomDto dto) {
        Room.Builder builder = Room.newBuilder()
            .setId(dto.getId().toString())
            .setName(dto.getName())
            .setStatus(dto.getStatus().name());

        if (dto.getCapacity() != null) builder.setCapacity(Int32Value.of(dto.getCapacity()));
        if (dto.getDescription() != null) builder.setDescription(StringValue.of(dto.getDescription()));
        if (dto.getColor() != null) builder.setColor(StringValue.of(dto.getColor()));
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }
}
