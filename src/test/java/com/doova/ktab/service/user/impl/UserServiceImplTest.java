package com.doova.ktab.service.user.impl;

import com.doova.ktab.dto.user.UserRegisterRequest;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.model.user.User;
import com.doova.ktab.model.user.UserCode;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.email.EmailService;
import com.doova.ktab.service.user.UserCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserCodeService userCodeService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserServiceImpl userService;

    private UserRegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new UserRegisterRequest(
                "John",
                null,
                "Doe",
                "john.doe@example.com",
                "Password123!",
                UserRole.READER.getCode()
        );
    }

    @Test
    @DisplayName("registerUser_validRequest_attachesDefaultUserSettingsAndSavesUser")
    void registerUser_validRequest_attachesDefaultUserSettingsAndSavesUser() {
        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        UserCode mockCode = UserCode.builder()
                .code("123456")
                .codeType("EMAIL_VERIFY")
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(userCodeService.createCode(any(User.class), eq("EMAIL_VERIFY"), eq(10))).thenReturn(mockCode);

        User registered = userService.register(validRequest);

        assertThat(registered).isNotNull();
        assertThat(registered.getEmail()).isEqualTo("john.doe@example.com");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User captured = userCaptor.getValue();
        // Verify UserSettings is instantiated, attached to user, and references user for cascade insert
        assertThat(captured.getSettings()).isNotNull();
        assertThat(captured.getSettings().getUser()).isEqualTo(captured);
        assertThat(captured.getSettings().getLanguage()).isEqualTo("en");
        assertThat(captured.getSettings().getNotificationsEmail()).isTrue();
        assertThat(captured.getSettings().getNotificationsInApp()).isTrue();
        assertThat(captured.getSettings().getPrivacyProfilePublic()).isFalse();

        verify(emailService).sendHtml(eq("john.doe@example.com"), anyString(), eq("verify-email"), anyMap());
    }

    @Test
    @DisplayName("registerUser_existingEmail_throwsBadRequestException")
    void registerUser_existingEmail_throwsBadRequestException() {
        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(validRequest))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("registerUser_forbiddenRole_throwsBadRequestException")
    void registerUser_forbiddenRole_throwsBadRequestException() {
        UserRegisterRequest adminRequest = new UserRegisterRequest(
                "Bad",
                null,
                "Actor",
                "hacker@example.com",
                "Password123!",
                UserRole.ADMIN.getCode()
        );

        when(userRepository.existsByEmail("hacker@example.com")).thenReturn(false);

        assertThatThrownBy(() -> userService.register(adminRequest))
                .isInstanceOf(BadRequestException.class);
    }
}
