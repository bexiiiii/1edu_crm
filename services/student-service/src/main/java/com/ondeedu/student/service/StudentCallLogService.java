package com.ondeedu.student.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.student.dto.SaveStudentCallLogRequest;
import com.ondeedu.student.dto.StudentCallLogDto;
import com.ondeedu.student.entity.StudentCallLog;
import com.ondeedu.student.repository.StudentCallLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCallLogService {

    private final StudentCallLogRepository callLogRepository;

    @Transactional
    public StudentCallLogDto createCallLog(SaveStudentCallLogRequest request) {
        UUID branchId = resolveCurrentBranchId();
        String currentUserId = TenantContext.getUserId();

        StudentCallLog callLog = StudentCallLog.builder()
                .studentId(request.getStudentId())
                .callerStaffId(request.getCallerStaffId())
                .callDate(request.getCallDate())
                .callTime(request.getCallTime())
                .callResult(request.getCallResult())
                .notes(request.getNotes())
                .followUpRequired(request.getFollowUpRequired() != null ? request.getFollowUpRequired() : false)
                .followUpDate(request.getFollowUpDate())
                .branchId(branchId)
                .build();

            callLog.setCreatedBy(currentUserId);

        callLog = callLogRepository.save(callLog);
        log.info("Created call log for student {}", request.getStudentId());
        return toDto(callLog);
    }

    @Transactional
    public StudentCallLogDto updateCallLog(UUID id, SaveStudentCallLogRequest request) {
        UUID branchId = resolveCurrentBranchId();
        String currentUserId = TenantContext.getUserId();

        StudentCallLog callLog = findCallLogByIdInScope(id, branchId);

        // Требуют причину для изменения
        if (!StringUtils.hasText(request.getUpdateReason())) {
            throw new BusinessException(
                    "CALL_LOG_UPDATE_REASON_REQUIRED",
                    "Необходимо указать причину изменения записи обзвона",
                    HttpStatus.BAD_REQUEST
            );
        }

        callLog.setCallerStaffId(request.getCallerStaffId());
        callLog.setCallDate(request.getCallDate());
        callLog.setCallTime(request.getCallTime());
        callLog.setCallResult(request.getCallResult());
        callLog.setNotes(request.getNotes());
        callLog.setFollowUpRequired(request.getFollowUpRequired() != null ? request.getFollowUpRequired() : false);
        callLog.setFollowUpDate(request.getFollowUpDate());
        callLog.setUpdateReason(request.getUpdateReason());
        callLog.setUpdatedBy(currentUserId);

        callLog = callLogRepository.save(callLog);
        log.info("Updated call log {}", id);
        return toDto(callLog);
    }

    @Transactional
    public void deleteCallLog(UUID id, String reason) {
        UUID branchId = resolveCurrentBranchId();

        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(
                    "CALL_LOG_DELETE_REASON_REQUIRED",
                    "Необходимо указать причину удаления записи обзвона",
                    HttpStatus.BAD_REQUEST
            );
        }

        StudentCallLog callLog = findCallLogByIdInScope(id, branchId);
        callLogRepository.delete(callLog);
        log.info("Deleted call log {} (reason: {})", id, reason);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentCallLogDto> getCallLogsByStudent(UUID studentId, Pageable pageable) {
        UUID branchId = resolveCurrentBranchId();
        Page<StudentCallLog> page = callLogRepository.findByStudentId(studentId, branchId, pageable);
        return PageResponse.from(page, this::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentCallLogDto> getCallLogsByStudentAndDateRange(
            UUID studentId, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        UUID branchId = resolveCurrentBranchId();
        Page<StudentCallLog> page = callLogRepository.findByStudentIdAndDateRange(
                studentId, fromDate, toDate, branchId, pageable);
        return PageResponse.from(page, this::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentCallLogDto> getCallLogsByCaller(UUID staffId, Pageable pageable) {
        UUID branchId = resolveCurrentBranchId();
        Page<StudentCallLog> page = callLogRepository.findByCallerStaffId(staffId, branchId, pageable);
        return PageResponse.from(page, this::toDto);
    }

    private StudentCallLog findCallLogByIdInScope(UUID id, UUID branchId) {
        if (branchId != null) {
            return callLogRepository.findById(id)
                    .filter(log -> log.getBranchId() == null || log.getBranchId().equals(branchId))
                    .orElseThrow(() -> new ResourceNotFoundException("StudentCallLog", "id", id));
        }
        return callLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StudentCallLog", "id", id));
    }

    private StudentCallLogDto toDto(StudentCallLog callLog) {
        return StudentCallLogDto.builder()
                .id(callLog.getId())
                .studentId(callLog.getStudentId())
                .callerStaffId(callLog.getCallerStaffId())
                .callDate(callLog.getCallDate())
                .callTime(callLog.getCallTime())
                .callResult(callLog.getCallResult())
                .notes(callLog.getNotes())
                .followUpRequired(callLog.getFollowUpRequired())
                .followUpDate(callLog.getFollowUpDate())
                .createdBy(parseUuid(callLog.getCreatedBy()))
                .createdAt(callLog.getCreatedAt())
                .updatedBy(parseUuid(callLog.getUpdatedBy()))
                .updatedAt(callLog.getUpdatedAt())
                .updateReason(callLog.getUpdateReason())
                .build();
    }

    private UUID parseUuid(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private UUID resolveCurrentBranchId() {
        String rawBranchId = TenantContext.getBranchId();
        if (!StringUtils.hasText(rawBranchId)) {
            return null;
        }
        try {
            return UUID.fromString(rawBranchId.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("BRANCH_ID_INVALID", "Invalid branch_id in tenant context");
        }
    }
}
