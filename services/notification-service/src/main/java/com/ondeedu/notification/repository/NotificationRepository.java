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
}
