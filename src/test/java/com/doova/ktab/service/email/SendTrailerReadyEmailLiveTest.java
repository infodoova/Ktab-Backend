package com.doova.ktab.service.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatNoException;

class SendTrailerReadyEmailLiveTest {

    @Test
    @Disabled("Manual live verification test against smtp.zeptomail.com")
    @DisplayName("Send live trailer-ready email using Thymeleaf trailer-ready.html template to fneishussein@gmail.com")
    void sendLiveTrailerReadyEmail() throws Exception {
        String host = System.getenv().getOrDefault("ZEPTOMAIL_HOST", "smtp.zeptomail.com");
        int port = Integer.parseInt(System.getenv().getOrDefault("ZEPTOMAIL_PORT", "587"));
        String username = System.getenv().getOrDefault("ZEPTOMAIL_USERNAME", "emailapikey");
        String password = System.getenv().getOrDefault("ZEPTOMAIL_PASSWORD", "");
        String from = System.getenv().getOrDefault("ZEPTOMAIL_FROM_EMAIL", "noreply@ktab.app");
        String to = "fneishussein@gmail.com";

        if (password.isBlank()) {
            Path envPath = Path.of(".env");
            if (Files.exists(envPath)) {
                for (String line : Files.readAllLines(envPath)) {
                    if (line.startsWith("ZEPTOMAIL_PASSWORD=")) {
                        password = line.substring("ZEPTOMAIL_PASSWORD=".length()).trim();
                        break;
                    }
                }
            }
        }

        if (password.isBlank()) {
            throw new IllegalStateException("ZEPTOMAIL_PASSWORD could not be found in environment or .env file");
        }

        // 1. Setup SpringTemplateEngine
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/emails/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        // 2. Render trailer-ready.html
        String bookTitle = "سر المكتبة القديمة";
        String trailerUrl = "https://ktab-rho.vercel.app/books/2/trailer";
        String subject = "إعلانك الترويجي السينمائي جاهز! - " + bookTitle;

        Context context = new Context();
        context.setVariable("NAME", "حسين");
        context.setVariable("BOOK_TITLE", bookTitle);
        context.setVariable("STATUS", "READY");
        context.setVariable("TRAILER_URL", trailerUrl);
        context.setVariable("SUBJECT", subject);

        String htmlBody = engine.process("trailer-ready", context);

        // 3. Configure JavaMailSender
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
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");

        // 4. Send Message with inline Logo
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from, "Ktab");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        ClassPathResource logoResource = new ClassPathResource("static/images/logo.png");
        if (logoResource.exists()) {
            helper.addInline("ktabLogo", logoResource, "image/png");
        }

        System.out.println("Dispatching live email to " + to + " via " + host + ":" + port + "...");
        mailSender.send(message);
        System.out.println("Email successfully sent to " + to + "!");
    }
}
