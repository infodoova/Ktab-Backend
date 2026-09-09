package com.doova.ktab.security.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class SecurityConfig {

    @PostConstruct
    public void init() {
        // Use standard ThreadLocal strategy to prevent security context leakage across pooled threads
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL);
    }
}
