package com.doova.ktab.controller.v1.auth;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.user.UserLoginRequest;
import com.doova.ktab.dto.user.UserResponseDto;
import com.doova.ktab.model.user.RefreshToken;
import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.GoogleOAuth2Service;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.auth.RefreshTokenService;
import com.doova.ktab.service.user.UserService;
import com.doova.ktab.utils.web.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService service;

    @Mock
    private MessageSource messageSource;

    @Mock
    private CookieUtils cookieUtils;

    @Mock
    private GoogleOAuth2Service googleOAuth2Service;

    @Mock
    private JWTService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private AuthController authController;

    private User testUser;
    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("ali@darhashem.com")
                .firstName("Ali")
                .lastName("Hashem")
                .role("ADMIN_LIBRARIAN")
                .build();
        testUser.setId(1L);

        testPrincipal = new UserPrincipal(testUser);
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("login_validCredentials_setsAccessAndRefreshTokenCookiesAndReturnsUser")
    void login_validCredentials_setsAccessAndRefreshTokenCookiesAndReturnsUser() {
        // Arrange
        UserLoginRequest loginReq = new UserLoginRequest("ali@darhashem.com", "Password123!");
        when(service.authenticate(loginReq)).thenReturn(testPrincipal);
        when(jwtService.generateToken(testPrincipal)).thenReturn("mock-access-token");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");

        RefreshToken mockRefreshToken = RefreshToken.builder()
                .token("mock-refresh-token")
                .user(testUser)
                .build();
        when(refreshTokenService.createRefreshToken(testUser, "Mozilla/5.0")).thenReturn(mockRefreshToken);

        // Act
        ResponseEntity<ApiResponse<UserResponseDto>> result = authController.login(loginReq, request, response);

        // Assert
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        UserResponseDto data = result.getBody().getData();
        assertNotNull(data);
        assertEquals("ali@darhashem.com", data.email());

        verify(cookieUtils).setAccessTokenCookie(response, "mock-access-token");
        verify(cookieUtils).setRefreshTokenCookie(response, "mock-refresh-token");
        verify(refreshTokenService).createRefreshToken(testUser, "Mozilla/5.0");
    }
}
