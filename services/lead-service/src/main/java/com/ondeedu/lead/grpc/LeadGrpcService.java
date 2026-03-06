package com.ondeedu.lead.grpc;

import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.lead.*;
import com.ondeedu.lead.dto.CreateLeadRequest;
import com.ondeedu.lead.dto.LeadDto;
import com.ondeedu.lead.dto.UpdateLeadRequest;
import com.ondeedu.lead.entity.LeadStage;
import com.ondeedu.lead.service.LeadService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class LeadGrpcService extends LeadServiceGrpc.LeadServiceImplBase {

    private final LeadService leadService;

    @Override
    public void createLead(com.ondeedu.grpc.lead.CreateLeadRequest request,
                           StreamObserver<LeadResponse> responseObserver) {
        try {
            CreateLeadRequest dto = CreateLeadRequest.builder()
                .firstName(request.getFirstName())
                .lastName(request.hasLastName() ? request.getLastName().getValue() : null)
                .phone(request.getPhone())
                .email(request.hasEmail() ? request.getEmail().getValue() : null)
                .source(request.getSource())
                .courseInterest(request.hasInterestedCourse() ? request.getInterestedCourse().getValue() : null)
                .notes(request.hasNotes() ? request.getNotes().getValue() : null)
                .build();

            LeadDto lead = leadService.createLead(dto);

            responseObserver.onNext(buildLeadResponse(true, "Lead created successfully", lead));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error creating lead via gRPC", e);
            responseObserver.onNext(LeadResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getLead(GetLeadRequest request,
                        StreamObserver<LeadResponse> responseObserver) {
        try {
            LeadDto lead = leadService.getLead(UUID.fromString(request.getLeadId()));
            responseObserver.onNext(buildLeadResponse(true, null, lead));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error getting lead via gRPC", e);
            responseObserver.onNext(LeadResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateLead(com.ondeedu.grpc.lead.UpdateLeadRequest request,
                           StreamObserver<LeadResponse> responseObserver) {
        try {
            UpdateLeadRequest dto = UpdateLeadRequest.builder()
                .firstName(request.hasFirstName() ? request.getFirstName().getValue() : null)
                .lastName(request.hasLastName() ? request.getLastName().getValue() : null)
                .phone(request.hasPhone() ? request.getPhone().getValue() : null)
                .email(request.hasEmail() ? request.getEmail().getValue() : null)
                .notes(request.hasNotes() ? request.getNotes().getValue() : null)
                .build();

            LeadDto lead = leadService.updateLead(UUID.fromString(request.getLeadId()), dto);

            responseObserver.onNext(buildLeadResponse(true, "Lead updated successfully", lead));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error updating lead via gRPC", e);
            responseObserver.onNext(LeadResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteLead(DeleteLeadRequest request,
                           StreamObserver<ApiResponse> responseObserver) {
        try {
            leadService.deleteLead(UUID.fromString(request.getLeadId()));
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Lead deleted successfully")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error deleting lead via gRPC", e);
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listLeads(ListLeadsRequest request,
                          StreamObserver<ListLeadsResponse> responseObserver) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            LeadStage stage = request.hasStatus() ?
                LeadStage.valueOf(request.getStatus().getValue()) : null;

            var page = leadService.listLeads(stage, pageable);

            ListLeadsResponse.Builder responseBuilder = ListLeadsResponse.newBuilder()
                .setSuccess(true);

            page.getContent().forEach(dto -> responseBuilder.addLeads(toGrpcLead(dto)));

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
            log.error("Error listing leads via gRPC", e);
            responseObserver.onNext(ListLeadsResponse.newBuilder()
                .setSuccess(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateLeadStatus(UpdateLeadStatusRequest request,
                                 StreamObserver<LeadResponse> responseObserver) {
        try {
            LeadStage newStage = LeadStage.valueOf(request.getStatus());
            LeadDto lead = leadService.moveStage(UUID.fromString(request.getLeadId()), newStage);

            responseObserver.onNext(buildLeadResponse(true, "Lead stage updated", lead));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error updating lead status via gRPC", e);
            responseObserver.onNext(LeadResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    private LeadResponse buildLeadResponse(boolean success, String message, LeadDto dto) {
        LeadResponse.Builder builder = LeadResponse.newBuilder()
            .setSuccess(success);
        if (message != null) {
            builder.setMessage(StringValue.of(message));
        }
        if (dto != null) {
            builder.setLead(toGrpcLead(dto));
        }
        return builder.build();
    }

    private Lead toGrpcLead(LeadDto dto) {
        Lead.Builder builder = Lead.newBuilder()
            .setId(dto.getId().toString())
            .setFirstName(dto.getFirstName())
            .setStatus(dto.getStage().name());

        if (dto.getLastName() != null) builder.setLastName(StringValue.of(dto.getLastName()));
        if (dto.getPhone() != null) builder.setPhone(dto.getPhone());
        if (dto.getEmail() != null) builder.setEmail(StringValue.of(dto.getEmail()));
        if (dto.getSource() != null) builder.setSource(dto.getSource());
        if (dto.getCourseInterest() != null) builder.setInterestedCourse(StringValue.of(dto.getCourseInterest()));
        if (dto.getNotes() != null) builder.setNotes(StringValue.of(dto.getNotes()));
        if (dto.getAssignedTo() != null) builder.setAssignedTo(StringValue.of(dto.getAssignedTo()));
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }
}
