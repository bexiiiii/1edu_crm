package com.ondeedu.course.grpc;

import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.course.dto.CourseDto;
import com.ondeedu.course.entity.CourseStatus;
import com.ondeedu.course.service.CourseService;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.course.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class CourseGrpcService extends CourseServiceGrpc.CourseServiceImplBase {

    private final CourseService courseService;

    @Override
    public void getCourse(GetCourseRequest request, StreamObserver<CourseResponse> observer) {
        try {
            CourseDto dto = courseService.getCourse(UUID.fromString(request.getCourseId()));
            observer.onNext(buildResponse(true, null, dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getCourse error", e);
            observer.onNext(CourseResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            observer.onCompleted();
        }
    }

    @Override
    public void listCourses(ListCoursesRequest request, StreamObserver<ListCoursesResponse> observer) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            CourseStatus status = request.hasStatus()
                ? CourseStatus.valueOf(request.getStatus().getValue()) : null;

            var page = courseService.listCourses(status, null, pageable);

            ListCoursesResponse.Builder builder = ListCoursesResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addCourses(toGrpcCourse(dto)));
            builder.setPageInfo(com.ondeedu.grpc.common.PageInfo.newBuilder()
                .setPage(page.getPage())
                .setSize(page.getSize())
                .setTotalElements(page.getTotalElements())
                .setTotalPages(page.getTotalPages())
                .setHasNext(page.isHasNext())
                .setHasPrevious(page.isHasPrevious())
                .build());

            observer.onNext(builder.build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC listCourses error", e);
            observer.onNext(ListCoursesResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    @Override
    public void createCourse(CreateCourseRequest request, StreamObserver<CourseResponse> observer) {
        try {
            com.ondeedu.course.dto.CreateCourseRequest dto =
                com.ondeedu.course.dto.CreateCourseRequest.builder()
                    .name(request.getName())
                    .description(request.hasDescription() ? request.getDescription().getValue() : null)
                    .build();
            CourseDto created = courseService.createCourse(dto);
            observer.onNext(buildResponse(true, "Course created", created));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC createCourse error", e);
            observer.onNext(CourseResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            observer.onCompleted();
        }
    }

    @Override
    public void updateCourse(UpdateCourseRequest request, StreamObserver<CourseResponse> observer) {
        try {
            com.ondeedu.course.dto.UpdateCourseRequest dto =
                com.ondeedu.course.dto.UpdateCourseRequest.builder()
                    .name(request.hasName() ? request.getName().getValue() : null)
                    .description(request.hasDescription() ? request.getDescription().getValue() : null)
                    .status(request.hasStatus()
                        ? CourseStatus.valueOf(request.getStatus().getValue()) : null)
                    .build();
            CourseDto updated = courseService.updateCourse(UUID.fromString(request.getCourseId()), dto);
            observer.onNext(buildResponse(true, "Course updated", updated));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC updateCourse error", e);
            observer.onNext(CourseResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            observer.onCompleted();
        }
    }

    @Override
    public void deleteCourse(DeleteCourseRequest request, StreamObserver<ApiResponse> observer) {
        try {
            courseService.deleteCourse(UUID.fromString(request.getCourseId()));
            observer.onNext(ApiResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Course deleted")
                .build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC deleteCourse error", e);
            observer.onNext(ApiResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build());
            observer.onCompleted();
        }
    }

    private CourseResponse buildResponse(boolean success, String message, CourseDto dto) {
        CourseResponse.Builder builder = CourseResponse.newBuilder().setSuccess(success);
        if (message != null) builder.setMessage(StringValue.of(message));
        if (dto != null) builder.setCourse(toGrpcCourse(dto));
        return builder.build();
    }

    private Course toGrpcCourse(CourseDto dto) {
        Course.Builder builder = Course.newBuilder()
            .setId(dto.getId().toString())
            .setName(dto.getName())
            .setStatus(dto.getStatus().name());

        if (dto.getDescription() != null) builder.setDescription(StringValue.of(dto.getDescription()));
        if (dto.getBasePrice() != null) builder.setPrice(GrpcUtils.toMoney(dto.getBasePrice(), "UZS"));
        if (dto.getEnrollmentLimit() != null) builder.setMaxStudentsPerGroup(dto.getEnrollmentLimit());
        if (dto.getTeacherId() != null) builder.setTeacherId(StringValue.of(dto.getTeacherId().toString()));
        if (dto.getRoomId() != null) builder.setRoomId(StringValue.of(dto.getRoomId().toString()));
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }
}
