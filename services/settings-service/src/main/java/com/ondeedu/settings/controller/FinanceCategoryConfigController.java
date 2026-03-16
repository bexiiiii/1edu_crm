package com.ondeedu.settings.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.settings.dto.FinanceCategoryConfigDto;
import com.ondeedu.settings.dto.SaveFinanceCategoryRequest;
import com.ondeedu.settings.entity.FinanceCategoryType;
import com.ondeedu.settings.service.FinanceCategoryConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Finance Category Config", description = "Custom income and expense categories")
public class FinanceCategoryConfigController {

    private final FinanceCategoryConfigService service;

    @GetMapping("/income-categories")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Get all custom income categories")
    public ApiResponse<List<FinanceCategoryConfigDto>> getIncomeCategories() {
        return ApiResponse.success(service.getAll(FinanceCategoryType.INCOME));
    }

    @PostMapping("/income-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('ACCOUNTANT')")
    @Operation(summary = "Create a custom income category")
    public ApiResponse<FinanceCategoryConfigDto> createIncomeCategory(
            @Valid @RequestBody SaveFinanceCategoryRequest request) {
        return ApiResponse.success(service.create(FinanceCategoryType.INCOME, request),
                "Income category created successfully");
    }

    @PutMapping("/income-categories/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('ACCOUNTANT')")
    @Operation(summary = "Update a custom income category")
    public ApiResponse<FinanceCategoryConfigDto> updateIncomeCategory(
            @PathVariable UUID id,
            @Valid @RequestBody SaveFinanceCategoryRequest request) {
        return ApiResponse.success(service.update(FinanceCategoryType.INCOME, id, request),
                "Income category updated successfully");
    }

    @DeleteMapping("/income-categories/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('ACCOUNTANT')")
    @Operation(summary = "Delete a custom income category")
    public ApiResponse<Void> deleteIncomeCategory(@PathVariable UUID id) {
        service.delete(FinanceCategoryType.INCOME, id);
        return ApiResponse.success("Income category deleted successfully");
    }

    @GetMapping("/expense-categories")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','MANAGER','ACCOUNTANT')")
    @Operation(summary = "Get all custom expense categories")
    public ApiResponse<List<FinanceCategoryConfigDto>> getExpenseCategories() {
        return ApiResponse.success(service.getAll(FinanceCategoryType.EXPENSE));
    }

    @PostMapping("/expense-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('ACCOUNTANT')")
    @Operation(summary = "Create a custom expense category")
    public ApiResponse<FinanceCategoryConfigDto> createExpenseCategory(
            @Valid @RequestBody SaveFinanceCategoryRequest request) {
        return ApiResponse.success(service.create(FinanceCategoryType.EXPENSE, request),
                "Expense category created successfully");
    }

    @PutMapping("/expense-categories/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('ACCOUNTANT')")
    @Operation(summary = "Update a custom expense category")
    public ApiResponse<FinanceCategoryConfigDto> updateExpenseCategory(
            @PathVariable UUID id,
            @Valid @RequestBody SaveFinanceCategoryRequest request) {
        return ApiResponse.success(service.update(FinanceCategoryType.EXPENSE, id, request),
                "Expense category updated successfully");
    }

    @DeleteMapping("/expense-categories/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('ACCOUNTANT')")
    @Operation(summary = "Delete a custom expense category")
    public ApiResponse<Void> deleteExpenseCategory(@PathVariable UUID id) {
        service.delete(FinanceCategoryType.EXPENSE, id);
        return ApiResponse.success("Expense category deleted successfully");
    }
}
