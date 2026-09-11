package com.doova.ktab.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @Test
    @DisplayName("doFilterInternal generates UUID when no correlation ID header is present")
    void doFilterInternal_withoutHeader_generatesCorrelationId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/books");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedMdc = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilterInternal(request, response, chain);

        String responseHeader = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseHeader).isNotNull().isNotBlank();
        assertThat(capturedMdc.get()).isEqualTo(responseHeader);

        // MDC must be cleaned up after filter execution
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("doFilterInternal preserves existing X-Correlation-Id header from client")
    void doFilterInternal_withExistingHeader_preservesCorrelationId() throws ServletException, IOException {
        String existingId = "client-trace-12345";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedMdc = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(existingId);
        assertThat(capturedMdc.get()).isEqualTo(existingId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
