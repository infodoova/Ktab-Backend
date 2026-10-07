package com.doova.ktab.features.earlyaccess.repository;

import com.doova.ktab.features.earlyaccess.model.EarlyAccessSignup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EarlyAccessSignupRepository extends JpaRepository<EarlyAccessSignup, Long> {

    Optional<EarlyAccessSignup> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
