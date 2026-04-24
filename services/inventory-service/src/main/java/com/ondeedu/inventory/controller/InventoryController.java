package com.ondeedu.inventory.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.inventory.dto.*;
import com.ondeedu.inventory.service.InventoryExcelExportService;
import com.ondeedu.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Inventory management — items, transactions, categories, units")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryExcelExportService excelExportService;

    // ==================== Items ====================

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Create inventory item", description = "Create a new inventory item")
    public ApiResponse<InventoryItemDto> createItem(@Valid @RequestBody CreateInventoryItemRequest request) {
        return ApiResponse.success(inventoryService.createItem(request), "Item created successfully");
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Get inventory item", description = "Get item by ID")
    public ApiResponse<InventoryItemDto> getItem(@PathVariable UUID id) {
        return ApiResponse.success(inventoryService.getItem(id));
    }

    @GetMapping("/items")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "List inventory items", description = "List items with optional filters")
    public ApiResponse<PageResponse<InventoryItemDto>> listItems(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(inventoryService.listItems(status, search, page, size));
    }

    @GetMapping("/items/category/{categoryId}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Get items by category", description = "List items in a specific category")
    public ApiResponse<PageResponse<InventoryItemDto>> getItemsByCategory(
        @PathVariable UUID categoryId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(inventoryService.getItemsByCategory(categoryId, page, size));
    }

    @GetMapping("/items/reorder-required")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Get items requiring reorder", description = "List items with low/out of stock")
    public ApiResponse<PageResponse<InventoryItemDto>> getReorderRequired(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(inventoryService.getReorderRequired(page, size));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Update inventory item", description = "Update item details")
    public ApiResponse<InventoryItemDto> updateItem(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateInventoryItemRequest request) {
        return ApiResponse.success(inventoryService.updateItem(id, request), "Item updated successfully");
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Delete inventory item", description = "Delete item (only if quantity is zero)")
    public ApiResponse<Void> deleteItem(@PathVariable UUID id) {
        inventoryService.deleteItem(id);
        return ApiResponse.success("Item deleted successfully");
    }

    // ==================== Transactions ====================

    @PostMapping("/items/{itemId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Record inventory transaction", description = "Record stock movement (received, issued, adjustment, etc.)")
    public ApiResponse<InventoryTransactionDto> recordTransaction(
        @PathVariable UUID itemId,
        @Valid @RequestBody CreateInventoryTransactionRequest request) {
        return ApiResponse.success(inventoryService.recordTransaction(itemId, request), "Transaction recorded successfully");
    }

    @GetMapping("/items/{itemId}/transactions")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Get item transactions", description = "Get transaction history for an item")
    public ApiResponse<PageResponse<InventoryTransactionDto>> getItemTransactions(
        @PathVariable UUID itemId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(inventoryService.getItemTransactions(itemId, page, size));
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "List transactions by type", description = "Filter transactions by type")
    public ApiResponse<PageResponse<InventoryTransactionDto>> getTransactionsByType(
        @RequestParam String transactionType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(inventoryService.getTransactionsByType(transactionType, page, size));
    }

    @GetMapping("/transactions/date-range")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Get transactions by date range", description = "Filter transactions by date range")
    public ApiResponse<PageResponse<InventoryTransactionDto>> getTransactionsByDateRange(
        @RequestParam LocalDateTime fromDate,
        @RequestParam LocalDateTime toDate,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(inventoryService.getTransactionsByDateRange(fromDate, toDate, page, size));
    }

    // ==================== Categories ====================

    @GetMapping("/categories")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "List categories", description = "Get all inventory categories")
    public ApiResponse<List<InventoryCategoryDto>> getCategories() {
        return ApiResponse.success(inventoryService.getCategories());
    }

    @GetMapping("/categories/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Get category", description = "Get category by ID")
    public ApiResponse<InventoryCategoryDto> getCategory(@PathVariable UUID id) {
        return ApiResponse.success(inventoryService.getCategory(id));
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Create category", description = "Create a new inventory category")
    public ApiResponse<InventoryCategoryDto> createCategory(@Valid @RequestBody SaveInventoryCategoryRequest request) {
        return ApiResponse.success(inventoryService.createCategory(request), "Category created successfully");
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Update category", description = "Update category details")
    public ApiResponse<InventoryCategoryDto> updateCategory(
        @PathVariable UUID id,
        @Valid @RequestBody SaveInventoryCategoryRequest request) {
        return ApiResponse.success(inventoryService.updateCategory(id, request), "Category updated successfully");
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Delete category", description = "Delete category (only if no items assigned)")
    public ApiResponse<Void> deleteCategory(@PathVariable UUID id) {
        inventoryService.deleteCategory(id);
        return ApiResponse.success("Category deleted successfully");
    }

    // ==================== Units ====================

    @GetMapping("/units")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "List units", description = "Get all inventory units")
    public ApiResponse<List<InventoryUnitDto>> getUnits() {
        return ApiResponse.success(inventoryService.getUnits());
    }

    @GetMapping("/units/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Get unit", description = "Get unit by ID")
    public ApiResponse<InventoryUnitDto> getUnit(@PathVariable UUID id) {
        return ApiResponse.success(inventoryService.getUnit(id));
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Create unit", description = "Create a new inventory unit of measurement")
    public ApiResponse<InventoryUnitDto> createUnit(@Valid @RequestBody SaveInventoryUnitRequest request) {
        return ApiResponse.success(inventoryService.createUnit(request), "Unit created successfully");
    }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Update unit", description = "Update unit details")
    public ApiResponse<InventoryUnitDto> updateUnit(
        @PathVariable UUID id,
        @Valid @RequestBody SaveInventoryUnitRequest request) {
        return ApiResponse.success(inventoryService.updateUnit(id, request), "Unit updated successfully");
    }

    @DeleteMapping("/units/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_EDIT')")
    @Operation(summary = "Delete unit", description = "Delete unit of measurement")
    public ApiResponse<Void> deleteUnit(@PathVariable UUID id) {
        inventoryService.deleteUnit(id);
        return ApiResponse.success("Unit deleted successfully");
    }

    // ==================== Dashboard ====================

    @GetMapping("/stats")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Get inventory stats", description = "Get dashboard statistics for inventory")
    public ApiResponse<InventoryStatsDto> getStats() {
        return ApiResponse.success(inventoryService.getStats());
    }

    // ==================== Report & Export ====================

    @GetMapping("/report")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Инвентаризационный отчёт (JSON)", description = "Полный список товаров с остатками и общей стоимостью")
    public ApiResponse<InventoryReportDto> getInventoryReport() {
        return ApiResponse.success(inventoryService.getInventoryReport());
    }

    @GetMapping("/report/export")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Скачать инвентаризационный отчёт (Excel)")
    public ResponseEntity<byte[]> exportInventoryReport() {
        InventoryReportDto report = inventoryService.getInventoryReport();
        byte[] file = excelExportService.exportInventoryReport(report);
        return ExcelResponseFactory.attachment("inventory-report.xlsx", file);
    }

    @GetMapping("/items/export")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Скачать список товаров (Excel)")
    public ResponseEntity<byte[]> exportItems() {
        List<InventoryItemDto> items = inventoryService.listAllItemsForExport();
        byte[] file = excelExportService.exportItems(items);
        return ExcelResponseFactory.attachment("inventory-items.xlsx", file);
    }

    @GetMapping("/transactions/export")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('INVENTORY_VIEW')")
    @Operation(summary = "Скачать журнал движения товаров (Excel)")
    public ResponseEntity<byte[]> exportTransactions(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<InventoryTransactionDto> transactions = inventoryService.listAllTransactionsForExport(fromDate, toDate);
        byte[] file = excelExportService.exportTransactions(transactions, fromDate, toDate);
        return ExcelResponseFactory.attachment("inventory-transactions.xlsx", file);
    }
}
