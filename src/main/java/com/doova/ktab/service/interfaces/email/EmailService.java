package com.doova.ktab.service.interfaces.email;


import java.util.Map;

public interface EmailService {
    void sendHtml(String to, String subject, String templateName, Map<String, Object> variables);

    void sendText(String to, String subject, String body);
}