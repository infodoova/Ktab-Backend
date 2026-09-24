package com.doova.ktab.service.email;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatNoException;

class LiveZeptoMailTest {

    @Test
    @Disabled("Manual live SMTP verification test against smtp.zeptomail.com")
    @DisplayName("Verify real connection and email dispatch to Zoho ZeptoMail SMTP")
    void testLiveZeptoMailDispatch() {
        String host = System.getenv().getOrDefault("ZEPTOMAIL_HOST", "smtp.zeptomail.com");
        int port = Integer.parseInt(System.getenv().getOrDefault("ZEPTOMAIL_PORT", "587"));
        String username = System.getenv().getOrDefault("ZEPTOMAIL_USERNAME", "emailapikey");
        String password = System.getenv().getOrDefault("ZEPTOMAIL_PASSWORD", "");
        String from = System.getenv().getOrDefault("ZEPTOMAIL_FROM_EMAIL", "noreply@ktab.app");
        String to = "info@doova.ai";

        if (password.isBlank()) {
            return;
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.from", from);
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        Session session = Session.getInstance(props);

        assertThatNoException().isThrownBy(() -> {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from, "Ktab"));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject("Ktab - Zoho ZeptoMail Integration Verified");
            message.setText("Congratulations! Your Zoho ZeptoMail integration with Ktab-Backend is active and verified.");

            try (Transport transport = session.getTransport("smtp")) {
                transport.connect(host, port, username, password);
                transport.sendMessage(message, message.getAllRecipients());
            }
        });
    }
}
