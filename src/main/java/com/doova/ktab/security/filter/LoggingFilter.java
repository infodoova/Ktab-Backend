package com.doova.ktab.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Log the incoming request
        logger.info("Incoming request: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());

        // Optionally log request headers
        httpRequest.getHeaderNames().asIterator().forEachRemaining(headerName -> logger.info("Request Header: {} = {}", headerName, httpRequest.getHeader(headerName)));

        // Proceed with the request
        chain.doFilter(request, response);

        // Log the outgoing response
        logger.info("Outgoing response: {} {}", httpResponse.getStatus(), httpRequest.getRequestURI());
    }


    @Override
    public void destroy() {
        // Cleanup logic if necessary
    }
}
