package com.doova.ktab.service.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatNoException;

class SendVerifyEmailLiveTest {

    @Test
    @Disabled("Manual live verification test against smtp.zeptomail.com")
    @DisplayName("Send live verification email using Thymeleaf verify-email.html template to info@doova.ai")
    void sendLiveVerificationEmail() {
        String host = System.getenv().getOrDefault("ZEPTOMAIL_HOST", "smtp.zeptomail.com");
        int port = Integer.parseInt(System.getenv().getOrDefault("ZEPTOMAIL_PORT", "587"));
        String username = System.getenv().getOrDefault("ZEPTOMAIL_USERNAME", "emailapikey");
        String password = System.getenv().getOrDefault("ZEPTOMAIL_PASSWORD", "wSsVR61wqxXwW/0pmWesceYxyFlSBFqnQBl/iwSh63P0SP3Losc4k0edDQHzH6RLRW89QGdBpbMqkRcG2mAIjo4tzlgJCCiF9mqRe1U4J3x17qnvhDzOXWlcmxqNL4oAwAhonWhlFc0n+g==");
        String from = System.getenv().getOrDefault("ZEPTOMAIL_FROM_EMAIL", "noreply@ktab.app");
        String to = "info@doova.ai";

        if (password.isBlank()) {
            return;
        }

        // 1. Setup SpringTemplateEngine (uses SpEL for Thymeleaf expressions)
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/emails/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        // 2. Render verify-email.html with sample data
        String testCode = String.format("%06d", new java.util.Random().nextInt(900000) + 100000);
        Context context = new Context();
        context.setVariable("NAME", "Doova Team");
        context.setVariable("CODE", testCode);
        String htmlBody = engine.process("verify-email", context);

        // 3. Configure JavaMailSender with ZeptoMail credentials
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.from", from);
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        // 4. Construct and dispatch MimeMessage
        assertThatNoException().isThrownBy(() -> {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "Ktab");
            helper.setTo(to);
            helper.setSubject("Ktab — Verify Your Account");
            helper.setText(htmlBody, true); // true = HTML

            java.io.File logoFile = new java.io.File("src/main/resources/static/images/logo.png");
            if (logoFile.exists()) {
                helper.addInline("ktabLogo", logoFile);
            }

            mailSender.send(message);
        });
    }
}
