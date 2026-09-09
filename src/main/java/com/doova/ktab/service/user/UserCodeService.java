package com.doova.ktab.service.user;

import com.doova.ktab.model.user.User;
import com.doova.ktab.model.user.UserCode;

public interface UserCodeService {

    UserCode createCode(User user, String type, int validMinutes);

    boolean verify(User user, String code, String type);
}
