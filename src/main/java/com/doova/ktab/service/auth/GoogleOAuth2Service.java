package com.doova.ktab.service.auth;

import com.doova.ktab.security.model.UserPrincipal;

public interface GoogleOAuth2Service {

    UserPrincipal verifyAndAuthenticate(String idTokenString);
}
