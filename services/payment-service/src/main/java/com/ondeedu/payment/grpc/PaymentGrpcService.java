package com.ondeedu.payment.grpc;

import com.google.protobuf.StringValue;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.common.ApiResponse;
import com.ondeedu.grpc.payment.*;
import com.ondeedu.payment.dto.SubscriptionDto;
import com.ondeedu.payment.entity.SubscriptionStatus;
import com.ondeedu.payment.service.SubscriptionService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final SubscriptionService subscriptionService;

    @Override
    public void getPayment(GetPaymentRequest request, StreamObserver<PaymentResponse> observer) {
        try {
            SubscriptionDto dto = subscriptionService.getSubscription(
                UUID.fromString(request.getPaymentId()));
            observer.onNext(buildResponse(true, null, dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getPayment error", e);
            observer.onNext(PaymentResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            observer.onCompleted();
        }
    }

    @Override
    public void listPayments(ListPaymentsRequest request, StreamObserver<ListPaymentsResponse> observer) {
        try {
            var pageable = GrpcUtils.toPageable(request.getPage());
            SubscriptionStatus status = request.hasStatus()
                ? SubscriptionStatus.valueOf(request.getStatus().getValue()) : null;

            var page = subscriptionService.listAll(status, pageable);

            ListPaymentsResponse.Builder builder = ListPaymentsResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addPayments(toGrpcPayment(dto)));
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
            log.error("gRPC listPayments error", e);
            observer.onNext(ListPaymentsResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getPaymentsByStudent(GetPaymentsByStudentRequest request,
                                     StreamObserver<ListPaymentsResponse> observer) {
        try {
            var pageable = GrpcUtils.toPageable(request.getPage());
            var page = subscriptionService.listByStudent(
                UUID.fromString(request.getStudentId()), null, pageable);

            ListPaymentsResponse.Builder builder = ListPaymentsResponse.newBuilder().setSuccess(true);
            page.getContent().forEach(dto -> builder.addPayments(toGrpcPayment(dto)));
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
            log.error("gRPC getPaymentsByStudent error", e);
            observer.onNext(ListPaymentsResponse.newBuilder().setSuccess(false).build());
            observer.onCompleted();
        }
    }

    @Override
    public void ensureCourseSubscription(EnsureCourseSubscriptionRequest request,
                                         StreamObserver<PaymentResponse> observer) {
        try {
            BigDecimal amount = request.hasAmount() ? GrpcUtils.fromMoney(request.getAmount()) : BigDecimal.ZERO;
            String currency = request.hasAmount() ? request.getAmount().getCurrency() : null;

            SubscriptionDto dto = subscriptionService.ensureCourseSubscription(
                    UUID.fromString(request.getStudentId()),
                    UUID.fromString(request.getCourseId()),
                    request.getCourseName(),
                    amount,
                    currency
            );

            observer.onNext(buildResponse(true, "Course subscription synced", dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC ensureCourseSubscription error", e);
            observer.onNext(PaymentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(StringValue.of(e.getMessage()))
                    .build());
            observer.onCompleted();
        }
    }

    @Override
    public void cancelCourseSubscription(CancelCourseSubscriptionRequest request,
                                         StreamObserver<PaymentResponse> observer) {
        try {
            subscriptionService.cancelCourseSubscription(
                    UUID.fromString(request.getStudentId()),
                    UUID.fromString(request.getCourseId())
            );
            observer.onNext(PaymentResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(StringValue.of("Course subscription cancelled"))
                    .build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC cancelCourseSubscription error", e);
            observer.onNext(PaymentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(StringValue.of(e.getMessage()))
                    .build());
            observer.onCompleted();
        }
    }

    @Override
    public void cancelPayment(CancelPaymentRequest request, StreamObserver<PaymentResponse> observer) {
        try {
            subscriptionService.cancelSubscription(UUID.fromString(request.getPaymentId()));
            SubscriptionDto dto = subscriptionService.getSubscription(
                UUID.fromString(request.getPaymentId()));
            observer.onNext(buildResponse(true, "Subscription cancelled", dto));
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC cancelPayment error", e);
            observer.onNext(PaymentResponse.newBuilder()
                .setSuccess(false)
                .setMessage(StringValue.of(e.getMessage()))
                .build());
            observer.onCompleted();
        }
    }

    private PaymentResponse buildResponse(boolean success, String message, SubscriptionDto dto) {
        PaymentResponse.Builder builder = PaymentResponse.newBuilder().setSuccess(success);
        if (message != null) builder.setMessage(StringValue.of(message));
        if (dto != null) builder.setPayment(toGrpcPayment(dto));
        return builder.build();
    }

    private Payment toGrpcPayment(SubscriptionDto dto) {
        Payment.Builder builder = Payment.newBuilder()
            .setId(dto.getId().toString())
            .setStudentId(dto.getStudentId().toString())
            .setStatus(dto.getStatus().name());

        if (dto.getAmount() != null) {
            String currency = dto.getCurrency() != null ? dto.getCurrency() : "UZS";
            builder.setAmount(GrpcUtils.toMoney(dto.getAmount(), currency));
        }
        if (dto.getCreatedAt() != null) builder.setCreatedAt(GrpcUtils.toTimestamp(dto.getCreatedAt()));
        if (dto.getUpdatedAt() != null) builder.setUpdatedAt(GrpcUtils.toTimestamp(dto.getUpdatedAt()));

        return builder.build();
    }
}
