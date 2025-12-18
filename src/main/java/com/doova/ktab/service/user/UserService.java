package com.doova.ktab.service.user;

import com.doova.ktab.api.dto.request.*;
import com.doova.ktab.enums.ErrorMessage;
import com.doova.ktab.enums.Status;
import com.doova.ktab.exceptions.BadRequestException;
import com.doova.ktab.model.user.User;
import com.doova.ktab.model.user.UserCode;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.interfaces.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final JWTService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserCodeService userCodeService;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final PasswordEncoder encoder;
    private final EmailService emailService;

    // ============================
    // REGISTER NEW USER
    // ============================
    @Transactional
    public User register(UserRegisterRequest request) {

        String email = request.email();
        log.debug("Register attempt for email={}", email);

        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email cannot be empty.");
        }

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException(ErrorMessage.USERID_ALREADY_EXISTS.getMessage(messageSource));
        }

        // Build user cleanly
        User user = User.builder().email(email).firstName(request.firstName()).middleName(request.middleName()).lastName(request.lastName()).role(request.role()).active(Status.INACTIVE.getCode()).build();

        // Encode password safely
        user.setPasswordAndDigest(request.password(), (org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder) encoder);

        try {
            User savedUser = userRepository.save(user);

            // Generate verification code
            UserCode code = userCodeService.createCode(savedUser, "EMAIL_VERIFY", 10);

            // Email model variables
            Map<String, Object> model = Map.of("CODE", code.getCode(), "NAME", savedUser.getFirstName());

            // Send verification email
            emailService.sendHtml(savedUser.getEmail(), "Ktab — Verify Your Account", "verify-email", model);

            log.info("User registered successfully email={} (verification code sent)", savedUser.getEmail());
            return savedUser;

        } catch (Exception ex) {
            log.error("Failed to register user email={}, error={}", email, ex.getMessage(), ex);
            throw ex;
        }
    }

    // ============================
    // LOGIN / GENERATE TOKEN
    // ============================
    public String verify(UserLoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return jwtService.generateToken(userPrincipal);
    }

    // ============================
    // GET USER BY ID
    // ============================
    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    // ============================
    // VERIFY EMAIL CODE
    // ============================
    public boolean verifyEmail(VerifyCodeRequest req) {

        User user = userRepository.findByEmail(req.email()).orElseThrow(() -> new BadRequestException("User not found"));

        boolean ok = userCodeService.verify(user, req.code(), "EMAIL_VERIFY");

        if (ok) {
            user.setActive(Status.ACTIVE.getCode());
            userRepository.save(user);
            log.info("Email verified for user email={}", user.getEmail());
        } else {
            log.warn("Email verification failed for user email={}", user.getEmail());
        }

        return ok;
    }

    // ============================
    // SEND RESET PASSWORD CODE
    // ============================
    public void sendResetCode(SendResetPasswordRequest req) {

        User user = userRepository.findByEmail(req.email()).orElseThrow(() -> new BadRequestException("User not found"));

        UserCode code = userCodeService.createCode(user, "RESET_PASSWORD", 10);

        Map<String, Object> model = Map.of("RESET_CODE", code.getCode(), "NAME", user.getFirstName());

        emailService.sendHtml(user.getEmail(), "Reset Your Ktab Password", "reset-password", model);

        log.info("Password reset code sent to email={}", user.getEmail());
    }

    // ============================
    // SEND RESET PASSWORD CODE
    // ============================
    public void sendReVerifyAccountCode(ResendVerificationCodeRequest req) {

        User user = userRepository.findByEmail(req.email()).orElseThrow(() -> new BadRequestException("User not found"));

        if (user.getActive().equals(Status.ACTIVE.getCode())) {
            throw new RuntimeException("Account already active");
        }

        UserCode code = userCodeService.createCode(user, "EMAIL_VERIFY", 10);

        Map<String, Object> model = Map.of("CODE", code.getCode(), "NAME", user.getFirstName());

        // Send verification email
        emailService.sendHtml(user.getEmail(), "Ktab — Verify Your Account", "verify-email", model);

        log.info("Password reverify code sent to email={}", user.getEmail());
    }

    // ============================
    // RESET PASSWORD
    // ============================
    public boolean resetPassword(ResetPasswordRequest req) {

        User user = userRepository.findByEmail(req.email()).orElseThrow(() -> new BadRequestException("User not found"));

        boolean ok = userCodeService.verify(user, req.code(), "RESET_PASSWORD");

        if (!ok) {
            log.warn("Invalid reset code for email={}", user.getEmail());
            return false;
        }

        user.setPasswordAndDigest(req.newPassword(), (org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder) encoder);
        userRepository.save(user);

        log.info("Password reset successful for email={}", user.getEmail());
        return true;
    }

    public String refreshToken(String email) {
        // Load user
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BadRequestException("User not found."));

        UserPrincipal principal = new UserPrincipal(user);

        // Generate new token
        return jwtService.generateToken(principal);
    }

}
