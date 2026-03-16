package com.ondeedu.task.client;

import com.ondeedu.grpc.staff.GetStaffRequest;
import com.ondeedu.grpc.staff.StaffResponse;
import com.ondeedu.grpc.staff.StaffServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class StaffGrpcClient {

    @GrpcClient("staff-service")
    private StaffServiceGrpc.StaffServiceBlockingStub staffStub;

    public Optional<StaffRecipient> findRecipient(UUID staffId) {
        try {
            StaffResponse response = staffStub.getStaff(GetStaffRequest.newBuilder()
                    .setStaffId(staffId.toString())
                    .build());

            if (!response.getSuccess() || !response.hasStaff()) {
                return Optional.empty();
            }

            String email = response.getStaff().hasEmail() ? response.getStaff().getEmail().getValue() : null;
            if (!StringUtils.hasText(email)) {
                return Optional.empty();
            }

            String fullName = (response.getStaff().getFirstName() + " " + response.getStaff().getLastName()).trim();
            return Optional.of(new StaffRecipient(staffId, email, fullName));
        } catch (StatusRuntimeException e) {
            log.warn("Failed to resolve staff {} via gRPC: {}", staffId, e.getStatus());
            return Optional.empty();
        }
    }

    public record StaffRecipient(UUID staffId, String email, String fullName) {
    }
}
