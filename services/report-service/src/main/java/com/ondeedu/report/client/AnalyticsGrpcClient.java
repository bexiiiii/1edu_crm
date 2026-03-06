package com.ondeedu.report.client;

import com.google.protobuf.StringValue;
import com.ondeedu.grpc.analytics.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
public class AnalyticsGrpcClient {

    @GrpcClient("analytics-service")
    private AnalyticsServiceGrpc.AnalyticsServiceBlockingStub analyticsStub;

    public String getDashboardJson(LocalDate from, LocalDate to) {
        try {
            GetDashboardRequest.Builder requestBuilder = GetDashboardRequest.newBuilder();
            if (from != null || to != null) {
                com.ondeedu.grpc.analytics.DateRange.Builder rangeBuilder =
                    com.ondeedu.grpc.analytics.DateRange.newBuilder();
                if (from != null) rangeBuilder.setFrom(StringValue.of(from.toString()));
                if (to != null) rangeBuilder.setTo(StringValue.of(to.toString()));
                requestBuilder.setDateRange(rangeBuilder.build());
            }

            DashboardResponse response = analyticsStub.getDashboard(requestBuilder.build());
            if (response.getSuccess()) {
                return response.getDataJson();
            }
            log.warn("Analytics gRPC getDashboard returned failure: {}",
                response.hasMessage() ? response.getMessage().getValue() : "unknown");
            return null;
        } catch (StatusRuntimeException e) {
            log.error("gRPC getDashboard failed: {}", e.getStatus());
            throw new RuntimeException("Analytics service unavailable: " + e.getStatus(), e);
        }
    }

    public String getFinanceReportJson(LocalDate from, LocalDate to) {
        try {
            GetFinanceReportRequest.Builder requestBuilder = GetFinanceReportRequest.newBuilder();
            if (from != null || to != null) {
                com.ondeedu.grpc.analytics.DateRange.Builder rangeBuilder =
                    com.ondeedu.grpc.analytics.DateRange.newBuilder();
                if (from != null) rangeBuilder.setFrom(StringValue.of(from.toString()));
                if (to != null) rangeBuilder.setTo(StringValue.of(to.toString()));
                requestBuilder.setDateRange(rangeBuilder.build());
            }

            FinanceReportResponse response = analyticsStub.getFinanceReport(requestBuilder.build());
            if (response.getSuccess()) {
                return response.getDataJson();
            }
            log.warn("Analytics gRPC getFinanceReport returned failure: {}",
                response.hasMessage() ? response.getMessage().getValue() : "unknown");
            return null;
        } catch (StatusRuntimeException e) {
            log.error("gRPC getFinanceReport failed: {}", e.getStatus());
            throw new RuntimeException("Analytics service unavailable: " + e.getStatus(), e);
        }
    }
}
