package com.ondeedu.tenant.grpc;

import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.tenant.*;
import com.ondeedu.tenant.dto.TenantDto;
import com.ondeedu.tenant.entity.TenantPlan;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.service.TenantService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class TenantGrpcService extends TenantServiceGrpc.TenantServiceImplBase {

    private final TenantService tenantService;

    @Override
    public void createTenant(CreateTenantRequest request, StreamObserver<TenantResponse> observer) {
        try {
            com.ondeedu.tenant.dto.CreateTenantRequest dto =
                com.ondeedu.tenant.dto.CreateTenantRequest.builder()
                    .name(request.getName())
                    .subdomain(request.getCode())
                    .email(request.getOwnerEmail())
                    .contactPerson(request.getOwnerName())
                    .plan(request.getPlan().isEmpty() ? TenantPlan.BASIC
                        : TenantPlan.valueOf(request.getPlan()))
                    .build();
            TenantDto created = tenantService.createTenant(dto);
            observer.onNext(buildResponse(true, "Tenant created", created));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC createTenant error", e);
            observer.onNext(TenantResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            observer.onCompleted();
        }
    }

    @Override
    public void getTenant(GetTenantRequest request, StreamObserver<TenantResponse> observer) {
        try {
            TenantDto dto = tenantService.getTenant(UUID.fromString(request.getTenantId()));
            observer.onNext(buildResponse(true, null, dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getTenant error", e);
            observer.onNext(TenantResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            observer.onCompleted();
        }
    }

    @Override
    public void updateTenant(UpdateTenantRequest request, StreamObserver<TenantResponse> observer) {
        try {
            com.ondeedu.tenant.dto.UpdateTenantRequest dto =
                com.ondeedu.tenant.dto.UpdateTenantRequest.builder()
                    .name(request.hasName() ? request.getName().getValue() : null)
                    .plan(request.hasPlan()
                        ? TenantPlan.valueOf(request.getPlan().getValue()) : null)
                    .build();
            TenantDto updated = tenantService.updateTenant(UUID.fromString(request.getTenantId()), dto);
            observer.onNext(buildResponse(true, "Tenant updated", updated));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC updateTenant error", e);
            observer.onNext(TenantResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            observer.onCompleted();
        }
    }

    @Override
    public void deleteTenant(DeleteTenantRequest request, StreamObserver<ApiResponse> observer) {
        try {
            tenantService.deleteTenant(UUID.fromString(request.getTenantId()));
            observer.onNext(ApiResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Tenant deleted")
                .build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC deleteTenant error", e);
            observer.onNext(ApiResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build());
            observer.onCompleted();
        }
    }

    @Override
    public void listTenants(ListTenantsRequest request, StreamObserver<ListTenantsResponse> observer) {
        try {
            var pageable = com.ondeedu.common.util.GrpcUtils.toPageable(request.getPage());
            TenantStatus status = request.hasStatus()
                ? TenantStatus.valueOf(request.getStatus().getValue()) : null;

            var page = tenantService.listTenants(status, pageable);

            ListTenantsResponse.Builder builder = ListTenantsResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addTenants(toGrpcTenant(dto)));
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
            log.error("gRPC listTenants error", e);
            observer.onNext(ListTenantsResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    private TenantResponse buildResponse(boolean success, String message, TenantDto dto) {
        TenantResponse.Builder builder = TenantResponse.newBuilder().setSuccess(success);
        if (message != null) builder.setMessage(StringValue.of(message));
        if (dto != null) builder.setTenant(toGrpcTenant(dto));
        return builder.build();
    }

    private Tenant toGrpcTenant(TenantDto dto) {
        Tenant.Builder builder = Tenant.newBuilder()
            .setId(dto.getId().toString())
            .setCode(dto.getSubdomain())
            .setName(dto.getName())
            .setSchemaName(dto.getSchemaName() != null ? dto.getSchemaName() : "")
            .setStatus(dto.getStatus().name())
            .setPlan(dto.getPlan().name());

        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }
}
