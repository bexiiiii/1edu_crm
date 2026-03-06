package com.ondeedu.analytics.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.StringValue;
import com.ondeedu.analytics.service.*;
import com.ondeedu.grpc.analytics.*;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.LocalDate;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AnalyticsGrpcService extends AnalyticsServiceGrpc.AnalyticsServiceImplBase {

    private final DashboardService dashboardService;
    private final FinanceReportService financeReportService;
    private final TodayStatsService todayStatsService;
    private final FunnelService funnelService;
    private final SubscriptionReportService subscriptionReportService;
    private final RetentionService retentionService;
    private final ManagerEfficiencyService managerEfficiencyService;
    private final TeacherAnalyticsService teacherAnalyticsService;
    private final ObjectMapper objectMapper;

    @Override
    public void getDashboard(GetDashboardRequest request, StreamObserver<DashboardResponse> observer) {
        try {
            LocalDate from = request.hasDateRange() && request.getDateRange().hasFrom()
                ? LocalDate.parse(request.getDateRange().getFrom().getValue()) : null;
            LocalDate to = request.hasDateRange() && request.getDateRange().hasTo()
                ? LocalDate.parse(request.getDateRange().getTo().getValue()) : null;
            String lessonType = request.hasLessonType() ? request.getLessonType().getValue() : null;

            Object result = dashboardService.getDashboard(from, to, lessonType);
            String json = objectMapper.writeValueAsString(result);

            observer.onNext(DashboardResponse.newBuilder()
                .setSuccess(true).setDataJson(json).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getDashboard error", e);
            observer.onNext(DashboardResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getFinanceReport(GetFinanceReportRequest request,
                                 StreamObserver<FinanceReportResponse> observer) {
        try {
            LocalDate from = request.hasDateRange() && request.getDateRange().hasFrom()
                ? LocalDate.parse(request.getDateRange().getFrom().getValue()) : null;
            LocalDate to = request.hasDateRange() && request.getDateRange().hasTo()
                ? LocalDate.parse(request.getDateRange().getTo().getValue()) : null;

            Object result = financeReportService.getReport(from, to);
            String json = objectMapper.writeValueAsString(result);

            observer.onNext(FinanceReportResponse.newBuilder()
                .setSuccess(true).setDataJson(json).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getFinanceReport error", e);
            observer.onNext(FinanceReportResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getTodayStats(GetTodayStatsRequest request,
                              StreamObserver<TodayStatsResponse> observer) {
        try {
            LocalDate date = request.hasDate()
                ? LocalDate.parse(request.getDate().getValue()) : LocalDate.now();

            Object result = todayStatsService.getStats(date);
            String json = objectMapper.writeValueAsString(result);

            observer.onNext(TodayStatsResponse.newBuilder()
                .setSuccess(true).setDataJson(json).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getTodayStats error", e);
            observer.onNext(TodayStatsResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getFunnel(GetFunnelRequest request, StreamObserver<FunnelResponse> observer) {
        try {
            LocalDate from = request.hasDateRange() && request.getDateRange().hasFrom()
                ? LocalDate.parse(request.getDateRange().getFrom().getValue()) : null;
            LocalDate to = request.hasDateRange() && request.getDateRange().hasTo()
                ? LocalDate.parse(request.getDateRange().getTo().getValue()) : null;

            Object result = funnelService.getFunnel(from, to);
            String json = objectMapper.writeValueAsString(result);

            observer.onNext(FunnelResponse.newBuilder()
                .setSuccess(true).setDataJson(json).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getFunnel error", e);
            observer.onNext(FunnelResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getSubscriptions(GetSubscriptionsRequest request,
                                 StreamObserver<SubscriptionsResponse> observer) {
        try {
            Object result = subscriptionReportService.getReport(null, null, request.getOnlySuspicious());
            String json = objectMapper.writeValueAsString(result);

            observer.onNext(SubscriptionsResponse.newBuilder()
                .setSuccess(true).setDataJson(json).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getSubscriptions error", e);
            observer.onNext(SubscriptionsResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getRetention(GetRetentionRequest request, StreamObserver<RetentionResponse> observer) {
        try {
            Object result = retentionService.getCohorts(null, null, null);
            String json = objectMapper.writeValueAsString(result);

            observer.onNext(RetentionResponse.newBuilder()
                .setSuccess(true).setDataJson(json).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getRetention error", e);
            observer.onNext(RetentionResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getLeadConversions(GetLeadConversionsRequest request,
                                   StreamObserver<LeadConversionsResponse> observer) {
        try {
            LocalDate from = request.hasDateRange() && request.getDateRange().hasFrom()
                ? LocalDate.parse(request.getDateRange().getFrom().getValue()) : null;
            LocalDate to = request.hasDateRange() && request.getDateRange().hasTo()
                ? LocalDate.parse(request.getDateRange().getTo().getValue()) : null;

            Object result = funnelService.getConversions(from, to);
            String json = objectMapper.writeValueAsString(result);

            observer.onNext(LeadConversionsResponse.newBuilder()
                .setSuccess(true).setDataJson(json).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getLeadConversions error", e);
            observer.onNext(LeadConversionsResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getManagers(GetManagersRequest request, StreamObserver<ManagersResponse> observer) {
        try {
            LocalDate from = request.hasDateRange() && request.getDateRange().hasFrom()
                ? LocalDate.parse(request.getDateRange().getFrom().getValue()) : null;
            LocalDate to = request.hasDateRange() && request.getDateRange().hasTo()
                ? LocalDate.parse(request.getDateRange().getTo().getValue()) : null;

            Object result = managerEfficiencyService.getEfficiency(from, to);
            String json = objectMapper.writeValueAsString(result);

            observer.onNext(ManagersResponse.newBuilder()
                .setSuccess(true).setDataJson(json).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getManagers error", e);
            observer.onNext(ManagersResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }

    @Override
    public void getTeachers(GetTeachersRequest request, StreamObserver<TeachersResponse> observer) {
        try {
            LocalDate from = request.hasDateRange() && request.getDateRange().hasFrom()
                ? LocalDate.parse(request.getDateRange().getFrom().getValue()) : null;
            LocalDate to = request.hasDateRange() && request.getDateRange().hasTo()
                ? LocalDate.parse(request.getDateRange().getTo().getValue()) : null;

            Object result = teacherAnalyticsService.getAnalytics(from, to);
            String json = objectMapper.writeValueAsString(result);

            observer.onNext(TeachersResponse.newBuilder()
                .setSuccess(true).setDataJson(json).build());
            observer.onCompleted();
        } catch (Exception e) {
            log.error("gRPC getTeachers error", e);
            observer.onNext(TeachersResponse.newBuilder()
                .setSuccess(false).setMessage(StringValue.of(e.getMessage())).build());
            observer.onCompleted();
        }
    }
}
