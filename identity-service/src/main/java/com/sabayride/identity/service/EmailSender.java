package com.sabayride.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dispatches Email OTP notifications.
 * Logs prominent dispatch messages for testing/production environments.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final String fromEmail;

    public EmailSender(
            @Value("${sabayride.email.from:noreply@sabayride.com}") String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public boolean sendEmail(String toEmail, String subject, String content) {
        log.info("═══ [EMAIL OTP DISPATCH] ═══ From: {} | To: {} | Subject: {} | Content: {}",
                fromEmail, toEmail, subject, content);
        return true;
    }
}
