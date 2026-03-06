package com.ondeedu.lead.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.lead.dto.CreateLeadRequest;
import com.ondeedu.lead.dto.LeadDto;
import com.ondeedu.lead.dto.UpdateLeadRequest;
import com.ondeedu.lead.entity.Lead;
import com.ondeedu.lead.entity.LeadStage;
import com.ondeedu.lead.mapper.LeadMapper;
import com.ondeedu.lead.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;

    @Transactional
    public LeadDto createLead(CreateLeadRequest request) {
        Lead lead = leadMapper.toEntity(request);
        lead = leadRepository.save(lead);
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
        leadMapper.updateEntity(lead, request);
        lead = leadRepository.save(lead);
        log.info("Updated lead: {}", id);
        return leadMapper.toDto(lead);
    }

    @Transactional
    public LeadDto moveStage(UUID id, LeadStage newStage) {
        Lead lead = leadRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Lead", "id", id));
        lead.setStage(newStage);
        lead = leadRepository.save(lead);
        log.info("Moved lead {} to stage {}", id, newStage);
        return leadMapper.toDto(lead);
    }

    @Transactional
    public void deleteLead(UUID id) {
        if (!leadRepository.existsById(id)) {
            throw new ResourceNotFoundException("Lead", "id", id);
        }
        leadRepository.deleteById(id);
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
        Page<Lead> page = leadRepository.search(query, pageable);
        return PageResponse.from(page, leadMapper::toDto);
    }
}
