package com.ondeedu.notification.repository;

import com.ondeedu.notification.entity.NotificationLog;
import com.ondeedu.notification.entity.NotificationStatus;
import com.ondeedu.notification.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByStatus(NotificationStatus status, Pageable pageable);

    Page<NotificationLog> findByTenantId(String tenantId, Pageable pageable);

    Page<NotificationLog> findByType(NotificationType type, Pageable pageable);

    Page<NotificationLog> findByTypeAndStatus(NotificationType type, NotificationStatus status, Pageable pageable);

    Page<NotificationLog> findByTenantIdAndStatus(String tenantId, NotificationStatus status, Pageable pageable);

    Page<NotificationLog> findByTenantIdAndType(String tenantId, NotificationType type, Pageable pageable);

    Page<NotificationLog> findByTenantIdAndTypeAndStatus(
            String tenantId,
            NotificationType type,
            NotificationStatus status,
            Pageable pageable
    );
}
