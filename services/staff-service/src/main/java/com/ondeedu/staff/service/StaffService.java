package com.ondeedu.staff.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.staff.dto.CreateStaffRequest;
import com.ondeedu.staff.dto.StaffDto;
import com.ondeedu.staff.dto.UpdateStaffRequest;
import com.ondeedu.staff.entity.Staff;
import com.ondeedu.staff.entity.StaffRole;
import com.ondeedu.staff.entity.StaffStatus;
import com.ondeedu.staff.mapper.StaffMapper;
import com.ondeedu.staff.repository.StaffRepository;
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
public class StaffService {

    private final StaffRepository staffRepository;
    private final StaffMapper staffMapper;

    @Transactional
    public StaffDto createStaff(CreateStaffRequest request) {
        Staff staff = staffMapper.toEntity(request);
        staff = staffRepository.save(staff);
        log.info("Created staff: {} {}", staff.getFirstName(), staff.getLastName());
        return staffMapper.toDto(staff);
    }

    @Transactional(readOnly = true)
    public StaffDto getStaff(UUID id) {
        Staff staff = staffRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", id));
        return staffMapper.toDto(staff);
    }

    @Transactional
    public StaffDto updateStaff(UUID id, UpdateStaffRequest request) {
        Staff staff = staffRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Staff", "id", id));
        staffMapper.updateEntity(staff, request);
        staff = staffRepository.save(staff);
        log.info("Updated staff: {}", id);
        return staffMapper.toDto(staff);
    }

    @Transactional
    public void deleteStaff(UUID id) {
        if (!staffRepository.existsById(id)) {
            throw new ResourceNotFoundException("Staff", "id", id);
        }
        staffRepository.deleteById(id);
        log.info("Deleted staff: {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffDto> listStaff(StaffRole role, StaffStatus status, Pageable pageable) {
        Page<Staff> page;
        if (role != null) {
            page = staffRepository.findByRole(role, pageable);
        } else if (status != null) {
            page = staffRepository.findByStatus(status, pageable);
        } else {
            page = staffRepository.findAll(pageable);
        }
        return PageResponse.from(page, staffMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<StaffDto> searchStaff(String query, Pageable pageable) {
        Page<Staff> page = staffRepository.search(query, pageable);
        return PageResponse.from(page, staffMapper::toDto);
    }
}
