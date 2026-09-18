package com.doova.ktab.security.filter;

import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.auth.MyUserDetailsService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JWTService jwtService;

    @Mock
    private MyUserDetailsService userDetailsService;

    @Mock
    private FilterResponseWriter responseWriter;

    @Mock
    private FilterChain filterChain;

    private JwtFilter jwtFilter;

    @BeforeEach
    void setUp() {
        jwtFilter = new JwtFilter(jwtService, userDetailsService, responseWriter);
    }

    @Test
    @DisplayName("shouldNotFilter returns true for standard and snapshot websocket paths")
    void shouldNotFilter_whenWebSocketPaths_returnsTrue() {
        for (String path : List.of("/ws/reader/tts", "/Ktab-0.0.1-SNAPSHOT/ws/reader/tts")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);

            boolean result = jwtFilter.shouldNotFilter(request);

            assertThat(result).isTrue();
        }
    }

    @Test
    @DisplayName("shouldNotFilter returns true for public auth paths")
    void shouldNotFilter_whenAuthPaths_returnsTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");

        boolean result = jwtFilter.shouldNotFilter(request);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("shouldNotFilter returns false for protected api paths")
    void shouldNotFilter_whenProtectedApiPath_returnsFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/books/1");
        request.setServletPath("/api/v1/books/1");

        boolean result = jwtFilter.shouldNotFilter(request);

        assertThat(result).isFalse();
    }
}
