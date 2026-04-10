package com.ondeedu.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${notification.mail.from:}")
    private String fromEmail;

    @Value("${notification.mail.reply-to:}")
    private String replyTo;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    public void sendEmail(String to, String subject, String body) {
        String sender = StringUtils.hasText(fromEmail) ? fromEmail : smtpUsername;
        if (!StringUtils.hasText(sender)) {
            throw new IllegalStateException("Mail sender address is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        if (StringUtils.hasText(replyTo)) {
            message.setReplyTo(replyTo);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.debug("Email sent successfully to: {}", to);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw e;
        }
    }
}
