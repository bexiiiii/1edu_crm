package com.ondeedu.notification.service;

import com.ondeedu.notification.entity.NotificationLog;
import com.ondeedu.notification.entity.NotificationStatus;
import com.ondeedu.notification.mapper.NotificationMapper;
import com.ondeedu.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        when(notificationRepository.save(any(NotificationLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldMarkNotificationAsSentWhenEmailIsDelivered() {
        NotificationLog result = notificationService.sendEmailNotification(
                "tenant-1",
                "student.created",
                "student@example.com",
                "Subject",
                "Body"
        );

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
        assertThat(result.getErrorMessage()).isNull();
        verify(emailService).sendEmail("student@example.com", "Subject", "Body");
    }

    @Test
    void shouldMarkNotificationAsFailedWhenEmailSendingThrows() {
        doThrow(new MailSendException("smtp failed"))
                .when(emailService)
                .sendEmail("student@example.com", "Subject", "Body");

        NotificationLog result = notificationService.sendEmailNotification(
                "tenant-1",
                "student.created",
                "student@example.com",
                "Subject",
                "Body"
        );

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getSentAt()).isNull();
        assertThat(result.getErrorMessage()).contains("smtp failed");
    }

    @Test
    void shouldMarkNotificationAsFailedWhenRecipientEmailIsBlank() {
        NotificationLog result = notificationService.sendEmailNotification(
                "tenant-1",
                "student.created",
                "   ",
                "Subject",
                "Body"
        );

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("Recipient email is blank");
        verifyNoInteractions(emailService);
    }
}
