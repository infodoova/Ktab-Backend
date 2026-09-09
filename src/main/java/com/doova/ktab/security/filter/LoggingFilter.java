package com.doova.ktab.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Component
public class LoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "x-refresh-token", "proxy-authorization"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        logger.info("Incoming request: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());

        // Log headers at DEBUG level only, redacting sensitive tokens
        if (logger.isDebugEnabled()) {
            httpRequest.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                String value = SENSITIVE_HEADERS.contains(headerName.toLowerCase())
                        ? "[REDACTED]"
                        : httpRequest.getHeader(headerName);
                logger.debug("Request Header: {} = {}", headerName, value);
            });
        }

        chain.doFilter(request, response);

        logger.info("Outgoing response: {} {}", httpResponse.getStatus(), httpRequest.getRequestURI());
    }

    @Override
    public void destroy() {
        // Cleanup logic if necessary
    }
}
