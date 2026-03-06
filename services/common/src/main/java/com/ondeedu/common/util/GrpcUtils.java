package com.ondeedu.common.util;

import com.google.protobuf.Timestamp;
import com.ondeedu.grpc.common.Money;
import com.ondeedu.grpc.common.PageInfo;
import com.ondeedu.grpc.common.PageRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GrpcUtils {

    public static Timestamp toTimestamp(Instant instant) {
        if (instant == null) return null;
        return Timestamp.newBuilder()
            .setSeconds(instant.getEpochSecond())
            .setNanos(instant.getNano())
            .build();
    }

    public static Instant fromTimestamp(Timestamp timestamp) {
        if (timestamp == null) return null;
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static Money toMoney(BigDecimal amount, String currency) {
        if (amount == null) return null;
        return Money.newBuilder()
            .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
            .setCurrency(currency != null ? currency : "KZT")
            .build();
    }

    public static BigDecimal fromMoney(Money money) {
        if (money == null) return null;
        return BigDecimal.valueOf(money.getAmount()).divide(BigDecimal.valueOf(100));
    }

    public static Pageable toPageable(PageRequest request) {
        int page = request.getPage() > 0 ? request.getPage() : 0;
        int size = request.getSize() > 0 ? request.getSize() : 20;

        Sort sort = Sort.unsorted();
        if (request.getSortBy() != null && !request.getSortBy().isEmpty()) {
            Sort.Direction direction = "DESC".equalsIgnoreCase(request.getSortDirection())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
            sort = Sort.by(direction, request.getSortBy());
        }

        return org.springframework.data.domain.PageRequest.of(page, size, sort);
    }

    public static PageInfo toPageInfo(Page<?> page) {
        return PageInfo.newBuilder()
            .setPage(page.getNumber())
            .setSize(page.getSize())
            .setTotalElements(page.getTotalElements())
            .setTotalPages(page.getTotalPages())
            .setHasNext(page.hasNext())
            .setHasPrevious(page.hasPrevious())
            .build();
    }
}