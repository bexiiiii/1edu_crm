package com.ondeedu.notification.service;

import com.ondeedu.common.event.AssignmentNotificationEvent;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    @Transactional
    public NotificationLog createInAppNotification(AssignmentNotificationEvent event) {
        return createInAppNotification(
                event.getTenantId(),
                event.getRecipientEmail(),
                event.getRecipientStaffId(),
                event.getRecipientName(),
                event.getSubject(),
                event.getBody(),
                event.getEventType(),
                event.getEntityType(),
                event.getEntityId()
        );
    }

    @Transactional
    public NotificationLog createInAppNotification(
            String tenantId,
            String recipientEmail,
            UUID recipientStaffId,
            String recipientName,
            String subject,
            String body,
            String eventType,
            String referenceType,
            UUID referenceId
    ) {
        NotificationLog log = NotificationLog.builder()
                .type(NotificationType.IN_APP)
                .tenantId(tenantId)
                .eventType(eventType)
                .recipientEmail(recipientEmail)
                .recipientStaffId(recipientStaffId)
                .recipientName(recipientName)
                .subject(subject)
                .body(body)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .status(NotificationStatus.SENT)
                .sentAt(Instant.now())
                .build();

        return notificationRepository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> listLogs(
            String tenantId,
            String recipientEmail,
            NotificationType type,
            NotificationStatus status,
            Pageable pageable
    ) {
        if (recipientEmail != null && !StringUtils.hasText(recipientEmail)) {
            return PageResponse.from(new PageImpl<>(java.util.List.of(), pageable, 0));
        }

        String normalizedTenantId = StringUtils.hasText(tenantId) ? tenantId : null;
        Page<NotificationLog> page;
        if (StringUtils.hasText(recipientEmail)) {
            page = notificationRepository.searchByRecipientEmail(
                    normalizedTenantId,
                    recipientEmail.trim().toLowerCase(),
                    type,
                    status,
                    pageable
            );
        } else {
            page = notificationRepository.search(
                    normalizedTenantId,
                    type,
                    status,
                    pageable
            );
        }
        return PageResponse.from(page, notificationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getLogsByTenantId(String tenantId, Pageable pageable) {
        Page<NotificationLog> page = notificationRepository.findByTenantId(tenantId, pageable);
        return PageResponse.from(page, notificationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public NotificationDto getById(UUID id, String tenantId, String recipientEmail) {
        NotificationLog log = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NotificationLog", "id", id));
        if (StringUtils.hasText(tenantId) && !tenantId.equals(log.getTenantId())) {
            throw new ResourceNotFoundException("NotificationLog", "id", id);
        }
        if (StringUtils.hasText(recipientEmail)) {
            if (!StringUtils.hasText(log.getRecipientEmail())
                    || !recipientEmail.equalsIgnoreCase(log.getRecipientEmail())) {
                throw new ResourceNotFoundException("NotificationLog", "id", id);
            }
        }
        return notificationMapper.toDto(log);
    }
}
