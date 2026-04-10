package com.ondeedu.schedule.client;

import com.ondeedu.grpc.course.CourseServiceGrpc;
import com.ondeedu.grpc.course.GetCourseRequest;
import com.ondeedu.grpc.course.CourseResponse;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class CourseGrpcClient {

    @GrpcClient("course-service")
    private CourseServiceGrpc.CourseServiceBlockingStub courseStub;

    public record CourseInfo(UUID teacherId, Integer enrollmentLimit, UUID roomId) {}

    /**
     * Fetches course teacherId and enrollmentLimit from course-service.
     * Returns empty if course not found or gRPC call fails.
     */
    public Optional<CourseInfo> getCourseInfo(UUID courseId) {
        try {
            CourseResponse response = courseStub.getCourse(
                    GetCourseRequest.newBuilder()
                            .setCourseId(courseId.toString())
                            .build()
            );

            if (!response.getSuccess() || !response.hasCourse()) {
                log.warn("CourseGrpcClient: course {} not found", courseId);
                return Optional.empty();
            }

            var course = response.getCourse();
            UUID teacherId = course.hasTeacherId()
                    ? parseUuid(course.getTeacherId().getValue()) : null;
            UUID roomId = course.hasRoomId()
                    ? parseUuid(course.getRoomId().getValue()) : null;
            Integer enrollmentLimit = course.getMaxStudentsPerGroup() > 0
                    ? course.getMaxStudentsPerGroup() : null;

            return Optional.of(new CourseInfo(teacherId, enrollmentLimit, roomId));
        } catch (StatusRuntimeException e) {
            log.warn("CourseGrpcClient: gRPC call failed for course {}: {}", courseId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("CourseGrpcClient: unexpected error for course {}: {}", courseId, e.getMessage());
            return Optional.empty();
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
