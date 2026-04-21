package com.ondeedu.payment.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.payment.dto.*;
import com.ondeedu.payment.service.StudentPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/student-payments")
@RequiredArgsConstructor
@Tag(name = "Student Payments", description = "Учёт ежемесячных платежей студентов")
public class StudentPaymentController {

    private final StudentPaymentService studentPaymentService;

    // ── Record payment ───────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_CREATE')")
    @Operation(summary = "Записать платёж студента",
               description = "Регистрирует фактический взнос в счёт конкретной подписки и месяца")
    public ApiResponse<StudentPaymentDto> recordPayment(
            @Valid @RequestBody RecordPaymentRequest request) {
        return ApiResponse.success(
                studentPaymentService.recordPayment(request), "Платёж записан");
    }

    // ── Student payment history ──────────────────────────────────────────

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "История платежей студента",
               description = "Возвращает все подписки студента с помесячной разбивкой: ожидаемо, оплачено, долг")
    public ApiResponse<StudentPaymentHistoryResponse> getStudentPaymentHistory(
            @PathVariable UUID studentId) {
        return ApiResponse.success(
                studentPaymentService.getStudentPaymentHistory(studentId));
    }

    // ── Monthly overview ─────────────────────────────────────────────────

    @GetMapping("/overview")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "Месячный отчёт по платежам",
               description = "Показывает статус оплаты каждого активного студента за указанный месяц (PAID/PARTIAL/UNPAID)")
    public ApiResponse<MonthlyOverviewResponse> getMonthlyOverview(
            @RequestParam(required = false) String month) {
        // Default to current month
        String targetMonth = (month != null && !month.isBlank())
                ? month
                : YearMonth.now().toString();
        return ApiResponse.success(
                studentPaymentService.getMonthlyOverview(targetMonth));
    }

    // ── Debtors ──────────────────────────────────────────────────────────

    @GetMapping("/debtors")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_VIEW')")
    @Operation(summary = "Список должников",
               description = "Студенты с накопленным долгом. Можно указать month (YYYY-MM) для фильтрации по конкретному месяцу")
    public ApiResponse<List<StudentDebtDto>> getDebtors(
            @RequestParam(required = false) String month) {
        return ApiResponse.success(studentPaymentService.getDebtors(month));
    }

    // ── Delete payment record ────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('FINANCE_EDIT')")
    @Operation(summary = "Удалить запись платежа",
               description = "Используется для исправления ошибочно записанного платежа")
    public ApiResponse<Void> deletePayment(@PathVariable UUID id) {
        studentPaymentService.deletePayment(id);
        return ApiResponse.success("Запись платежа удалена");
    }
}
