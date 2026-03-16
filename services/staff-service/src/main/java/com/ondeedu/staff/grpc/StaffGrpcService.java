package com.ondeedu.staff.grpc;

import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.staff.*;
import com.ondeedu.staff.dto.CreateStaffRequest;
import com.ondeedu.staff.dto.StaffDto;
import com.ondeedu.staff.dto.UpdateStaffRequest;
import com.ondeedu.staff.entity.StaffRole;
import com.ondeedu.staff.entity.StaffStatus;
import com.ondeedu.staff.service.StaffService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.math.BigDecimal;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class StaffGrpcService extends StaffServiceGrpc.StaffServiceImplBase {

    private final StaffService staffService;

    @Override
    public void createStaff(com.ondeedu.grpc.staff.CreateStaffRequest request,
                            StreamObserver<StaffResponse> responseObserver) {
        try {
            CreateStaffRequest dto = CreateStaffRequest.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .middleName(request.hasMiddleName() ? request.getMiddleName().getValue() : null)
                .email(request.hasEmail() ? request.getEmail().getValue() : null)
                .phone(request.hasPhone() ? request.getPhone().getValue() : null)
                .role(StaffRole.valueOf(request.getRole()))
                .position(request.hasPosition() ? request.getPosition().getValue() : null)
                .salary(request.hasSalary() ? GrpcUtils.fromMoney(request.getSalary()) : null)
                .salaryType(request.hasSalaryType() ? com.ondeedu.common.payroll.SalaryType.valueOf(request.getSalaryType().getValue()) : null)
                .salaryPercentage(request.hasSalaryPercentage() ? BigDecimal.valueOf(request.getSalaryPercentage().getValue()) : null)
                .build();

            StaffDto staff = staffService.createStaff(dto);

            responseObserver.onNext(buildStaffResponse(true, "Staff created successfully", staff));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error creating staff via gRPC", e);
            responseObserver.onNext(StaffResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getStaff(GetStaffRequest request,
                         StreamObserver<StaffResponse> responseObserver) {
        try {
            StaffDto staff = staffService.getStaff(UUID.fromString(request.getStaffId()));
            responseObserver.onNext(buildStaffResponse(true, null, staff));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error getting staff via gRPC", e);
            responseObserver.onNext(StaffResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateStaff(com.ondeedu.grpc.staff.UpdateStaffRequest request,
                            StreamObserver<StaffResponse> responseObserver) {
        try {
            UpdateStaffRequest dto = UpdateStaffRequest.builder()
                .firstName(request.hasFirstName() ? request.getFirstName().getValue() : null)
                .lastName(request.hasLastName() ? request.getLastName().getValue() : null)
                .middleName(request.hasMiddleName() ? request.getMiddleName().getValue() : null)
                .email(request.hasEmail() ? request.getEmail().getValue() : null)
                .phone(request.hasPhone() ? request.getPhone().getValue() : null)
                .role(request.hasRole() ? StaffRole.valueOf(request.getRole().getValue()) : null)
                .status(request.hasStatus() ? StaffStatus.valueOf(request.getStatus().getValue()) : null)
                .position(request.hasPosition() ? request.getPosition().getValue() : null)
                .salary(request.hasSalary() ? GrpcUtils.fromMoney(request.getSalary()) : null)
                .salaryType(request.hasSalaryType() ? com.ondeedu.common.payroll.SalaryType.valueOf(request.getSalaryType().getValue()) : null)
                .salaryPercentage(request.hasSalaryPercentage() ? BigDecimal.valueOf(request.getSalaryPercentage().getValue()) : null)
                .build();

            StaffDto staff = staffService.updateStaff(UUID.fromString(request.getStaffId()), dto);

            responseObserver.onNext(buildStaffResponse(true, "Staff updated successfully", staff));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error updating staff via gRPC", e);
            responseObserver.onNext(StaffResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteStaff(DeleteStaffRequest request,
                            StreamObserver<ApiResponse> responseObserver) {
        try {
            staffService.deleteStaff(UUID.fromString(request.getStaffId()));
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Staff deleted successfully")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error deleting staff via gRPC", e);
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listStaff(ListStaffRequest request,
                          StreamObserver<ListStaffResponse> responseObserver) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            StaffRole role = request.hasRole() ? StaffRole.valueOf(request.getRole().getValue()) : null;
            StaffStatus status = request.hasStatus() ? StaffStatus.valueOf(request.getStatus().getValue()) : null;

            var page = staffService.listStaff(role, status, pageable);

            ListStaffResponse.Builder responseBuilder = ListStaffResponse.newBuilder()
                .setSuccess(true);

            page.getContent().forEach(dto -> responseBuilder.addStaffList(toGrpcStaff(dto)));

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
            log.error("Error listing staff via gRPC", e);
            responseObserver.onNext(ListStaffResponse.newBuilder()
                .setSuccess(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    private StaffResponse buildStaffResponse(boolean success, String message, StaffDto dto) {
        StaffResponse.Builder builder = StaffResponse.newBuilder()
            .setSuccess(success);
        if (message != null) {
            builder.setMessage(StringValue.of(message));
        }
        if (dto != null) {
            builder.setStaff(toGrpcStaff(dto));
        }
        return builder.build();
    }

    private Staff toGrpcStaff(StaffDto dto) {
        Staff.Builder builder = Staff.newBuilder()
            .setId(dto.getId().toString())
            .setFirstName(dto.getFirstName())
            .setLastName(dto.getLastName())
            .setRole(dto.getRole().name())
            .setStatus(dto.getStatus().name());

        if (dto.getMiddleName() != null) builder.setMiddleName(StringValue.of(dto.getMiddleName()));
        if (dto.getEmail() != null) builder.setEmail(StringValue.of(dto.getEmail()));
        if (dto.getPhone() != null) builder.setPhone(StringValue.of(dto.getPhone()));
        if (dto.getPosition() != null) builder.setPosition(StringValue.of(dto.getPosition()));
        if (dto.getSalary() != null) builder.setSalary(GrpcUtils.toMoney(dto.getSalary(), "UZS"));
        if (dto.getSalaryType() != null) builder.setSalaryType(StringValue.of(dto.getSalaryType().name()));
        if (dto.getSalaryPercentage() != null) builder.setSalaryPercentage(com.google.protobuf.DoubleValue.of(dto.getSalaryPercentage().doubleValue()));
        if (dto.getNotes() != null) builder.setNotes(StringValue.of(dto.getNotes()));
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }
}
