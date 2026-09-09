package com.doova.ktab.repository.user;

import com.doova.ktab.model.user.User;
import com.doova.ktab.model.user.UserCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserCodeRepository extends JpaRepository<UserCode, Long> {

    Optional<UserCode> findTopByUserAndCodeTypeAndUsedIsFalseOrderByExpiresAtDesc(User user, String codeType);

    Optional<UserCode> findByUserAndCodeAndCodeTypeAndUsedIsFalse(User user, String code, String codeType);
}
