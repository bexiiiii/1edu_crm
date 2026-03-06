package com.ondeedu.lead.controller;

import com.ondeedu.common.dto.ApiResponse;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.lead.dto.*;
import com.ondeedu.lead.entity.LeadStage;
import com.ondeedu.lead.service.LeadService;
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
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Tag(name = "Leads", description = "Lead management API (Kanban)")
public class LeadController {

    private final LeadService leadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LEADS_CREATE')")
    @Operation(summary = "Create a new lead")
    public ApiResponse<LeadDto> createLead(@Valid @RequestBody CreateLeadRequest request) {
        LeadDto lead = leadService.createLead(request);
        return ApiResponse.success(lead, "Lead created successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LEADS_VIEW')")
    @Operation(summary = "Get lead by ID")
    public ApiResponse<LeadDto> getLead(@PathVariable UUID id) {
        return ApiResponse.success(leadService.getLead(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LEADS_EDIT')")
    @Operation(summary = "Update lead")
    public ApiResponse<LeadDto> updateLead(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLeadRequest request) {
        LeadDto lead = leadService.updateLead(id, request);
        return ApiResponse.success(lead, "Lead updated successfully");
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LEADS_EDIT')")
    @Operation(summary = "Move lead to a new stage (Kanban)")
    public ApiResponse<LeadDto> moveStage(
            @PathVariable UUID id,
            @RequestParam LeadStage stage) {
        LeadDto lead = leadService.moveStage(id, stage);
        return ApiResponse.success(lead, "Lead moved to " + stage);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LEADS_DELETE')")
    @Operation(summary = "Delete lead")
    public ApiResponse<Void> deleteLead(@PathVariable UUID id) {
        leadService.deleteLead(id);
        return ApiResponse.success("Lead deleted successfully");
    }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LEADS_VIEW')")
    @Operation(summary = "List leads with optional stage filter (Kanban view)")
    public ApiResponse<PageResponse<LeadDto>> listLeads(
            @RequestParam(required = false) LeadStage stage,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(leadService.listLeads(stage, pageable));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasAuthority('LEADS_VIEW')")
    @Operation(summary = "Search leads")
    public ApiResponse<PageResponse<LeadDto>> searchLeads(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(leadService.searchLeads(query, pageable));
    }
}
