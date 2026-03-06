package com.ondeedu.payment.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.payment.dto.*;
import com.ondeedu.payment.entity.*;
import com.ondeedu.payment.repository.PriceListRepository;
import com.ondeedu.payment.repository.StudentPaymentRepository;
import com.ondeedu.payment.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentPaymentService {

    private final StudentPaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PriceListRepository priceListRepository;

    // ── Record a payment ──────────────────────────────────────────────────

    @Transactional
    public StudentPaymentDto recordPayment(RecordPaymentRequest request) {
        Subscription sub = subscriptionRepository.findById(request.getSubscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "id", request.getSubscriptionId()));

        if (!sub.getStudentId().equals(request.getStudentId())) {
            throw new BusinessException("Subscription does not belong to student " + request.getStudentId());
        }

        // HIGH-5: validate paymentMonth falls within subscription period
        String paymentMonth = request.getPaymentMonth();
        YearMonth pm = YearMonth.parse(paymentMonth);
        YearMonth subStart = YearMonth.from(sub.getStartDate());
        if (pm.isBefore(subStart)) {
            throw new BusinessException("Payment month " + paymentMonth
                    + " is before subscription start " + subStart);
        }
        if (sub.getEndDate() != null) {
            YearMonth subEnd = YearMonth.from(sub.getEndDate());
            if (pm.isAfter(subEnd)) {
                throw new BusinessException("Payment month " + paymentMonth
                        + " is after subscription end " + subEnd);
            }
        }

        StudentPayment payment = StudentPayment.builder()
                .studentId(request.getStudentId())
                .subscriptionId(request.getSubscriptionId())
                .amount(request.getAmount())
                .paidAt(request.getPaidAt() != null ? request.getPaidAt() : LocalDate.now())
                .paymentMonth(paymentMonth)
                .method(request.getMethod() != null ? request.getMethod() : PaymentMethod.CASH)
                .notes(request.getNotes())
                .build();

        payment = paymentRepository.save(payment);
        log.debug("Recorded payment {} for subscription {} month {}", payment.getId(),
                request.getSubscriptionId(), paymentMonth);
        return toDto(payment);
    }

    // ── Delete a payment record ───────────────────────────────────────────

    @Transactional
    public void deletePayment(UUID paymentId) {
        StudentPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentPayment", "id", paymentId));
        paymentRepository.delete(payment);
        log.debug("Deleted payment record {}", paymentId);
    }

    // ── Full history for one student ──────────────────────────────────────

    @Transactional(readOnly = true)
    public StudentPaymentHistoryResponse getStudentPaymentHistory(UUID studentId) {
        // Uses dedicated query instead of Page API — no pagination needed here
        List<Subscription> subs = subscriptionRepository.findByStudentIdOrderByStartDateDesc(studentId);

        if (subs.isEmpty()) {
            return StudentPaymentHistoryResponse.builder()
                    .studentId(studentId)
                    .totalPaid(BigDecimal.ZERO)
                    .totalDebt(BigDecimal.ZERO)
                    .subscriptions(List.of())
                    .build();
        }

        // HIGH-2: preload all PriceLists in one query
        Set<UUID> priceListIds = subs.stream()
                .map(Subscription::getPriceListId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PriceList> priceListMap = priceListRepository.findAllById(priceListIds).stream()
                .collect(Collectors.toMap(PriceList::getId, pl -> pl));

        // HIGH-1 (variant): load all payments for this student in one query, group by subscriptionId
        List<StudentPayment> allPayments = paymentRepository.findByStudentIdOrderByPaidAtDesc(studentId);
        Map<UUID, List<StudentPayment>> paymentsBySub = allPayments.stream()
                .collect(Collectors.groupingBy(StudentPayment::getSubscriptionId));

        BigDecimal grandTotalPaid = BigDecimal.ZERO;
        BigDecimal grandTotalDebt = BigDecimal.ZERO;
        List<SubscriptionPaymentSummaryDto> summaries = new ArrayList<>();

        for (Subscription sub : subs) {
            List<StudentPayment> subPayments = paymentsBySub.getOrDefault(sub.getId(), List.of());
            SubscriptionPaymentSummaryDto summary = buildSubscriptionSummary(sub, subPayments, priceListMap);
            grandTotalPaid = grandTotalPaid.add(summary.getTotalPaid());
            grandTotalDebt = grandTotalDebt.add(summary.getTotalDebt());
            summaries.add(summary);
        }

        return StudentPaymentHistoryResponse.builder()
                .studentId(studentId)
                .totalPaid(grandTotalPaid)
                .totalDebt(grandTotalDebt)
                .subscriptions(summaries)
                .build();
    }

    // ── Monthly overview for all students ────────────────────────────────

    @Transactional(readOnly = true)
    public MonthlyOverviewResponse getMonthlyOverview(String month) {
        // MEDIUM: guard against bad format before any DB call
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (Exception e) {
            throw new BusinessException("Invalid month format '" + month + "'. Expected YYYY-MM");
        }
        LocalDate firstDay = ym.atDay(1);
        LocalDate lastDay  = ym.atEndOfMonth();

        // HIGH-3: SQL-level filtering — no findAll() + Java filter
        List<Subscription> activeSubs = subscriptionRepository.findActiveInPeriod(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED),
                firstDay, lastDay);

        if (activeSubs.isEmpty()) {
            return MonthlyOverviewResponse.builder()
                    .month(month).totalStudents(0).paidCount(0).partialCount(0).unpaidCount(0)
                    .totalExpected(BigDecimal.ZERO).totalCollected(BigDecimal.ZERO)
                    .totalDebt(BigDecimal.ZERO).students(List.of()).build();
        }

        // HIGH-2: preload all PriceLists in one query
        Set<UUID> plIds = activeSubs.stream().map(Subscription::getPriceListId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, PriceList> plMap = priceListRepository.findAllById(plIds).stream()
                .collect(Collectors.toMap(PriceList::getId, pl -> pl));

        // HIGH-1: load all payments for this month in one query, group by subscriptionId
        List<UUID> subIds = activeSubs.stream().map(Subscription::getId).toList();
        List<StudentPayment> monthPayments = paymentRepository.findBySubscriptionIdIn(subIds)
                .stream().filter(p -> month.equals(p.getPaymentMonth())).toList();
        Map<UUID, BigDecimal> paidBySubscription = monthPayments.stream()
                .collect(Collectors.groupingBy(StudentPayment::getSubscriptionId,
                        Collectors.reducing(BigDecimal.ZERO, StudentPayment::getAmount, BigDecimal::add)));

        BigDecimal totalExpected  = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalDebt      = BigDecimal.ZERO;
        int paidCount = 0, partialCount = 0, unpaidCount = 0;
        List<MonthlyStudentDto> students = new ArrayList<>();

        for (Subscription sub : activeSubs) {
            BigDecimal monthly = calcMonthlyExpected(sub, plMap);
            BigDecimal paid    = paidBySubscription.getOrDefault(sub.getId(), BigDecimal.ZERO);
            BigDecimal debt    = monthly.subtract(paid).max(BigDecimal.ZERO);
            String status      = resolveStatus(monthly, paid);

            if ("PAID".equals(status))         paidCount++;
            else if ("PARTIAL".equals(status)) partialCount++;
            else                               unpaidCount++;

            totalExpected  = totalExpected.add(monthly);
            totalCollected = totalCollected.add(paid);
            totalDebt      = totalDebt.add(debt);

            students.add(MonthlyStudentDto.builder()
                    .studentId(sub.getStudentId())
                    .subscriptionId(sub.getId())
                    .expected(monthly)
                    .paid(paid)
                    .debt(debt)
                    .status(status)
                    .build());
        }

        // Sort: UNPAID first, then PARTIAL, then PAID
        students.sort(Comparator.comparingInt(s -> statusOrder(s.getStatus())));

        return MonthlyOverviewResponse.builder()
                .month(month)
                .totalStudents(activeSubs.size())
                .paidCount(paidCount)
                .partialCount(partialCount)
                .unpaidCount(unpaidCount)
                .totalExpected(totalExpected)
                .totalCollected(totalCollected)
                .totalDebt(totalDebt)
                .students(students)
                .build();
    }

    // ── Debtors list ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StudentDebtDto> getDebtors() {
        LocalDate today        = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();

        // HIGH-3: SQL-level filtering
        List<Subscription> activeSubs = subscriptionRepository.findActiveNotExpiredBefore(
                SubscriptionStatus.ACTIVE, today);

        if (activeSubs.isEmpty()) return List.of();

        // HIGH-2: preload PriceLists
        Set<UUID> plIds = activeSubs.stream().map(Subscription::getPriceListId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, PriceList> plMap = priceListRepository.findAllById(plIds).stream()
                .collect(Collectors.toMap(PriceList::getId, pl -> pl));

        // HIGH-1: one bulk query for all payments — no N*M DB calls
        List<UUID> subIds = activeSubs.stream().map(Subscription::getId).toList();
        Map<UUID, Map<String, BigDecimal>> paidBySubAndMonth = paymentRepository
                .findBySubscriptionIdIn(subIds).stream()
                .collect(Collectors.groupingBy(StudentPayment::getSubscriptionId,
                        Collectors.groupingBy(StudentPayment::getPaymentMonth,
                                Collectors.reducing(BigDecimal.ZERO, StudentPayment::getAmount, BigDecimal::add))));

        List<StudentDebtDto> debtors = new ArrayList<>();

        for (Subscription sub : activeSubs) {
            BigDecimal monthly = calcMonthlyExpected(sub, plMap);
            if (monthly.compareTo(BigDecimal.ZERO) == 0) continue;

            Map<String, BigDecimal> paidByMonth = paidBySubAndMonth.getOrDefault(sub.getId(), Map.of());

            YearMonth m        = YearMonth.from(sub.getStartDate());
            int debtMonthCount = 0;
            BigDecimal totalDebt = BigDecimal.ZERO;

            // Iterate months in memory — no DB calls inside loop
            while (!m.isAfter(currentMonth)) {
                BigDecimal paid = paidByMonth.getOrDefault(m.toString(), BigDecimal.ZERO);
                BigDecimal debt = monthly.subtract(paid).max(BigDecimal.ZERO);
                if (debt.compareTo(BigDecimal.ZERO) > 0) {
                    debtMonthCount++;
                    totalDebt = totalDebt.add(debt);
                }
                m = m.plusMonths(1);
            }

            if (totalDebt.compareTo(BigDecimal.ZERO) > 0) {
                debtors.add(StudentDebtDto.builder()
                        .studentId(sub.getStudentId())
                        .subscriptionId(sub.getId())
                        .totalDebt(totalDebt)
                        .debtMonths(debtMonthCount)
                        .monthlyExpected(monthly)
                        .build());
            }
        }

        debtors.sort(Comparator.comparing(StudentDebtDto::getTotalDebt).reversed());
        return debtors;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private SubscriptionPaymentSummaryDto buildSubscriptionSummary(Subscription sub,
                                                                    List<StudentPayment> payments,
                                                                    Map<UUID, PriceList> plMap) {
        BigDecimal monthly = calcMonthlyExpected(sub, plMap);
        int months         = calcMonthsDuration(sub, plMap);

        YearMonth startMonth = YearMonth.from(sub.getStartDate());
        // HIGH-4: if endDate is null, show months up to today so debts are visible
        YearMonth endMonth = sub.getEndDate() != null
                ? YearMonth.from(sub.getEndDate())
                : YearMonth.now();

        // Group payments by paymentMonth — no DB calls inside loop
        Map<String, List<StudentPayment>> byMonth = payments.stream()
                .collect(Collectors.groupingBy(StudentPayment::getPaymentMonth));

        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalDebt = BigDecimal.ZERO;
        List<MonthlyBreakdownDto> monthBreakdowns = new ArrayList<>();

        YearMonth m = startMonth;
        while (!m.isAfter(endMonth)) {
            String monthKey = m.toString();
            List<StudentPayment> mp = byMonth.getOrDefault(monthKey, List.of());
            BigDecimal paid = mp.stream().map(StudentPayment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal debt = monthly.subtract(paid).max(BigDecimal.ZERO);

            totalPaid = totalPaid.add(paid);
            totalDebt = totalDebt.add(debt);

            monthBreakdowns.add(MonthlyBreakdownDto.builder()
                    .month(monthKey)
                    .expected(monthly)
                    .paid(paid)
                    .debt(debt)
                    .status(resolveStatus(monthly, paid))
                    .payments(mp.stream().map(this::toDto).collect(Collectors.toList()))
                    .build());

            m = m.plusMonths(1);
        }

        return SubscriptionPaymentSummaryDto.builder()
                .subscriptionId(sub.getId())
                .courseId(sub.getCourseId())
                .priceListId(sub.getPriceListId())
                .totalAmount(sub.getAmount())
                .monthlyExpected(monthly)
                .totalMonths(months)
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .subscriptionStatus(sub.getStatus())
                .totalPaid(totalPaid)
                .totalDebt(totalDebt)
                .months(monthBreakdowns)
                .build();
    }

    /** Рассчитывает ежемесячный взнос. */
    private BigDecimal calcMonthlyExpected(Subscription sub, Map<UUID, PriceList> plMap) {
        int months = calcMonthsDuration(sub, plMap);
        if (months <= 0) return sub.getAmount();
        return sub.getAmount().divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    /** Количество месяцев в подписке. HIGH-2: принимает preloaded map вместо DB-запроса. */
    private int calcMonthsDuration(Subscription sub, Map<UUID, PriceList> plMap) {
        if (sub.getPriceListId() != null) {
            PriceList pl = plMap.get(sub.getPriceListId());
            if (pl != null) {
                return (int) Math.ceil(pl.getValidityDays() / 30.0);
            }
        }
        return calcFromDates(sub);
    }

    private int calcFromDates(Subscription sub) {
        if (sub.getEndDate() != null) {
            long days = ChronoUnit.DAYS.between(sub.getStartDate(), sub.getEndDate());
            return Math.max(1, (int) Math.ceil(days / 30.0));
        }
        return 1;
    }

    private String resolveStatus(BigDecimal expected, BigDecimal paid) {
        if (paid.compareTo(BigDecimal.ZERO) == 0)  return "UNPAID";
        if (paid.compareTo(expected) >= 0)          return "PAID";
        return "PARTIAL";
    }

    private int statusOrder(String status) {
        return switch (status) {
            case "UNPAID"  -> 0;
            case "PARTIAL" -> 1;
            default        -> 2;
        };
    }

    private StudentPaymentDto toDto(StudentPayment p) {
        return StudentPaymentDto.builder()
                .id(p.getId())
                .studentId(p.getStudentId())
                .subscriptionId(p.getSubscriptionId())
                .amount(p.getAmount())
                .paidAt(p.getPaidAt())
                .paymentMonth(p.getPaymentMonth())
                .method(p.getMethod())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
