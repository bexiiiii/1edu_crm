package com.ondeedu.payment.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.payment.dto.CreatePriceListRequest;
import com.ondeedu.payment.dto.PriceListDto;
import com.ondeedu.payment.dto.UpdatePriceListRequest;
import com.ondeedu.payment.service.PriceListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/price-lists")
@RequiredArgsConstructor
@Tag(name = "Price Lists", description = "Price list management API")
public class PriceListController {

    private final PriceListService priceListService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Create a new price list")
    public ApiResponse<PriceListDto> createPriceList(
            @Valid @RequestBody CreatePriceListRequest request) {
        return ApiResponse.success(priceListService.createPriceList(request), "Price list created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'RECEPTIONIST', 'TEACHER')")
    @Operation(summary = "Get price list by ID")
    public ApiResponse<PriceListDto> getPriceList(@PathVariable UUID id) {
        return ApiResponse.success(priceListService.getPriceList(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Update price list")
    public ApiResponse<PriceListDto> updatePriceList(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePriceListRequest request) {
        return ApiResponse.success(priceListService.updatePriceList(id, request), "Price list updated successfully");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER')")
    @Operation(summary = "Delete price list")
    public ApiResponse<Void> deletePriceList(@PathVariable UUID id) {
        priceListService.deletePriceList(id);
        return ApiResponse.success("Price list deleted successfully");
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'RECEPTIONIST', 'TEACHER')")
    @Operation(summary = "List price lists with optional active filter")
    public ApiResponse<PageResponse<PriceListDto>> list(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(priceListService.list(active, pageable));
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'MANAGER', 'RECEPTIONIST', 'TEACHER')")
    @Operation(summary = "List price lists by course")
    public ApiResponse<PageResponse<PriceListDto>> listByCourse(
            @PathVariable UUID courseId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(priceListService.listByCourse(courseId, pageable));
    }
}
