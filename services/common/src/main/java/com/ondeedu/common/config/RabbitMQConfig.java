package com.ondeedu.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(ConnectionFactory.class)
public class RabbitMQConfig {

    // Exchanges
    public static final String STUDENT_EXCHANGE = "student.exchange";
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String LEAD_EXCHANGE = "lead.exchange";
    public static final String AUDIT_EXCHANGE = "audit.exchange";

    // Audit queues & routing keys
    public static final String AUDIT_SYSTEM_QUEUE = "audit.system.queue";
    public static final String AUDIT_TENANT_QUEUE = "audit.tenant.queue";
    public static final String AUDIT_SYSTEM_KEY   = "audit.system";
    public static final String AUDIT_TENANT_KEY   = "audit.tenant";

    // Queues
    public static final String STUDENT_CREATED_QUEUE = "student.created.queue";
    public static final String STUDENT_UPDATED_QUEUE = "student.updated.queue";
    public static final String PAYMENT_COMPLETED_QUEUE = "payment.completed.queue";
    public static final String NOTIFICATION_EMAIL_QUEUE = "notification.email.queue";
    public static final String NOTIFICATION_SMS_QUEUE = "notification.sms.queue";
    public static final String NOTIFICATION_ASSIGNMENT_QUEUE = "notification.assignment.queue";
    public static final String LEAD_CREATED_QUEUE = "lead.created.queue";
    public static final String LEAD_CONVERTED_QUEUE = "lead.converted.queue";

    // Routing keys
    public static final String STUDENT_CREATED_KEY = "student.created";
    public static final String STUDENT_UPDATED_KEY = "student.updated";
    public static final String PAYMENT_COMPLETED_KEY = "payment.completed";
    public static final String NOTIFICATION_EMAIL_KEY = "notification.email";
    public static final String NOTIFICATION_SMS_KEY = "notification.sms";
    public static final String NOTIFICATION_ASSIGNMENT_KEY = "notification.assignment";
    public static final String LEAD_CREATED_KEY = "lead.created";
    public static final String LEAD_CONVERTED_KEY = "lead.converted";

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    // Student Exchange
    @Bean
    public TopicExchange studentExchange() {
        return new TopicExchange(STUDENT_EXCHANGE);
    }

    @Bean
    public Queue studentCreatedQueue() {
        return QueueBuilder.durable(STUDENT_CREATED_QUEUE).build();
    }

    @Bean
    public Binding studentCreatedBinding(Queue studentCreatedQueue, TopicExchange studentExchange) {
        return BindingBuilder.bind(studentCreatedQueue).to(studentExchange).with(STUDENT_CREATED_KEY);
    }

    // Payment Exchange
    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE);
    }

    @Bean
    public Queue paymentCompletedQueue() {
        return QueueBuilder.durable(PAYMENT_COMPLETED_QUEUE).build();
    }

    @Bean
    public Binding paymentCompletedBinding(Queue paymentCompletedQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentCompletedQueue).to(paymentExchange).with(PAYMENT_COMPLETED_KEY);
    }

    // Notification Exchange
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Queue notificationEmailQueue() {
        return QueueBuilder.durable(NOTIFICATION_EMAIL_QUEUE).build();
    }

    @Bean
    public Queue notificationSmsQueue() {
        return QueueBuilder.durable(NOTIFICATION_SMS_QUEUE).build();
    }

    @Bean
    public Queue notificationAssignmentQueue() {
        return QueueBuilder.durable(NOTIFICATION_ASSIGNMENT_QUEUE).build();
    }

    @Bean
    public Binding notificationEmailBinding(Queue notificationEmailQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationEmailQueue).to(notificationExchange).with(NOTIFICATION_EMAIL_KEY);
    }

    @Bean
    public Binding notificationSmsBinding(Queue notificationSmsQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationSmsQueue).to(notificationExchange).with(NOTIFICATION_SMS_KEY);
    }

    @Bean
    public Binding notificationAssignmentBinding(Queue notificationAssignmentQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationAssignmentQueue)
                .to(notificationExchange)
                .with(NOTIFICATION_ASSIGNMENT_KEY);
    }

    // Lead Exchange
    @Bean
    public TopicExchange leadExchange() {
        return new TopicExchange(LEAD_EXCHANGE);
    }

    @Bean
    public Queue leadCreatedQueue() {
        return QueueBuilder.durable(LEAD_CREATED_QUEUE).build();
    }

    @Bean
    public Binding leadCreatedBinding(Queue leadCreatedQueue, TopicExchange leadExchange) {
        return BindingBuilder.bind(leadCreatedQueue).to(leadExchange).with(LEAD_CREATED_KEY);
    }

    // Audit Exchange
    @Bean
    public TopicExchange auditExchange() {
        return new TopicExchange(AUDIT_EXCHANGE);
    }

    @Bean
    public Queue auditSystemQueue() {
        return QueueBuilder.durable(AUDIT_SYSTEM_QUEUE).build();
    }

    @Bean
    public Queue auditTenantQueue() {
        return QueueBuilder.durable(AUDIT_TENANT_QUEUE).build();
    }

    @Bean
    public Binding auditSystemBinding(Queue auditSystemQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditSystemQueue).to(auditExchange).with(AUDIT_SYSTEM_KEY);
    }

    @Bean
    public Binding auditTenantBinding(Queue auditTenantQueue, TopicExchange auditExchange) {
        return BindingBuilder.bind(auditTenantQueue).to(auditExchange).with(AUDIT_TENANT_KEY);
    }
}
