package com.ondeedu.payment.client;

import com.ondeedu.grpc.student.GetStudentRequest;
import com.ondeedu.grpc.student.StudentResponse;
import com.ondeedu.grpc.student.StudentServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class StudentGrpcClient {

    @GrpcClient("student-service")
    private StudentServiceGrpc.StudentServiceBlockingStub studentStub;

    public record StudentContactData(
            UUID studentId,
            String phone,
            String studentPhone,
            String parentPhone,
            List<String> additionalPhones
    ) {
    }

    public Optional<StudentContactData> getStudentContact(UUID studentId) {
        try {
            StudentResponse response = studentStub.getStudent(
                    GetStudentRequest.newBuilder().setStudentId(studentId.toString()).build()
            );

            if (!response.getSuccess() || !response.hasStudent()) {
                return Optional.empty();
            }

            var student = response.getStudent();
            return Optional.of(new StudentContactData(
                    studentId,
                    blankToNull(student.getPhone()),
                    student.hasStudentPhone() ? blankToNull(student.getStudentPhone().getValue()) : null,
                    student.hasParentPhone() ? blankToNull(student.getParentPhone().getValue()) : null,
                    student.getAdditionalPhonesList()
            ));
        } catch (StatusRuntimeException e) {
            log.warn("Student gRPC call failed for {}: {}", studentId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Unexpected student gRPC error for {}: {}", studentId, e.getMessage());
            return Optional.empty();
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
