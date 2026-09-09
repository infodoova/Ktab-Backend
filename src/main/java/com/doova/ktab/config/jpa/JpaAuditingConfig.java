package com.doova.ktab.config.jpa;

import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.security.Utils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> Utils.getCurrentLoggedInUser().map(User::getId);
    }
}
