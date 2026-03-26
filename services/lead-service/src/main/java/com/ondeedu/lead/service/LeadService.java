package com.ondeedu.lead.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.lead.dto.CreateLeadRequest;
import com.ondeedu.lead.dto.LeadDto;
import com.ondeedu.lead.dto.UpdateLeadRequest;
import com.ondeedu.lead.entity.Lead;
import com.ondeedu.lead.entity.LeadStage;
import com.ondeedu.lead.mapper.LeadMapper;
import com.ondeedu.lead.search.LeadSearchService;
import com.ondeedu.lead.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;
    private final Optional<LeadSearchService> leadSearchService;
    private final LeadAssignmentNotificationService leadAssignmentNotificationService;

    @Transactional
    public LeadDto createLead(CreateLeadRequest request) {
        Lead lead = leadMapper.toEntity(request);
        lead = leadRepository.save(lead);
        leadAssignmentNotificationService.notifyIfAssigned(null, lead);
        indexLead(lead);
        log.info("Created lead: {} {}", lead.getFirstName(), lead.getLastName());
        return leadMapper.toDto(lead);
    }

    @Transactional(readOnly = true)
    public LeadDto getLead(UUID id) {
        Lead lead = leadRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lead", "id", id));
        return leadMapper.toDto(lead);
    }

    @Transactional
    public LeadDto updateLead(UUID id, UpdateLeadRequest request) {
        Lead lead = leadRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lead", "id", id));
        String previousAssignedTo = lead.getAssignedTo();
        leadMapper.updateEntity(lead, request);
        lead = leadRepository.save(lead);
        leadAssignmentNotificationService.notifyIfAssigned(previousAssignedTo, lead);
        indexLead(lead);
        log.info("Updated lead: {}", id);
        return leadMapper.toDto(lead);
    }

    @Transactional
    public LeadDto moveStage(UUID id, LeadStage newStage) {
        Lead lead = leadRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lead", "id", id));
        lead.setStage(newStage);
        lead = leadRepository.save(lead);
        indexLead(lead);
        log.info("Moved lead {} to stage {}", id, newStage);
        return leadMapper.toDto(lead);
    }

    @Transactional
    public void deleteLead(UUID id) {
        if (!leadRepository.existsById(id)) {
            throw new ResourceNotFoundException("Lead", "id", id);
        }
        leadRepository.deleteById(id);
        deleteLeadFromIndex(id);
        log.info("Deleted lead: {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadDto> listLeads(LeadStage stage, Pageable pageable) {
        Page<Lead> page;
        if (stage != null) {
            page = leadRepository.findByStage(stage, pageable);
        } else {
            page = leadRepository.findAll(pageable);
        }
        return PageResponse.from(page, leadMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadDto> searchLeads(String query, Pageable pageable) {
        String tenantId = TenantContext.getTenantId();

        if (StringUtils.hasText(tenantId) && leadSearchService.isPresent()) {
            try {
                PageResponse<LeadDto> indexedResults =
                        leadSearchService.get().searchLeads(tenantId, query, pageable);
                if (indexedResults.getTotalElements() > 0) {
                    return indexedResults;
                }
            } catch (Exception e) {
                log.warn("Elasticsearch lead search failed, falling back to PostgreSQL: {}", e.getMessage());
            }
        }

        Page<Lead> page = leadRepository.search(query, pageable);
        scheduleIndexBackfill(page.getContent(), tenantId);
        return PageResponse.from(page, leadMapper::toDto);
    }

    private void indexLeads(List<Lead> leads) {
        String tenantId = TenantContext.getTenantId();
        leadSearchService.ifPresent(service -> leads.forEach(lead -> safeIndexLead(service, lead, tenantId)));
    }

    private void indexLead(Lead lead) {
        String tenantId = TenantContext.getTenantId();
        leadSearchService.ifPresent(service -> safeIndexLead(service, lead, tenantId));
    }

    private void safeIndexLead(LeadSearchService service, Lead lead, String tenantId) {
        try {
            service.indexLead(lead, tenantId);
        } catch (Exception e) {
            log.warn("Failed to index lead {} in Elasticsearch: {}", lead.getId(), e.getMessage());
        }
    }

    private void scheduleIndexBackfill(List<Lead> leads, String tenantId) {
        if (!StringUtils.hasText(tenantId) || leads == null || leads.isEmpty()) {
            return;
        }
        leadSearchService.ifPresent(service -> CompletableFuture.runAsync(
                () -> leads.forEach(lead -> safeIndexLead(service, lead, tenantId))
        ));
    }

    private void deleteLeadFromIndex(UUID id) {
        leadSearchService.ifPresent(service -> {
            try {
                service.deleteLead(id);
            } catch (Exception e) {
                log.warn("Failed to delete lead {} from Elasticsearch: {}", id, e.getMessage());
            }
        });
    }
}
