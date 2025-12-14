package com.doova.ktab.security.filter;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FilterResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(FilterResponseWriter.class);

    public void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        // Standardized error response JSON structure
        String json = String.format("""
                {
                    "success": false,
                    "status": %d,
                    "error": "%s",
                    "message": "%s"
                }
                """, status.value(), status.getReasonPhrase(), message);

        try {
            response.getWriter().write(json);
            response.getWriter().flush();
        } catch (IOException e) {
            log.error("Failed to write error response to client: {}", e.getMessage());
            throw e;
        }
    }
}
