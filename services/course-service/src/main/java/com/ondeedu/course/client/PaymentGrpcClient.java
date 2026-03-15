package com.ondeedu.course.client;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.util.GrpcUtils;
import com.ondeedu.grpc.payment.CancelCourseSubscriptionRequest;
import com.ondeedu.grpc.payment.EnsureCourseSubscriptionRequest;
import com.ondeedu.grpc.payment.PaymentResponse;
import com.ondeedu.grpc.payment.PaymentServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Component
public class PaymentGrpcClient {

    private static final String DEFAULT_CURRENCY = "KZT";

    @GrpcClient("payment-service")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub;

    public void ensureCourseSubscription(UUID studentId, UUID courseId, String courseName, BigDecimal amount) {
        EnsureCourseSubscriptionRequest request = EnsureCourseSubscriptionRequest.newBuilder()
                .setStudentId(studentId.toString())
                .setCourseId(courseId.toString())
                .setCourseName(courseName != null ? courseName : "")
                .setAmount(GrpcUtils.toMoney(amount != null ? amount : BigDecimal.ZERO, DEFAULT_CURRENCY))
                .build();

        PaymentResponse response = executeEnsure(request);
        if (!response.getSuccess()) {
            throw new BusinessException(
                    "COURSE_SUBSCRIPTION_SYNC_FAILED",
                    response.hasMessage() ? response.getMessage().getValue() : "Failed to sync course subscription"
            );
        }
    }

    public void cancelCourseSubscription(UUID studentId, UUID courseId) {
        CancelCourseSubscriptionRequest request = CancelCourseSubscriptionRequest.newBuilder()
                .setStudentId(studentId.toString())
                .setCourseId(courseId.toString())
                .build();

        PaymentResponse response = executeCancel(request);
        if (!response.getSuccess()) {
            throw new BusinessException(
                    "COURSE_SUBSCRIPTION_SYNC_FAILED",
                    response.hasMessage() ? response.getMessage().getValue() : "Failed to cancel course subscription"
            );
        }
    }

    private PaymentResponse executeEnsure(EnsureCourseSubscriptionRequest request) {
        try {
            return paymentStub.ensureCourseSubscription(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC ensureCourseSubscription failed for course {} student {}: {}",
                    request.getCourseId(), request.getStudentId(), e.getStatus());
            throw new BusinessException(
                    "COURSE_SUBSCRIPTION_SYNC_FAILED",
                    "Payment service is unavailable for course subscription sync"
            );
        }
    }

    private PaymentResponse executeCancel(CancelCourseSubscriptionRequest request) {
        try {
            return paymentStub.cancelCourseSubscription(request);
        } catch (StatusRuntimeException e) {
            log.error("gRPC cancelCourseSubscription failed for course {} student {}: {}",
                    request.getCourseId(), request.getStudentId(), e.getStatus());
            throw new BusinessException(
                    "COURSE_SUBSCRIPTION_SYNC_FAILED",
                    "Payment service is unavailable for course subscription sync"
            );
        }
    }
}
