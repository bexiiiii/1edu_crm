package com.ondeedu.finance.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.payroll.SalaryType;
import com.ondeedu.finance.dto.CreateSalaryPaymentRequest;
import com.ondeedu.finance.dto.SalaryMonthBreakdownDto;
import com.ondeedu.finance.dto.SalaryOverviewDto;
import com.ondeedu.finance.dto.SalaryPaymentDto;
import com.ondeedu.finance.dto.StaffSalaryHistoryDto;
import com.ondeedu.finance.dto.StaffSalarySummaryDto;
import com.ondeedu.finance.entity.Transaction;
import com.ondeedu.finance.entity.TransactionStatus;
import com.ondeedu.finance.entity.TransactionType;
import com.ondeedu.finance.repository.SalaryComputationRow;
import com.ondeedu.finance.repository.SalaryQueryRepository;
import com.ondeedu.finance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryService {

    private static final String DEFAULT_CURRENCY = "KZT";
    private static final String SALARY_CATEGORY = "SALARY";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SalaryQueryRepository salaryQueryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public SalaryOverviewDto getMonthlyOverview(String monthParam, Integer yearParam) {
        YearMonth yearMonth = resolveYearMonth(monthParam, yearParam);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();
        String salaryMonth = yearMonth.format(MONTH_FORMATTER);

        List<Transaction> monthlyPayments = transactionRepository
                .findBySalaryMonthAndStaffIdIsNotNullOrderByTransactionDateDescCreatedAtDesc(salaryMonth);
        Map<UUID, List<SalaryPaymentDto>> paymentsByStaffId = monthlyPayments.stream()
                .map(this::toSalaryPaymentDto)
                .collect(java.util.stream.Collectors.groupingBy(
                        SalaryPaymentDto::getStaffId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        List<StaffSalarySummaryDto> entries = salaryQueryRepository.listSalaryRows(monthStart, monthEnd).stream()
                .map(row -> toStaffSalarySummary(row, salaryMonth, paymentsByStaffId.getOrDefault(row.staffId(), List.of())))
                .toList();

        BigDecimal totalDue = entries.stream()
                .map(StaffSalarySummaryDto::getDueAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = entries.stream()
                .map(StaffSalarySummaryDto::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return SalaryOverviewDto.builder()
                .month(salaryMonth)
                .year(yearMonth.getYear())
                .currency(DEFAULT_CURRENCY)
                .totalStaff(entries.size())
                .totalDue(totalDue)
                .totalPaid(totalPaid)
                .totalOutstanding(totalDue.subtract(totalPaid))
                .entries(entries)
                .build();
    }

    @Transactional(readOnly = true)
    public StaffSalaryHistoryDto getStaffHistory(UUID staffId, String fromMonth, String toMonth) {
        YearMonth currentMonth = YearMonth.now();
        SalaryComputationRow currentRow = salaryQueryRepository.findSalaryRow(staffId, currentMonth.atDay(1), currentMonth.atEndOfMonth())
                .orElseThrow(() -> new ResourceNotFoundException("Staff salary profile", "staffId", staffId));

        List<Transaction> payments = transactionRepository.findByStaffIdAndSalaryMonthIsNotNullOrderByTransactionDateDescCreatedAtDesc(staffId);
        Map<String, List<SalaryPaymentDto>> paymentsByMonth = payments.stream()
                .map(this::toSalaryPaymentDto)
                .collect(java.util.stream.Collectors.groupingBy(
                        SalaryPaymentDto::getSalaryMonth,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        YearMonth startMonth = resolveHistoryStart(currentRow, payments, fromMonth);
        YearMonth endMonth = resolveHistoryEnd(toMonth, currentMonth);
        if (startMonth.isAfter(endMonth)) {
            throw new BusinessException("INVALID_SALARY_RANGE", "from month must be before to month");
        }

        List<SalaryMonthBreakdownDto> months = new ArrayList<>();
        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (YearMonth cursor = startMonth; !cursor.isAfter(endMonth); cursor = cursor.plusMonths(1)) {
            LocalDate monthStart = cursor.atDay(1);
            LocalDate monthEnd = cursor.atEndOfMonth();
            SalaryComputationRow row = salaryQueryRepository.findSalaryRow(staffId, monthStart, monthEnd).orElse(currentRow);
            String salaryMonth = cursor.format(MONTH_FORMATTER);
            BigDecimal dueAmount = calculateDueAmount(row);
            BigDecimal paidAmount = paymentsByMonth.getOrDefault(salaryMonth, List.of()).stream()
                    .map(SalaryPaymentDto::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            totalDue = totalDue.add(dueAmount);
            totalPaid = totalPaid.add(paidAmount);

            months.add(SalaryMonthBreakdownDto.builder()
                    .month(salaryMonth)
                    .activeStudentCount(row.activeStudentCount())
                    .percentageBaseAmount(row.percentageBaseAmount())
                    .dueAmount(dueAmount)
                    .paidAmount(paidAmount)
                    .outstandingAmount(dueAmount.subtract(paidAmount))
                    .build());
        }

        return StaffSalaryHistoryDto.builder()
                .staffId(currentRow.staffId())
                .fullName(fullName(currentRow))
                .role(currentRow.role())
                .status(currentRow.status())
                .salaryType(currentRow.salaryType())
                .fixedSalary(currentRow.fixedSalary())
                .salaryPercentage(currentRow.salaryPercentage())
                .totalDue(totalDue)
                .totalPaid(totalPaid)
                .totalOutstanding(totalDue.subtract(totalPaid))
                .months(months)
                .payments(payments.stream().map(this::toSalaryPaymentDto).toList())
                .build();
    }

    @Transactional
    public SalaryPaymentDto recordSalaryPayment(CreateSalaryPaymentRequest request) {
        YearMonth yearMonth = resolveYearMonth(request.getSalaryMonth(), null);
        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();

        SalaryComputationRow currentRow = salaryQueryRepository.findSalaryRow(request.getStaffId(), paymentDate.withDayOfMonth(1), paymentDate.withDayOfMonth(paymentDate.lengthOfMonth()))
                .orElseThrow(() -> new ResourceNotFoundException("Staff salary profile", "staffId", request.getStaffId()));

        Transaction transaction = Transaction.builder()
                .type(TransactionType.EXPENSE)
                .status(TransactionStatus.COMPLETED)
                .amount(request.getAmount())
                .currency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency() : DEFAULT_CURRENCY)
                .category(SALARY_CATEGORY)
                .description("Salary payment for " + fullName(currentRow) + " (" + yearMonth.format(MONTH_FORMATTER) + ")")
                .transactionDate(paymentDate)
                .staffId(request.getStaffId())
                .salaryMonth(yearMonth.format(MONTH_FORMATTER))
                .notes(request.getNotes())
                .build();

        transaction = transactionRepository.save(transaction);
        log.info("Recorded salary payment {} for staff {} month {}", transaction.getId(), request.getStaffId(), transaction.getSalaryMonth());
        return toSalaryPaymentDto(transaction);
    }

    private StaffSalarySummaryDto toStaffSalarySummary(
            SalaryComputationRow row,
            String salaryMonth,
            List<SalaryPaymentDto> payments
    ) {
        BigDecimal dueAmount = calculateDueAmount(row);
        BigDecimal paidAmount = payments.stream()
                .map(SalaryPaymentDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return StaffSalarySummaryDto.builder()
                .staffId(row.staffId())
                .fullName(fullName(row))
                .role(row.role())
                .status(row.status())
                .salaryType(row.salaryType())
                .fixedSalary(row.fixedSalary())
                .salaryPercentage(row.salaryPercentage())
                .activeStudentCount(row.activeStudentCount())
                .percentageBaseAmount(row.percentageBaseAmount())
                .dueAmount(dueAmount)
                .paidAmount(paidAmount)
                .outstandingAmount(dueAmount.subtract(paidAmount))
                .payments(payments.stream()
                        .sorted(Comparator.comparing(SalaryPaymentDto::getPaymentDate).reversed())
                        .toList())
                .build();
    }

    private BigDecimal calculateDueAmount(SalaryComputationRow row) {
        if (row.salaryType() == SalaryType.PER_STUDENT_PERCENTAGE) {
            return row.percentageBaseAmount()
                    .multiply(row.salaryPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        return row.fixedSalary();
    }

    private YearMonth resolveYearMonth(String monthParam, Integer yearParam) {
        if (StringUtils.hasText(monthParam)) {
            String normalized = monthParam.trim();
            try {
                if (normalized.matches("^\\d{4}-\\d{2}$")) {
                    return YearMonth.parse(normalized, MONTH_FORMATTER);
                }
                if (normalized.matches("^\\d{1,2}$") && yearParam != null) {
                    return YearMonth.of(yearParam, Integer.parseInt(normalized));
                }
            } catch (DateTimeParseException | IllegalArgumentException ex) {
                throw new BusinessException("INVALID_SALARY_MONTH", "month must be in YYYY-MM format");
            }
            throw new BusinessException("INVALID_SALARY_MONTH", "month must be in YYYY-MM format");
        }

        if (yearParam != null) {
            return YearMonth.of(yearParam, LocalDate.now().getMonthValue());
        }

        return YearMonth.now();
    }

    private YearMonth resolveHistoryStart(SalaryComputationRow currentRow, List<Transaction> payments, String fromMonth) {
        if (StringUtils.hasText(fromMonth)) {
            return resolveYearMonth(fromMonth, null);
        }

        List<YearMonth> candidates = new ArrayList<>();
        if (currentRow.hireDate() != null) {
            candidates.add(YearMonth.from(currentRow.hireDate()));
        }
        payments.stream()
                .map(Transaction::getSalaryMonth)
                .filter(StringUtils::hasText)
                .map(month -> YearMonth.parse(month, MONTH_FORMATTER))
                .min(Comparator.naturalOrder())
                .ifPresent(candidates::add);

        return candidates.stream().min(Comparator.naturalOrder()).orElse(YearMonth.now());
    }

    private YearMonth resolveHistoryEnd(String toMonth, YearMonth fallback) {
        if (!StringUtils.hasText(toMonth)) {
            return fallback;
        }
        return resolveYearMonth(toMonth, null);
    }

    private String fullName(SalaryComputationRow row) {
        return (row.firstName() + " " + row.lastName()).trim();
    }

    private SalaryPaymentDto toSalaryPaymentDto(Transaction transaction) {
        return SalaryPaymentDto.builder()
                .transactionId(transaction.getId())
                .staffId(transaction.getStaffId())
                .salaryMonth(transaction.getSalaryMonth())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .paymentDate(transaction.getTransactionDate())
                .notes(transaction.getNotes())
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
