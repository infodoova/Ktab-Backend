package com.doova.ktab.service.user;

import com.doova.ktab.dto.user.*;
import com.doova.ktab.model.user.User;

public interface UserService {

    User register(UserRegisterRequest request);

    String verify(UserLoginRequest request);

    void verifyEmail(VerifyCodeRequest req);

    void sendResetCode(SendResetPasswordRequest req);

    void sendReVerifyAccountCode(ResendVerificationCodeRequest req);

    void resetPassword(ResetPasswordRequest req);

    String refreshToken(String email);
}
