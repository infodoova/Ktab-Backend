package com.doova.ktab.service.user;

import com.doova.ktab.model.user.User;
import com.doova.ktab.model.user.UserCode;
import com.doova.ktab.repository.user.UserCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserCodeService {

    private final UserCodeRepository codeRepo;
    private final SecureRandom random = new SecureRandom();

    private String generateCode() {
        return String.valueOf(100000 + random.nextInt(900000)); // 6 digits
    }

    public UserCode createCode(User user, String type, int validMinutes) {

        // Invalidate older active code
        codeRepo.findTopByUserAndCodeTypeAndUsedIsFalseOrderByExpiresAtDesc(user, type).ifPresent(old -> {
            old.setUsed(true);
            codeRepo.saveAndFlush(old);
        });

        UserCode c = UserCode.builder().user(user).code(generateCode()).codeType(type).expiresAt(Instant.now().plusSeconds(validMinutes * 60L)).used(false).build();

        return codeRepo.save(c);
    }

    public boolean verify(User user, String code, String type) {
        return codeRepo.findByUserAndCodeAndCodeTypeAndUsedIsFalse(user, code, type).filter(c -> !c.isExpired()).map(c -> {
            c.setUsed(true);
            codeRepo.save(c);
            return true;
        }).orElse(false);
    }
}

