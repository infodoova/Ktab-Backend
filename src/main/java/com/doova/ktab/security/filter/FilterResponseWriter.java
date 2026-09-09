package com.doova.ktab.security.filter;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.MessageStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FilterResponseWriter {

    private final MessageSource messageSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void writeError(
            HttpServletResponse response,
            HttpStatus status,
            ApiMessageKey messageKey
    ) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        String localizedMessage = messageKey.getMessage(messageSource);

        Map<String, Object> body = Map.of(
                "success", false,
                "status", status.value(),
                "messageStatus", MessageStatus.ERROR,
                "message", localizedMessage
        );

        try {
            objectMapper.writeValue(response.getWriter(), body);
        } catch (IOException ex) {
            log.error("Failed to write security error response", ex);
            throw ex;
        }
    }
}
