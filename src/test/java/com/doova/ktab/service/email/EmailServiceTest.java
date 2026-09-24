package com.doova.ktab.service.email;

import com.doova.ktab.config.mail.ZeptoMailProperties;
import com.doova.ktab.dto.mail.EmailAttachment;
import com.doova.ktab.dto.mail.EmailDeliveryStatus;
import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.dto.mail.EmailResult;
import com.doova.ktab.event.model.SendEmailEvent;
import com.doova.ktab.service.email.impl.EmailServiceImpl;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ZeptoMailProperties properties;
    private MeterRegistry meterRegistry;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        properties = new ZeptoMailProperties();
        properties.setEnabled(true);
        properties.setHost("smtp.zeptomail.com");
        properties.setPort(587);
        properties.setUsername("emailapikey");
        properties.setPassword("dummy-token");
        properties.setFromEmail("noreply@ktab.app");
        properties.setFromName("Ktab");

        meterRegistry = new SimpleMeterRegistry();

        emailService = new EmailServiceImpl(
                mailSender,
                templateEngine,
                properties,
                meterRegistry,
                eventPublisher
        );
    }

    private MimeMessage createRealMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    @DisplayName("sendSync: renders Thymeleaf template and dispatches MIME message")
    void sendSync_withThymeleafTemplate_rendersAndSendsHtmlMessage() {
        when(mailSender.createMimeMessage()).thenReturn(createRealMimeMessage());
        when(templateEngine.process(eq("verify-email"), any(Context.class)))
                .thenReturn("<html><body>Code: 123456</body></html>");

        EmailRequest request = EmailRequest.builder()
                .to("reader@example.com")
                .subject("Verify Your Account")
                .templateName("verify-email")
                .variable("CODE", "123456")
                .build();

        EmailResult result = emailService.sendSync(request);

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(result.recipientCount()).isEqualTo(1);

        verify(templateEngine).process(eq("verify-email"), any(Context.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendSync: sends raw HTML body without calling TemplateEngine")
    void sendSync_withRawHtml_sendsDirectHtmlMessage() {
        when(mailSender.createMimeMessage()).thenReturn(createRealMimeMessage());

        EmailRequest request = EmailRequest.builder()
                .to("reader@example.com")
                .subject("Direct HTML Notice")
                .htmlBody("<h1>Welcome to Ktab</h1>")
                .build();

        EmailResult result = emailService.sendSync(request);

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.SENT);

        verify(templateEngine, never()).process(anyString(), any(Context.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendSync: sends plain text body successfully")
    void sendSync_withPlainText_sendsTextMessage() {
        when(mailSender.createMimeMessage()).thenReturn(createRealMimeMessage());

        EmailRequest request = EmailRequest.builder()
                .to("reader@example.com")
                .subject("Plain Text Alert")
                .textBody("Hello, this is a plain text notification.")
                .build();

        EmailResult result = emailService.sendSync(request);

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.SENT);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendSync: includes multiple TO, CC, and BCC recipients")
    void sendSync_withMultipleRecipients_countsAllRecipients() {
        when(mailSender.createMimeMessage()).thenReturn(createRealMimeMessage());

        EmailRequest request = EmailRequest.builder()
                .to("to1@example.com", "to2@example.com")
                .cc("cc1@example.com")
                .bcc("bcc1@example.com", "bcc2@example.com")
                .subject("Multi-recipient Update")
                .textBody("Notice to all")
                .build();

        EmailResult result = emailService.sendSync(request);

        assertThat(result.success()).isTrue();
        assertThat(result.recipientCount()).isEqualTo(5);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendSync: handles file attachments properly")
    void sendSync_withAttachments_attachesDataCorrectly() {
        when(mailSender.createMimeMessage()).thenReturn(createRealMimeMessage());

        byte[] pdfBytes = "%PDF-1.4 dummy content".getBytes(StandardCharsets.UTF_8);
        EmailAttachment attachment = EmailAttachment.of("invoice.pdf", pdfBytes, "application/pdf");

        EmailRequest request = EmailRequest.builder()
                .to("finance@example.com")
                .subject("Invoice Attached")
                .textBody("Please find your invoice attached.")
                .attachment(attachment)
                .build();

        EmailResult result = emailService.sendSync(request);

        assertThat(result.success()).isTrue();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendSync: when mail is disabled, skips dispatch and returns SKIPPED status")
    void sendSync_whenDisabled_returnsSkippedResult() {
        properties.setEnabled(false);

        EmailRequest request = EmailRequest.builder()
                .to("reader@example.com")
                .subject("Ignored")
                .textBody("Should not send")
                .build();

        EmailResult result = emailService.sendSync(request);

        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.SKIPPED);
        assertThat(result.errorMessage()).contains("disabled");
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendSync: returns FAILED result when no recipients are provided")
    void sendSync_whenNoRecipients_returnsFailedResult() {
        EmailRequest request = EmailRequest.builder()
                .subject("No Recipient")
                .textBody("Hello")
                .build();

        EmailResult result = emailService.sendSync(request);

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(result.errorMessage()).contains("No recipients");
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendSync: catches MailSendException and returns FAILED result gracefully")
    void sendSync_onMailSendException_handlesFailureGracefully() {
        when(mailSender.createMimeMessage()).thenReturn(createRealMimeMessage());
        doThrow(new MailSendException("SMTP connection timeout")).when(mailSender).send(any(MimeMessage.class));

        EmailRequest request = EmailRequest.builder()
                .to("reader@example.com")
                .subject("Failure Test")
                .textBody("Content")
                .build();

        EmailResult result = emailService.sendSync(request);

        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(result.errorMessage()).contains("SMTP connection timeout");
    }

    @Test
    @DisplayName("sendAfterCommit: publishes SendEmailEvent to Spring ApplicationEventPublisher")
    void sendAfterCommit_publishesSendEmailEvent() {
        EmailRequest request = EmailRequest.builder()
                .to("reader@example.com")
                .subject("Transactional Email")
                .textBody("Dispatched after DB commit")
                .build();

        emailService.sendAfterCommit(request);

        ArgumentCaptor<SendEmailEvent> captor = ArgumentCaptor.forClass(SendEmailEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().request()).isEqualTo(request);
    }

    @Test
    @DisplayName("sendHtml: legacy method maps to dynamic engine successfully")
    void sendHtml_legacyMethod_callsNewEngineSuccessfully() {
        when(mailSender.createMimeMessage()).thenReturn(createRealMimeMessage());
        when(templateEngine.process(eq("reset-password"), any(Context.class)))
                .thenReturn("<html><body>Reset</body></html>");

        emailService.sendHtml("user@test.com", "Reset", "reset-password", Map.of("TOKEN", "abc"));

        verify(templateEngine).process(eq("reset-password"), any(Context.class));
        verify(mailSender).send(any(MimeMessage.class));
    }
}
