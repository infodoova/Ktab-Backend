package com.doova.ktab.security.filter;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.ratelimit.RateLimitTier;
import com.doova.ktab.service.ratelimit.RateLimitResult;
import com.doova.ktab.service.ratelimit.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private FilterResponseWriter responseWriter;

    @Mock
    private FilterChain filterChain;

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(rateLimitService, responseWriter);
    }

    @Test
    @DisplayName("doFilterInternal permits allowed requests and sets quota headers")
    void doFilterInternal_whenAllowed_setsHeadersAndContinues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/books");
        request.setServletPath("/api/v1/books");
        request.setRemoteAddr("203.0.113.195");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.isEnabled()).thenReturn(true);
        when(rateLimitService.tryConsume(eq("203.0.113.195"), eq(RateLimitTier.GENERAL)))
                .thenReturn(RateLimitResult.permitted(100L, 99L));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(responseWriter);
        assertThat(response.getHeader(RateLimitingFilter.HEADER_LIMIT)).isEqualTo("100");
        assertThat(response.getHeader(RateLimitingFilter.HEADER_REMAINING)).isEqualTo("99");
    }

    @Test
    @DisplayName("doFilterInternal blocks throttled requests with 429 error and Retry-After header")
    void doFilterInternal_whenThrottled_setsRetryAfterAndWritesError() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        request.setRemoteAddr("198.51.100.4");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimitService.isEnabled()).thenReturn(true);
        when(rateLimitService.tryConsume(eq("198.51.100.4"), eq(RateLimitTier.AUTH)))
                .thenReturn(RateLimitResult.rejected(10L, 45L));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(responseWriter).writeError(response, HttpStatus.TOO_MANY_REQUESTS, ApiMessageKey.RATE_LIMIT_EXCEEDED);
        assertThat(response.getHeader(RateLimitingFilter.HEADER_LIMIT)).isEqualTo("10");
        assertThat(response.getHeader(RateLimitingFilter.HEADER_REMAINING)).isEqualTo("0");
        assertThat(response.getHeader(RateLimitingFilter.HEADER_RETRY_AFTER)).isEqualTo("45");
        assertThat(response.getHeader(RateLimitingFilter.HEADER_RESET)).isEqualTo("45");
    }

    @Test
    @DisplayName("doFilterInternal skips rate limiting on actuator health endpoints")
    void doFilterInternal_whenActuatorPath_skipsRateLimiting() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setServletPath("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimitService);
        verifyNoInteractions(responseWriter);
    }
}
