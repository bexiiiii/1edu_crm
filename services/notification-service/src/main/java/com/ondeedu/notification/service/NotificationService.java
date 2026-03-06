package com.ondeedu.notification.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.notification.dto.NotificationDto;
import com.ondeedu.notification.entity.NotificationLog;
import com.ondeedu.notification.entity.NotificationStatus;
import com.ondeedu.notification.entity.NotificationType;
import com.ondeedu.notification.mapper.NotificationMapper;
import com.ondeedu.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final EmailService emailService;

    @Transactional
    public NotificationLog sendEmailNotification(String tenantId, String eventType,
                                                  String recipientEmail, String subject, String body) {
        NotificationLog log = NotificationLog.builder()
                .type(NotificationType.EMAIL)
                .tenantId(tenantId)
                .eventType(eventType)
                .recipientEmail(recipientEmail)
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .build();

        log = notificationRepository.save(log);

        try {
            emailService.sendEmail(recipientEmail, subject, body);
            log.setStatus(NotificationStatus.SENT);
            log.setSentAt(Instant.now());
        } catch (Exception e) {
            NotificationService.log.error("Failed to send email notification for event '{}': {}", eventType, e.getMessage());
            log.setStatus(NotificationStatus.FAILED);
            log.setErrorMessage(e.getMessage());
        }

        return notificationRepository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> listLogs(NotificationType type, NotificationStatus status, Pageable pageable) {
        Page<NotificationLog> page;
        if (type != null && status != null) {
            page = notificationRepository.findAll(pageable);
        } else if (type != null) {
            page = notificationRepository.findByType(type, pageable);
        } else if (status != null) {
            page = notificationRepository.findByStatus(status, pageable);
        } else {
            page = notificationRepository.findAll(pageable);
        }
        return PageResponse.from(page, notificationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getLogsByTenantId(String tenantId, Pageable pageable) {
        Page<NotificationLog> page = notificationRepository.findByTenantId(tenantId, pageable);
        return PageResponse.from(page, notificationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public NotificationDto getById(UUID id) {
        NotificationLog log = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NotificationLog", "id", id));
        return notificationMapper.toDto(log);
    }
}
