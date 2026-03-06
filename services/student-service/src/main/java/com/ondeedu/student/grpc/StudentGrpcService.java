package com.ondeedu.student.grpc;

import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.student.*;
import com.ondeedu.student.dto.CreateStudentRequest;
import com.ondeedu.student.dto.StudentDto;
import com.ondeedu.student.dto.UpdateStudentRequest;
import com.ondeedu.student.entity.StudentStatus;
import com.ondeedu.student.service.StudentService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class StudentGrpcService extends StudentServiceGrpc.StudentServiceImplBase {

    private final StudentService studentService;

    @Override
    public void createStudent(com.ondeedu.grpc.student.CreateStudentRequest request,
                              StreamObserver<StudentResponse> responseObserver) {
        try {
            CreateStudentRequest dto = CreateStudentRequest.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .middleName(request.hasMiddleName() ? request.getMiddleName().getValue() : null)
                .email(request.hasEmail() ? request.getEmail().getValue() : null)
                .phone(request.getPhone())
                .birthDate(request.hasBirthDate() ?
                    GrpcUtils.fromTimestamp(request.getBirthDate()).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : null)
                .parentName(request.hasParentName() ? request.getParentName().getValue() : null)
                .parentPhone(request.hasParentPhone() ? request.getParentPhone().getValue() : null)
                .build();

            StudentDto student = studentService.createStudent(dto);

            responseObserver.onNext(buildStudentResponse(true, "Student created successfully", student));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error creating student via gRPC", e);
            responseObserver.onNext(StudentResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getStudent(GetStudentRequest request,
                           StreamObserver<StudentResponse> responseObserver) {
        try {
            StudentDto student = studentService.getStudent(UUID.fromString(request.getStudentId()));
            responseObserver.onNext(buildStudentResponse(true, null, student));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error getting student via gRPC", e);
            responseObserver.onNext(StudentResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateStudent(com.ondeedu.grpc.student.UpdateStudentRequest request,
                              StreamObserver<StudentResponse> responseObserver) {
        try {
            UpdateStudentRequest dto = UpdateStudentRequest.builder()
                .firstName(request.hasFirstName() ? request.getFirstName().getValue() : null)
                .lastName(request.hasLastName() ? request.getLastName().getValue() : null)
                .middleName(request.hasMiddleName() ? request.getMiddleName().getValue() : null)
                .email(request.hasEmail() ? request.getEmail().getValue() : null)
                .phone(request.hasPhone() ? request.getPhone().getValue() : null)
                .status(request.hasStatus() ? StudentStatus.valueOf(request.getStatus().getValue()) : null)
                .build();

            StudentDto student = studentService.updateStudent(
                UUID.fromString(request.getStudentId()), dto);

            responseObserver.onNext(buildStudentResponse(true, "Student updated successfully", student));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error updating student via gRPC", e);
            responseObserver.onNext(StudentResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteStudent(DeleteStudentRequest request,
                              StreamObserver<ApiResponse> responseObserver) {
        try {
            studentService.deleteStudent(UUID.fromString(request.getStudentId()));
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Student deleted successfully")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error deleting student via gRPC", e);
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listStudents(ListStudentsRequest request,
                             StreamObserver<ListStudentsResponse> responseObserver) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            StudentStatus status = request.hasStatus() ?
                StudentStatus.valueOf(request.getStatus().getValue()) : null;

            var page = studentService.listStudents(status, pageable);

            ListStudentsResponse.Builder responseBuilder = ListStudentsResponse.newBuilder()
                .setSuccess(true);

            page.getContent().forEach(dto ->
                responseBuilder.addStudents(toGrpcStudent(dto)));

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
            log.error("Error listing students via gRPC", e);
            responseObserver.onNext(ListStudentsResponse.newBuilder()
                .setSuccess(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getStudentStats(GetStudentStatsRequest request,
                                StreamObserver<StudentStatsResponse> responseObserver) {
        try {
            var stats = studentService.getStats();
            responseObserver.onNext(StudentStatsResponse.newBuilder()
                .setTotalStudents(stats.getTotalStudents())
                .setActiveStudents(stats.getActiveStudents())
                .setNewThisMonth(stats.getNewThisMonth())
                .setGraduated(stats.getGraduated())
                .setDropped(stats.getDropped())
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error getting student stats via gRPC", e);
            responseObserver.onError(e);
        }
    }

    private StudentResponse buildStudentResponse(boolean success, String message, StudentDto dto) {
        StudentResponse.Builder builder = StudentResponse.newBuilder()
            .setSuccess(success);

        if (message != null) {
            builder.setMessage(StringValue.of(message));
        }
        if (dto != null) {
            builder.setStudent(toGrpcStudent(dto));
        }

        return builder.build();
    }

    private Student toGrpcStudent(StudentDto dto) {
        Student.Builder builder = Student.newBuilder()
            .setId(dto.getId().toString())
            .setFirstName(dto.getFirstName())
            .setLastName(dto.getLastName())
            .setStatus(dto.getStatus().name());

        if (dto.getMiddleName() != null) {
            builder.setMiddleName(StringValue.of(dto.getMiddleName()));
        }
        if (dto.getEmail() != null) {
            builder.setEmail(StringValue.of(dto.getEmail()));
        }
        if (dto.getPhone() != null) {
            builder.setPhone(dto.getPhone());
        }
        if (dto.getCreatedAt() != null) {
            builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        }
        if (dto.getUpdatedAt() != null) {
            builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));
        }

        return builder.build();
    }
}