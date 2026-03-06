package com.ondeedu.tenant.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.tenant.dto.admin.*;
import com.ondeedu.tenant.entity.TenantStatus;
import com.ondeedu.tenant.service.AdminDashboardService;
import com.ondeedu.tenant.service.SuperAdminAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin", description = "Cross-tenant monitoring and management API (SUPER_ADMIN only)")
public class AdminController {

    private final AdminDashboardService adminDashboardService;
    private final SuperAdminAnalyticsService analyticsService;

    // -----------------------------------------------------------------------
    // Dashboard
    // -----------------------------------------------------------------------

    @GetMapping("/dashboard")
    @Operation(summary = "Global dashboard: all tenants stats, revenue, usage")
    public ApiResponse<AdminDashboardResponse> getDashboard() {
        return ApiResponse.success(adminDashboardService.getDashboard());
    }

    // -----------------------------------------------------------------------
    // Tenant list with stats
    // -----------------------------------------------------------------------

    @GetMapping("/tenants")
    @Operation(summary = "List all tenants with per-tenant usage stats")
    public ApiResponse<List<TenantStatsDto>> listTenants(
            @RequestParam(required = false) TenantStatus status) {
        return ApiResponse.success(adminDashboardService.listAllWithStats(status));
    }

    @GetMapping("/tenants/{id}/stats")
    @Operation(summary = "Get detailed stats for a single tenant")
    public ApiResponse<TenantStatsDto> getTenantStats(@PathVariable UUID id) {
        return ApiResponse.success(adminDashboardService.getTenantStats(id));
    }

    // -----------------------------------------------------------------------
    // Tenant management
    // -----------------------------------------------------------------------

    @PatchMapping("/tenants/{id}/status")
    @Operation(summary = "Change tenant status (TRIAL → ACTIVE / SUSPENDED / INACTIVE)")
    public ApiResponse<TenantStatsDto> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeTenantStatusRequest request) {
        return ApiResponse.success(
                adminDashboardService.changeStatus(id, request),
                "Tenant status changed to " + request.getStatus());
    }

    @PatchMapping("/tenants/{id}/plan")
    @Operation(summary = "Change tenant plan and limits (maxStudents, maxStaff)")
    public ApiResponse<TenantStatsDto> changePlan(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeTenantPlanRequest request) {
        return ApiResponse.success(
                adminDashboardService.changePlan(id, request),
                "Tenant plan changed to " + request.getPlan());
    }

    // -----------------------------------------------------------------------
    // Advanced Analytics
    // -----------------------------------------------------------------------

    @GetMapping("/analytics/platform")
    @Operation(summary = "Platform KPIs: MRR/ARR, ARPU, activeRate, trialConversionRate, total usage")
    public ApiResponse<PlatformKpiResponse> getPlatformKpis() {
        return ApiResponse.success(analyticsService.getPlatformKpis());
    }

    @GetMapping("/analytics/revenue-trend")
    @Operation(summary = "Monthly revenue trend across all tenant schemas (last N months)")
    public ApiResponse<List<RevenueTrendDto>> getRevenueTrend(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.success(analyticsService.getRevenueTrend(Math.min(months, 24)));
    }

    @GetMapping("/analytics/tenant-growth")
    @Operation(summary = "Tenant registration and churn by month (last N months)")
    public ApiResponse<List<TenantGrowthDto>> getTenantGrowth(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.success(analyticsService.getTenantGrowth(Math.min(months, 24)));
    }

    @GetMapping("/analytics/churn")
    @Operation(summary = "Churn analysis: churnRate30d/90d, recently churned tenants, breakdown by plan")
    public ApiResponse<ChurnAnalyticsResponse> getChurnAnalytics() {
        return ApiResponse.success(analyticsService.getChurnAnalytics());
    }

    // -----------------------------------------------------------------------
    // Ban / Unban
    // -----------------------------------------------------------------------

    @PostMapping("/tenants/{id}/ban")
    @Operation(summary = "Ban tenant: status → BANNED, schema stays intact")
    public ApiResponse<TenantStatsDto> banTenant(
            @PathVariable UUID id,
            @Valid @RequestBody BanTenantRequest request) {
        return ApiResponse.success(adminDashboardService.ban(id, request), "Tenant banned");
    }

    @PostMapping("/tenants/{id}/unban")
    @Operation(summary = "Unban tenant: status → ACTIVE, clears ban metadata")
    public ApiResponse<TenantStatsDto> unbanTenant(@PathVariable UUID id) {
        return ApiResponse.success(adminDashboardService.unban(id), "Tenant unbanned");
    }

    @GetMapping("/tenants/banned")
    @Operation(summary = "List all currently banned tenants")
    public ApiResponse<List<TenantStatsDto>> listBanned() {
        return ApiResponse.success(adminDashboardService.listBanned());
    }

    // -----------------------------------------------------------------------
    // Soft delete / Restore
    // -----------------------------------------------------------------------

    @DeleteMapping("/tenants/{id}")
    @Operation(summary = "Soft-delete tenant: sets deleted_at, status → INACTIVE. Data preserved.")
    public ApiResponse<TenantStatsDto> softDelete(@PathVariable UUID id) {
        return ApiResponse.success(adminDashboardService.softDelete(id), "Tenant soft-deleted");
    }

    @PostMapping("/tenants/{id}/restore")
    @Operation(summary = "Restore soft-deleted tenant: clears deleted_at, status → ACTIVE")
    public ApiResponse<TenantStatsDto> restore(@PathVariable UUID id) {
        return ApiResponse.success(adminDashboardService.restore(id), "Tenant restored");
    }

    @GetMapping("/tenants/deleted")
    @Operation(summary = "List all soft-deleted tenants")
    public ApiResponse<List<TenantStatsDto>> listDeleted() {
        return ApiResponse.success(adminDashboardService.listDeleted());
    }

    // -----------------------------------------------------------------------
    // Hard delete (irreversible)
    // -----------------------------------------------------------------------

    @DeleteMapping("/tenants/{id}/permanent")
    @Operation(summary = "PERMANENT delete: removes DB record AND drops tenant schema (irreversible!)")
    public ApiResponse<Void> hardDelete(@PathVariable UUID id) {
        adminDashboardService.hardDelete(id);
        return ApiResponse.success(null, "Tenant permanently deleted");
    }
}
