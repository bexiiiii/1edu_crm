package com.ondeedu.finance.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.finance.dto.*;
import com.ondeedu.finance.entity.TransactionType;
import com.ondeedu.finance.service.FinanceService;
import com.ondeedu.finance.service.SalaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance")
@RequiredArgsConstructor
@Tag(name = "Finance", description = "Finance / Transaction management API")
public class FinanceController {

    private final FinanceService financeService;
    private final SalaryService salaryService;

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_CREATE')")
    @Operation(summary = "Create a new transaction")
    public ApiResponse<TransactionDto> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionDto transaction = financeService.createTransaction(request);
        return ApiResponse.success(transaction, "Transaction created successfully");
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "Get transaction by ID")
    public ApiResponse<TransactionDto> getTransaction(@PathVariable UUID id) {
        return ApiResponse.success(financeService.getTransaction(id));
    }

    @PutMapping("/transactions/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_EDIT')")
    @Operation(summary = "Update transaction")
    public ApiResponse<TransactionDto> updateTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTransactionRequest request) {
        TransactionDto transaction = financeService.updateTransaction(id, request);
        return ApiResponse.success(transaction, "Transaction updated successfully");
    }

    @DeleteMapping("/transactions/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_EDIT')")
    @Operation(summary = "Delete transaction")
    public ApiResponse<Void> deleteTransaction(@PathVariable UUID id) {
        financeService.deleteTransaction(id);
        return ApiResponse.success("Transaction deleted successfully");
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "List transactions with optional type filter")
    public ApiResponse<PageResponse<TransactionDto>> listTransactions(
            @RequestParam(required = false) TransactionType type,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(financeService.listTransactions(type, pageable));
    }

    @GetMapping("/transactions/by-date")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "List transactions by date range")
    public ApiResponse<PageResponse<TransactionDto>> listByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(financeService.listByDateRange(from, to, pageable));
    }

    @GetMapping("/transactions/student/{studentId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "List transactions by student")
    public ApiResponse<PageResponse<TransactionDto>> listByStudent(
            @PathVariable UUID studentId,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(financeService.listByStudent(studentId, pageable));
    }

    @GetMapping("/salary")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "Get monthly salary overview by staff")
    public ApiResponse<SalaryOverviewDto> getSalaryOverview(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer year) {
        return ApiResponse.success(salaryService.getMonthlyOverview(month, year));
    }

    @GetMapping("/salary/staff/{staffId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "Get salary history for a staff member")
    public ApiResponse<StaffSalaryHistoryDto> getStaffSalaryHistory(
            @PathVariable UUID staffId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ApiResponse.success(salaryService.getStaffHistory(staffId, from, to));
    }

    @PostMapping("/salary/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_EDIT')")
    @Operation(summary = "Record a salary payment")
    public ApiResponse<SalaryPaymentDto> recordSalaryPayment(@Valid @RequestBody CreateSalaryPaymentRequest request) {
        return ApiResponse.success(salaryService.recordSalaryPayment(request), "Salary payment recorded successfully");
    }
}
