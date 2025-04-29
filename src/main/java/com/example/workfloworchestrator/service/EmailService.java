package com.example.workfloworchestrator.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Service for sending email notifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${workflow.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${workflow.email.from:workflow-orchestrator@example.com}")
    private String fromAddress;

    @Value("${workflow.email.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Send a simple text email
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param body Email body
     * @throws MessagingException If there is an error sending the email
     */
    @Async
    public void sendEmail(String to, String subject, String body) throws MessagingException {
        if (!emailEnabled) {
            log.info("Email is disabled. Would have sent email to {}: {}", to, subject);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.debug("Sent email to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new MessagingException("Failed to send email: " + e.getMessage(), e);
        }
    }

    /**
     * Send an HTML email using a template
     *
     * @param to Recipient email address
     * @param subject Email subject
     * @param templateName Name of the Thymeleaf template
     * @param variables Variables to be used in the template
     * @throws MessagingException If there is an error sending the email
     */
    @Async
    public void sendTemplateEmail(String to, String subject, String templateName,
                                  Map<String, Object> variables) throws MessagingException {
        if (!emailEnabled) {
            log.info("Email is disabled. Would have sent template email to {}: {}", to, subject);
            return;
        }

        try {
            // Add base URL to variables for template use
            variables.put("baseUrl", baseUrl);

            // Process the template
            Context context = new Context();
            variables.forEach(context::setVariable);
            String htmlContent = templateEngine.process(templateName, context);

            // Create message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true indicates HTML content

            mailSender.send(message);
            log.debug("Sent template email to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send template email to {}: {}", to, e.getMessage());
            throw new MessagingException("Failed to send template email: " + e.getMessage(), e);
        }
    }

    /**
     * Send email to multiple recipients
     *
     * @param recipients List of recipient email addresses
     * @param subject Email subject
     * @param body Email body
     */
    @Async
    public void sendBulkEmail(Iterable<String> recipients, String subject, String body) {
        if (!emailEnabled) {
            log.info("Email is disabled. Would have sent bulk email: {}", subject);
            return;
        }

        for (String recipient : recipients) {
            try {
                sendEmail(recipient, subject, body);
            } catch (MessagingException e) {
                // Log error but continue with other recipients
                log.error("Failed to send email to {} in bulk send: {}", recipient, e.getMessage());
            }
        }
    }

    /**
     * Send a notification email for a review task
     *
     * @param to Recipient email address
     * @param workflowName Name of the workflow
     * @param taskName Name of the task
     * @param reviewUrl URL for the review page
     * @throws MessagingException If there is an error sending the email
     */
    @Async
    public void sendReviewNotification(String to, String workflowName, String taskName,
                                       String reviewUrl) throws MessagingException {
        Map<String, Object> variables = Map.of(
                "workflowName", workflowName,
                "taskName", taskName,
                "reviewUrl", reviewUrl
        );

        sendTemplateEmail(
                to,
                "Review Required: " + taskName + " [" + workflowName + "]",
                "review-notification",
                variables
        );
    }
}
