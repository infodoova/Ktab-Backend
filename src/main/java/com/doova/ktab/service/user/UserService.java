package com.doova.ktab.service.user;

import com.doova.ktab.dto.request.*;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.enums.status.Status;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.model.user.User;
import com.doova.ktab.model.user.UserCode;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.security.model.UserPrincipal;
import com.doova.ktab.service.auth.JWTService;
import com.doova.ktab.service.interfaces.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
    private final PasswordEncoder encoder;
    private final EmailService emailService;

    // ============================
    // REGISTER
    // ============================

    @Transactional
    public User register(UserRegisterRequest request) {

        String email = request.email();

        if (email == null || email.isBlank()) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException(ApiMessageKey.AUTH_EMAIL_ALREADY_USED);
        }

        User user = User.builder().email(email).firstName(request.firstName()).middleName(request.middleName()).lastName(request.lastName()).role(request.role()).active(Status.INACTIVE.getCode()).build();

        user.setPasswordAndDigest(request.password(), (org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder) encoder);

        User savedUser = userRepository.save(user);

        UserCode code = userCodeService.createCode(savedUser, "EMAIL_VERIFY", 10);

        Map<String, Object> model = Map.of("CODE", code.getCode(), "NAME", savedUser.getFirstName());

        emailService.sendHtml(savedUser.getEmail(), "Ktab — Verify Your Account", "verify-email", model);

        log.info("User registered: {}", email);
        return savedUser;
    }

    // ============================
    // LOGIN
    // ============================

    public String verify(UserLoginRequest request) {

        var authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return jwtService.generateToken(principal);
    }

    // ============================
    // VERIFY EMAIL
    // ============================

    public void verifyEmail(VerifyCodeRequest req) {

        User user = userRepository.findByEmail(req.email()).orElseThrow(() -> new BadRequestException(ApiMessageKey.RESOURCE_NOT_FOUND));

        boolean ok = userCodeService.verify(user, req.code(), "EMAIL_VERIFY");

        if (!ok) {
            throw new BadRequestException(ApiMessageKey.AUTH_INVALID_OR_EXPIRED_CODE);
        }

        user.setActive(Status.ACTIVE.getCode());
        userRepository.save(user);
    }

    // ============================
    // SEND RESET PASSWORD CODE
    // ============================

    public void sendResetCode(SendResetPasswordRequest req) {

        User user = userRepository.findByEmail(req.email()).orElseThrow(() -> new BadRequestException(ApiMessageKey.RESOURCE_NOT_FOUND));

        UserCode code = userCodeService.createCode(user, "RESET_PASSWORD", 10);

        Map<String, Object> model = Map.of("RESET_CODE", code.getCode(), "NAME", user.getFirstName());

        emailService.sendHtml(user.getEmail(), "Reset Your Ktab Password", "reset-password", model);
    }

    // ============================
    // RESEND VERIFY CODE
    // ============================

    public void sendReVerifyAccountCode(ResendVerificationCodeRequest req) {

        User user = userRepository.findByEmail(req.email()).orElseThrow(() -> new BadRequestException(ApiMessageKey.RESOURCE_NOT_FOUND));

        if (Status.ACTIVE.getCode().equals(user.getActive())) {
            throw new BadRequestException(ApiMessageKey.AUTH_ACCOUNT_ALREADY_ACTIVE);
        }

        UserCode code = userCodeService.createCode(user, "EMAIL_VERIFY", 10);

        Map<String, Object> model = Map.of("CODE", code.getCode(), "NAME", user.getFirstName());

        emailService.sendHtml(user.getEmail(), "Ktab — Verify Your Account", "verify-email", model);
    }

    // ============================
    // RESET PASSWORD
    // ============================

    public void resetPassword(ResetPasswordRequest req) {

        User user = userRepository.findByEmail(req.email()).orElseThrow(() -> new BadRequestException(ApiMessageKey.RESOURCE_NOT_FOUND));

        boolean ok = userCodeService.verify(user, req.code(), "RESET_PASSWORD");

        if (!ok) {
            throw new BadRequestException(ApiMessageKey.AUTH_INVALID_OR_EXPIRED_CODE);
        }

        user.setPasswordAndDigest(req.newPassword(), (org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder) encoder);

        userRepository.save(user);
    }

    // ============================
    // REFRESH TOKEN
    // ============================

    public String refreshToken(String email) {

        User user = userRepository.findByEmail(email).orElseThrow(() -> new BadRequestException(ApiMessageKey.RESOURCE_NOT_FOUND));

        return jwtService.generateToken(new UserPrincipal(user));
    }
}
