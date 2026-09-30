package com.doova.ktab.features.trailer.repository;

import com.doova.ktab.features.trailer.model.TrailerOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrailerOAuthStateRepository extends JpaRepository<TrailerOAuthState, Long> {

    Optional<TrailerOAuthState> findByStateAndUsedFalse(String state);
}
