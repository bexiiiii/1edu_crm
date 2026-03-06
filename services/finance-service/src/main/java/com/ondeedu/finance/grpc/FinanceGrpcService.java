package com.ondeedu.finance.grpc;

import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.finance.*;
import com.ondeedu.finance.dto.CreateTransactionRequest;
import com.ondeedu.finance.dto.TransactionDto;
import com.ondeedu.finance.dto.UpdateTransactionRequest;
import com.ondeedu.finance.entity.TransactionType;
import com.ondeedu.finance.service.FinanceService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class FinanceGrpcService extends FinanceServiceGrpc.FinanceServiceImplBase {

    private final FinanceService financeService;

    @Override
    public void createTransaction(com.ondeedu.grpc.finance.CreateTransactionRequest request,
                                  StreamObserver<TransactionResponse> responseObserver) {
        try {
            CreateTransactionRequest dto = CreateTransactionRequest.builder()
                .type(TransactionType.valueOf(request.getType()))
                .amount(request.hasAmount() ? GrpcUtils.fromMoney(request.getAmount()) : null)
                .currency(request.hasAmount() ? request.getAmount().getCurrency() : "UZS")
                .category(request.hasCategory() ? request.getCategory().getValue() : null)
                .description(request.hasDescription() ? request.getDescription().getValue() : null)
                .transactionDate(request.hasTransactionDate() ?
                    GrpcUtils.fromTimestamp(request.getTransactionDate()).atZone(java.time.ZoneId.systemDefault()).toLocalDate() : LocalDate.now())
                .studentId(request.hasStudentId() ? UUID.fromString(request.getStudentId().getValue()) : null)
                .notes(request.hasNotes() ? request.getNotes().getValue() : null)
                .build();

            TransactionDto transaction = financeService.createTransaction(dto);

            responseObserver.onNext(buildTransactionResponse(true, "Transaction created successfully", transaction));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error creating transaction via gRPC", e);
            responseObserver.onNext(TransactionResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getTransaction(GetTransactionRequest request,
                               StreamObserver<TransactionResponse> responseObserver) {
        try {
            TransactionDto transaction = financeService.getTransaction(UUID.fromString(request.getTransactionId()));
            responseObserver.onNext(buildTransactionResponse(true, null, transaction));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error getting transaction via gRPC", e);
            responseObserver.onNext(TransactionResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteTransaction(DeleteTransactionRequest request,
                                  StreamObserver<ApiResponse> responseObserver) {
        try {
            financeService.deleteTransaction(UUID.fromString(request.getTransactionId()));
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Transaction deleted successfully")
                .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error deleting transaction via gRPC", e);
            responseObserver.onNext(ApiResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listTransactions(ListTransactionsRequest request,
                                 StreamObserver<ListTransactionsResponse> responseObserver) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            TransactionType type = request.hasType() ?
                TransactionType.valueOf(request.getType().getValue()) : null;

            var page = financeService.listTransactions(type, pageable);

            ListTransactionsResponse.Builder responseBuilder = ListTransactionsResponse.newBuilder()
                .setSuccess(true);

            page.getContent().forEach(dto -> responseBuilder.addTransactions(toGrpcTransaction(dto)));

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
            log.error("Error listing transactions via gRPC", e);
            responseObserver.onNext(ListTransactionsResponse.newBuilder()
                .setSuccess(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listTransactionsByStudent(ListTransactionsByStudentRequest request,
                                          StreamObserver<ListTransactionsResponse> responseObserver) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            UUID studentId = UUID.fromString(request.getStudentId());

            var page = financeService.listByStudent(studentId, pageable);

            ListTransactionsResponse.Builder responseBuilder = ListTransactionsResponse.newBuilder()
                .setSuccess(true);

            page.getContent().forEach(dto -> responseBuilder.addTransactions(toGrpcTransaction(dto)));

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
            log.error("Error listing transactions by student via gRPC", e);
            responseObserver.onNext(ListTransactionsResponse.newBuilder()
                .setSuccess(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    @Override
    public void listTransactionsByDateRange(ListTransactionsByDateRangeRequest request,
                                            StreamObserver<ListTransactionsResponse> responseObserver) {
        try {
            Pageable pageable = GrpcUtils.toPageable(request.getPage());
            LocalDate from = GrpcUtils.fromTimestamp(request.getDateRange().getStart())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            LocalDate to = GrpcUtils.fromTimestamp(request.getDateRange().getEnd())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();

            var page = financeService.listByDateRange(from, to, pageable);

            ListTransactionsResponse.Builder responseBuilder = ListTransactionsResponse.newBuilder()
                .setSuccess(true);

            page.getContent().forEach(dto -> responseBuilder.addTransactions(toGrpcTransaction(dto)));

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
            log.error("Error listing transactions by date range via gRPC", e);
            responseObserver.onNext(ListTransactionsResponse.newBuilder()
                .setSuccess(false)
                .build());
            responseObserver.onCompleted();
        }
    }

    private TransactionResponse buildTransactionResponse(boolean success, String message, TransactionDto dto) {
        TransactionResponse.Builder builder = TransactionResponse.newBuilder()
            .setSuccess(success);
        if (message != null) {
            builder.setMessage(StringValue.of(message));
        }
        if (dto != null) {
            builder.setTransaction(toGrpcTransaction(dto));
        }
        return builder.build();
    }

    private Transaction toGrpcTransaction(TransactionDto dto) {
        Transaction.Builder builder = Transaction.newBuilder()
            .setId(dto.getId().toString())
            .setType(dto.getType().name())
            .setStatus(dto.getStatus().name());

        if (dto.getAmount() != null) builder.setAmount(GrpcUtils.toMoney(dto.getAmount(), dto.getCurrency()));
        if (dto.getCategory() != null) builder.setCategory(StringValue.of(dto.getCategory()));
        if (dto.getDescription() != null) builder.setDescription(StringValue.of(dto.getDescription()));
        if (dto.getTransactionDate() != null) builder.setTransactionDate(GrpcUtils.toTimestamp(
            dto.getTransactionDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        if (dto.getStudentId() != null) builder.setStudentId(StringValue.of(dto.getStudentId().toString()));
        if (dto.getNotes() != null) builder.setNotes(StringValue.of(dto.getNotes()));
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }
}
