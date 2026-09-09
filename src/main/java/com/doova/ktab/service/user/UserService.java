package com.doova.ktab.service.user;

import com.doova.ktab.dto.user.*;
import com.doova.ktab.model.user.User;
import com.doova.ktab.security.model.UserPrincipal;

public interface UserService {

    User register(UserRegisterRequest request);

    UserPrincipal authenticate(UserLoginRequest request);

    String authenticateAndGenerateToken(UserLoginRequest request);

    void verifyEmail(VerifyCodeRequest req);

    void sendResetCode(SendResetPasswordRequest req);

    void sendReVerifyAccountCode(ResendVerificationCodeRequest req);

    void resetPassword(ResetPasswordRequest req);
}
