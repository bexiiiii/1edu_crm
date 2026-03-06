package com.ondeedu.notification.listener;

import com.ondeedu.common.config.RabbitMQConfig;
import com.ondeedu.common.event.PaymentCompletedEvent;
import com.ondeedu.common.event.StudentCreatedEvent;
import com.ondeedu.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.STUDENT_CREATED_QUEUE)
    public void onStudentCreated(StudentCreatedEvent event) {
        if (event.getEmail() == null) return;
        notificationService.sendEmailNotification(
                event.getTenantId(),
                "student.created",
                event.getEmail(),
                "Добро пожаловать в образовательный центр!",
                "Уважаемый " + event.getFirstName() + " " + event.getLastName() + ", ваш аккаунт создан."
        );
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_COMPLETED_QUEUE)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        // Log receipt notification
        log.info("Payment completed for tenant: {}", event.getTenantId());
    }
}
