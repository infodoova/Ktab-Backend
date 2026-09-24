package com.doova.ktab.service.email.impl;

import com.doova.ktab.config.mail.ZeptoMailProperties;
import com.doova.ktab.dto.mail.EmailAttachment;
import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.dto.mail.EmailResult;
import com.doova.ktab.event.model.SendEmailEvent;
import com.doova.ktab.service.email.EmailService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final ZeptoMailProperties properties;
    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Async("mailTaskExecutor")
    public CompletableFuture<EmailResult> sendAsync(EmailRequest request) {
        EmailResult result = sendSync(request);
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public EmailResult sendSync(EmailRequest request) {
        if (!properties.isEnabled()) {
            log.warn("Email service is disabled. Skipping dispatch for subject: '{}'", request.subject());
            return EmailResult.skipped("Email service is disabled");
        }

        if (request.to() == null || request.to().isEmpty()) {
            log.warn("Email dispatch rejected: no recipients provided for subject: '{}'", request.subject());
            return EmailResult.failed("No recipients specified", 0);
        }

        int totalRecipients = request.to().size() + request.cc().size() + request.bcc().size();
        String templateTag = request.templateName() != null ? request.templateName() : "raw";
        Timer.Sample sample = Timer.start(meterRegistry);

        log.debug("Initiating email send: subject='{}', primaryRecipient='{}', totalRecipients={}",
                request.subject(), maskEmail(request.to().get(0)), totalRecipients);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true flag enables multipart support (HTML content + attachments)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // 1. From / Sender Profile
            String fromEmail = (request.fromEmail() != null && !request.fromEmail().isBlank())
                    ? request.fromEmail()
                    : properties.getFromEmail();

            String fromName = (request.fromName() != null && !request.fromName().isBlank())
                    ? request.fromName()
                    : properties.getFromName();

            try {
                helper.setFrom(fromEmail, fromName);
            } catch (UnsupportedEncodingException e) {
                helper.setFrom(fromEmail);
            }

            // 2. Recipients
            for (String to : request.to()) {
                helper.addTo(to);
            }
            for (String cc : request.cc()) {
                helper.addCc(cc);
            }
            for (String bcc : request.bcc()) {
                helper.addBcc(bcc);
            }

            // 3. Reply-To
            if (request.replyTo() != null && !request.replyTo().isBlank()) {
                helper.setReplyTo(request.replyTo());
            } else if (properties.getReplyTo() != null && !properties.getReplyTo().isBlank()) {
                helper.setReplyTo(properties.getReplyTo());
            }

            // 4. Subject
            helper.setSubject(request.subject() != null ? request.subject() : "");

            // 5. Body resolution (Thymeleaf template > HTML body > Plain text)
            String resolvedHtml = null;
            if (request.templateName() != null && !request.templateName().isBlank()) {
                Context context = new Context();
                context.setVariables(request.templateVariables());
                resolvedHtml = templateEngine.process(request.templateName(), context);
                helper.setText(resolvedHtml, true);
            } else if (request.htmlBody() != null && !request.htmlBody().isBlank()) {
                resolvedHtml = request.htmlBody();
                helper.setText(resolvedHtml, true);
            } else if (request.textBody() != null) {
                helper.setText(request.textBody(), false);
            } else {
                helper.setText("", false);
            }

            // Automatic CID Logo embedding if referenced in HTML
            if (resolvedHtml != null && resolvedHtml.contains("cid:ktabLogo")) {
                org.springframework.core.io.ClassPathResource logoResource =
                        new org.springframework.core.io.ClassPathResource("static/images/logo.png");
                if (logoResource.exists()) {
                    helper.addInline("ktabLogo", logoResource, "image/png");
                }
            }

            // 6. Attachments
            if (request.attachments() != null && !request.attachments().isEmpty()) {
                for (EmailAttachment attachment : request.attachments()) {
                    ByteArrayResource resource = new ByteArrayResource(attachment.data());
                    if (attachment.inline() && attachment.contentId() != null) {
                        helper.addInline(attachment.contentId(), resource, attachment.contentType());
                    } else {
                        helper.addAttachment(attachment.filename(), resource, attachment.contentType());
                    }
                }
            }

            // 7. Custom Headers
            message.addHeader("X-Mailer", "Ktab-ZeptoMail");
            if (request.headers() != null) {
                for (Map.Entry<String, String> entry : request.headers().entrySet()) {
                    message.addHeader(entry.getKey(), entry.getValue());
                }
            }

            // 8. Transport Dispatch
            mailSender.send(message);

            String messageId = message.getMessageID();
            log.info("Email delivered successfully via ZeptoMail: subject='{}', primaryRecipient='{}', messageId='{}'",
                    request.subject(), maskEmail(request.to().get(0)), messageId);

            meterRegistry.counter("mail.sent.total", "status", "success", "template", templateTag).increment();
            sample.stop(meterRegistry.timer("mail.send.duration", "status", "success", "template", templateTag));

            return EmailResult.success(messageId, totalRecipients);

        } catch (MessagingException e) {
            log.error("SMTP delivery failure via ZeptoMail for subject='{}', recipient='{}': {}",
                    request.subject(), maskEmail(request.to().get(0)), e.getMessage(), e);

            meterRegistry.counter("mail.sent.total", "status", "failure", "template", templateTag).increment();
            sample.stop(meterRegistry.timer("mail.send.duration", "status", "failure", "template", templateTag));

            return EmailResult.failed(e.getMessage(), totalRecipients);
        } catch (Exception e) {
            log.error("Unexpected error during email dispatch for subject='{}': {}",
                    request.subject(), e.getMessage(), e);

            meterRegistry.counter("mail.sent.total", "status", "error", "template", templateTag).increment();
            sample.stop(meterRegistry.timer("mail.send.duration", "status", "error", "template", templateTag));

            return EmailResult.failed(e.getMessage(), totalRecipients);
        }
    }

    @Override
    public void sendAfterCommit(EmailRequest request) {
        log.debug("Enqueuing email for dispatch after transaction commit: subject='{}'", request.subject());
        eventPublisher.publishEvent(new SendEmailEvent(request));
    }

    // =========================================================================
    // Legacy / Backward-Compatible Implementations
    // =========================================================================

    @Override
    public void sendHtml(String to, String subject, String templateName, Map<String, Object> variables) {
        EmailRequest request = EmailRequest.builder()
                .to(to)
                .subject(subject)
                .templateName(templateName)
                .templateVariables(variables)
                .build();
        send(request);
    }

    @Override
    public void sendText(String to, String subject, String body) {
        EmailRequest request = EmailRequest.builder()
                .to(to)
                .subject(subject)
                .textBody(body)
                .build();
        send(request);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 2) {
            return local.charAt(0) + "***@" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + domain;
    }
}
